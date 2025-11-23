package com.lbry.globe.thread;

import com.lbry.globe.api.API;
import com.lbry.globe.kademlia.KademliaBucket;
import com.lbry.globe.kademlia.KademliaTriple;
import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;
import com.lbry.globe.util.DHT;
import com.lbry.globe.util.GeoIP;
import com.lbry.globe.util.NamedThreadFactory;
import com.lbry.globe.util.UDP;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public class DHTNodeFinderThread implements Runnable{

    // Using just a single bucket is wrong, but easy for now.
    private static final KademliaBucket SINGLE_BUCKET = DHT.KADEMLIA.getBucket(0);

    public static final String[] BOOTSTRAP = {
            "dht.lbry.grin.io:4444",		// Grin
            "dht.lbry.madiator.com:4444",	// Madiator
            "dht.lbry.pigg.es:4444",		// Pigges
            "lbrynet1.lbry.com:4444",		// US EAST
            "lbrynet2.lbry.com:4444",		// US WEST
            "lbrynet3.lbry.com:4444",		// EU
            "lbrynet4.lbry.com:4444",		// ASIA
            "dht.lizard.technology:4444",	// Jack
            "s2.lbry.network:4444",         // LBRY Foundation
    };

    @Override
    public void run(){
        for(String bootstrap : DHTNodeFinderThread.BOOTSTRAP){
            URI uri = URI.create("udp://"+bootstrap);
            try{
                DHT.ping(DHT.getSocket(),new InetSocketAddress(uri.getHost(),uri.getPort())).thenAccept((UDP.Packet packet) -> {
                    byte[] receivingBytes = packet.getData();
                    DHT.Message<?> message = DHT.Message.fromBencode(receivingBytes);
                    SINGLE_BUCKET.insertAtTail(new KademliaTriple(packet.getAddress().getAddress(),packet.getAddress().getPort(),message.getNodeID()));
                }).exceptionally((Throwable e) -> null);
            }catch(Exception e){
                System.out.println("Failed bootstrap ping");
            }
        }

        this.startSender();
        DHT.startReceiver();
    }

    private void startSender(){
        Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DHT Sender")).scheduleWithFixedDelay(() -> {
            System.out.println("[DHT] BULK PING");
            API.saveNodes();
            for(KademliaTriple triple : SINGLE_BUCKET.getList()){
                try{
                    for(InetAddress ip : InetAddress.getAllByName(triple.getIPAddress().getHostName())){
                        InetSocketAddress destination = new InetSocketAddress(ip,triple.getUDPPort());
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
                KademliaTriple existingTriple = null;
                for(KademliaTriple triple : SINGLE_BUCKET.getList()){
                    if(triple.getIPAddress().getHostName().equals(hostname) && triple.getUDPPort()==port){
                        existingTriple = triple;
                    }
                }
                if(existingTriple==null){
                    try{
                        SINGLE_BUCKET.insertAtTail(new KademliaTriple(InetAddress.getByName(hostname),port,message.getNodeID()));
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }).exceptionally((Throwable e) -> null);
    }

}
