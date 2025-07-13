package com.lbry.globe.thread;

import com.lbry.globe.api.API;
import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;
import com.lbry.globe.util.DHT;
import com.lbry.globe.util.GeoIP;
import com.lbry.globe.util.UDP;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.json.JSONObject;

public class DHTNodeFinderThread implements Runnable{

    public static final String[] BOOTSTRAP = {
            "dht.lbry.grin.io:4444",		// Grin
            "dht.lbry.madiator.com:4444",	// Madiator
            "dht.lbry.pigg.es:4444",		// Pigges
            "lbrynet1.lbry.com:4444",		// US EAST
            "lbrynet2.lbry.com:4444",		// US WEST
            "lbrynet3.lbry.com:4444",		// EU
            "lbrynet4.lbry.com:4444",		// ASIA
            "dht.lizard.technology:4444",	// Jack
            "s2.lbry.network:4444",
    };

    private final Queue<UDP.Packet> incoming = new ConcurrentLinkedQueue<>();

    @Override
    public void run(){
        for(String bootstrap : DHTNodeFinderThread.BOOTSTRAP){
            URI uri = URI.create("udp://"+bootstrap);
            DHT.getPeers().put(new InetSocketAddress(uri.getHost(),uri.getPort()),true);
        }

        this.startSender();
        this.startReceiver();
        this.handleIncomingMessages();
    }

    private void startSender(){
        Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("DHT Sender")).scheduleWithFixedDelay(() -> {
            System.out.println("[DHT] BULK PING");
            API.saveNodes();
            for(InetSocketAddress socketAddress : DHT.getPeers().keySet()){
                String hostname = socketAddress.getHostName();
                int port = socketAddress.getPort();
                try{
                    for(InetAddress ip : InetAddress.getAllByName(hostname)){
                        InetSocketAddress destination = new InetSocketAddress(ip,port);
                        this.doPing(destination);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        },0,15,TimeUnit.SECONDS);
    }

    private void doPing(InetSocketAddress destination) throws IOException{
        DHT.ping(DHT.getSocket(),destination).thenAccept((UDP.Packet packet) -> {
            byte[] receivingBytes = packet.getData();
            DHT.Message<?> message = DHT.Message.fromBencode(receivingBytes);
            System.out.println(" - [Ping Response] "+message);

            try{
                this.doFindNode(packet.getAddress());
            }catch(Exception e){
                e.printStackTrace();
            }

            //TODO Improve updating pinged nodes.

            Node existingNode = API.NODES.get(packet.getAddress().getAddress());
            if(existingNode==null){
                JSONObject geoData = GeoIP.getCachedGeoIPInformation(packet.getDatagramPacket().getAddress());
                Double[] coords = GeoIP.getCoordinateFromLocation((geoData!=null && geoData.has("loc"))?geoData.getString("loc"):null);
                existingNode = new Node(packet.getDatagramPacket().getAddress(),coords[0],coords[1]);
                API.NODES.put(packet.getDatagramPacket().getAddress(),existingNode);
            }
            Service dhtService = null;
            for(Service s : existingNode.getServices()){
                if(s.getPort()==packet.getDatagramPacket().getPort() && "dht".equals(s.getType())){
                    dhtService = s;
                    break;
                }
            }

            if(dhtService==null){
                existingNode.getServices().add(new Service(UUID.randomUUID(),packet.getDatagramPacket().getPort(),"dht"));
            }else{
                dhtService.updateLastSeen();
            }
        }).exceptionally((Throwable e) -> null);
    }

    private void doFindNode(InetSocketAddress destination) throws IOException{
        DHT.findNode(DHT.getSocket(),destination,new byte[48]).thenAccept((UDP.Packet packet) -> {
            byte[] receivingBytes = packet.getData();
            DHT.Message<?> message = DHT.Message.fromBencode(receivingBytes);
            System.out.println(" - [FindNode Response] "+message);

            List<List<Object>> nodes = (List<List<Object>>) message.getPayload();
            for(List<Object> n : nodes){
                String hostname = (String) n.get(1);
                int port = (int) ((long) n.get(2));
                InetSocketAddress existingSocketAddr = null;
                for(InetSocketAddress addr : DHT.getPeers().keySet()){
                    if(addr.getHostName().equals(hostname) && addr.getPort()==port){
                        existingSocketAddr = addr;
                    }
                }
                if(existingSocketAddr==null){
                    DHT.getPeers().put(new InetSocketAddress(hostname,port),false);
                }
            }
        }).exceptionally((Throwable e) -> null);
    }

    private void startReceiver(){
        new Thread(() -> {
            while(true) {
                try {
                    UDP.Packet receiverPacket = UDP.receive(DHT.getSocket());
                    DHTNodeFinderThread.this.incoming.add(receiverPacket);

                    byte[] receivingBytes = receiverPacket.getData();

                    DHT.Message<?> message = DHT.Message.fromBencode(receivingBytes);
                    DHT.RPCID rpcid = new DHT.RPCID(message);
                    DHT.getFutureManager().finishFuture(rpcid,receiverPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        },"DHT Receiver").start();
    }

    private void handleIncomingMessages(){
        new Thread(() -> {
            while(DHT.getSocket().isBound()){
                while(this.incoming.peek()!=null){
                    UDP.Packet receiverPacket = this.incoming.poll();
                    byte[] receivingBytes = receiverPacket.getData();

                    DHT.Message<?> message = DHT.Message.fromBencode(receivingBytes);
                    if(message.getType()==DHT.Message.TYPE_REQUEST){
                        System.out.println("Incoming request");
                    }
                }
            }
        },"DHT Incoming").start();
    }

}
