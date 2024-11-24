package com.lbry.globe.thread;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import com.lbry.globe.api.API;
import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;
import com.lbry.globe.util.GeoIP;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONObject;

public class DHTNodeFinderThread implements Runnable{

    private static final Bencode BENCODE = new Bencode();

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

    private static final DatagramSocket SOCKET;

    static{
        try{
            SOCKET = new DatagramSocket();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    private final Map<InetSocketAddress,Boolean> pingableDHTs = new ConcurrentHashMap<>();

    private final Queue<DatagramPacket> incoming = new ConcurrentLinkedQueue<>();

    @Override
    public void run(){
        for(String bootstrap : DHTNodeFinderThread.BOOTSTRAP){
            URI uri = URI.create("udp://"+bootstrap);
            this.pingableDHTs.put(new InetSocketAddress(uri.getHost(),uri.getPort()),true);
        }

        // Ping Sender
        new Thread(() -> {
            while(true){
                for(InetSocketAddress socketAddress : DHTNodeFinderThread.this.pingableDHTs.keySet()){
                    String hostname = socketAddress.getHostName();
                    int port = socketAddress.getPort();
                    try{
                        for(InetAddress ip : InetAddress.getAllByName(hostname)){
                            DHTNodeFinderThread.ping(ip,port);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        // Receiver
        new Thread(() -> {
            while(true) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
                    DHTNodeFinderThread.SOCKET.receive(receiverPacket);
                    DHTNodeFinderThread.this.incoming.add(receiverPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        while(true){

            //TODO: MARKS AS DELETED

            while(this.incoming.peek()!=null){
                DatagramPacket receiverPacket = this.incoming.poll();
                byte[] receivingBytes = receiverPacket.getData();
                Map<String, Object> receivingDictionary = DHTNodeFinderThread.decodePacket(receivingBytes);
                if(receivingDictionary.get("0").equals(1L)){
                    if(receivingDictionary.get("3").equals("pong")){
                        try{
                            DHTNodeFinderThread.findNode(receiverPacket.getAddress(),receiverPacket.getPort());
                        }catch(Exception e){
                            e.printStackTrace();
                        }

                        //TODO Improve updating pinged nodes.
                        System.out.println("PONG: "+receiverPacket.getSocketAddress());

                        Node existingNode = API.NODES.get(receiverPacket.getAddress());
                        if(existingNode==null){
                            JSONObject geoData = GeoIP.getCachedGeoIPInformation(receiverPacket.getAddress());
                            Double[] coords = GeoIP.getCoordinateFromLocation(geoData.has("loc")?geoData.getString("loc"):null);
                            existingNode = new Node(receiverPacket.getAddress(),coords[0],coords[1]);
                            API.NODES.put(receiverPacket.getAddress(),existingNode);
                        }
                        Service dhtService = null;
                        for(Service s : existingNode.getServices()){
                            if(s.getPort()==receiverPacket.getPort() && "dht".equals(s.getType())){
                                dhtService = s;
                                break;
                            }
                        }

                        if(dhtService==null){
                            existingNode.getServices().add(new Service(UUID.randomUUID(),receiverPacket.getPort(),"dht"));
                        }else{
                            dhtService.updateLastSeen();
                        }
                    }else{
                        //TODO Save connections too
                        List<List<Object>> nodes = (List<List<Object>>) receivingDictionary.get("3");
                        for(List<Object> n : nodes){
                            String hostname = (String) n.get(1);
                            int port = (int) ((long) n.get(2));
                            InetSocketAddress existingSocketAddr = null;
                            for(InetSocketAddress addr : this.pingableDHTs.keySet()){
                                if(addr.getHostName().equals(hostname) && addr.getPort()==port){
                                    existingSocketAddr = addr;
                                }
                            }
                            if(existingSocketAddr==null){
                                this.pingableDHTs.put(new InetSocketAddress(hostname,port),false);
                            }
                        }
                    }
                }
            }

            //TODO: REMOVE MARKED AS DELETED

            System.out.println("----");
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void ping(InetAddress ip,int port) throws IOException{
        byte[] rpcID = new byte[20];
        new Random().nextBytes(rpcID);

        Map<String,Object> ping = new HashMap<>();
        ping.put("0",0);
        ping.put("1",rpcID);
        ping.put("2",new byte[48]);
        ping.put("3","ping");
        ping.put("4",Collections.singletonList(Collections.singletonMap("protocolVersion",1)));
        byte[] pingBytes = DHTNodeFinderThread.encodePacket(ping);

        DatagramPacket sendingDiagram = new DatagramPacket(pingBytes,pingBytes.length,ip,port);
        DHTNodeFinderThread.SOCKET.send(sendingDiagram);
    }

    private static void findNode(InetAddress ip,int port) throws IOException{
        byte[] rpcID = new byte[20];
        new Random().nextBytes(rpcID);

        Map<String,Object> findNode = new HashMap<>();
        findNode.put("0",0);
        findNode.put("1",rpcID);
        findNode.put("2",new byte[48]);
        findNode.put("3","findNode");
        findNode.put("4",Arrays.asList(new byte[48],Collections.singletonMap("protocolVersion",1)));
        byte[] findNodeBytes = DHTNodeFinderThread.encodePacket(findNode);

        DatagramPacket sendingDiagram = new DatagramPacket(findNodeBytes,findNodeBytes.length,ip,port);
        DHTNodeFinderThread.SOCKET.send(sendingDiagram);
    }

    private static byte[] encodePacket(Map<String,Object> map){
        return DHTNodeFinderThread.BENCODE.encode(map);
    }

    private static Map<String,Object> decodePacket(byte[] bytes){
        // Fix invalid B-encoding
        if(bytes[0]=='d'){
            bytes[0] = 'l';
        }
        List<Object> list = DHTNodeFinderThread.BENCODE.decode(bytes,Type.LIST);
        for(int i=0;i<list.size();i++){
            if(i%2==0){
                list.set(i,String.valueOf(list.get(i)));
            }
        }
        bytes = DHTNodeFinderThread.BENCODE.encode(list);
        if(bytes[0]=='l'){
            bytes[0] = 'd';
        }
        // Normal B-decoding
        return DHTNodeFinderThread.BENCODE.decode(bytes,Type.DICTIONARY);
    }

}
