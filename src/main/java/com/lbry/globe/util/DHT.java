package com.lbry.globe.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class DHT{

    public static byte[] NODE_ID = new byte[48];

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Timeout Future"));
    private static final TimeoutFutureManager<RPCID,UDP.Packet> futureManager = new TimeoutFutureManager<>(executor);
    private static final Map<InetSocketAddress,Boolean> peers = new ConcurrentHashMap<>();
    private static final DatagramSocket socket;

    static{
        try{
            socket = new DatagramSocket();
        }catch(SocketException e){
            throw new RuntimeException(e);
        }
    }

    public static TimeoutFutureManager<RPCID,UDP.Packet> getFutureManager(){
        return DHT.futureManager;
    }

    public static DatagramSocket getSocket(){
        return DHT.socket;
    }

    public static Map<InetSocketAddress,Boolean> getPeers(){
        return DHT.peers;
    }

    public static CompletableFuture<UDP.Packet> ping(DatagramSocket socket,InetSocketAddress destination) throws IOException {
        byte[] rpcID = new byte[20];
        new Random().nextBytes(rpcID);

        DHT.Message<String> pingMessage = new DHT.Message<>(DHT.Message.TYPE_REQUEST,rpcID,DHT.NODE_ID,"ping",Collections.singletonList(Collections.singletonMap("protocolVersion",1)));

        return DHT.sendWithFuture(socket,destination,pingMessage);
    }

    public static CompletableFuture<UDP.Packet> findNode(DatagramSocket socket,InetSocketAddress destination,byte[] key) throws IOException{
        byte[] rpcID = new byte[20];
        new Random().nextBytes(rpcID);

        DHT.Message<String> findNodeMessage = new DHT.Message<>(DHT.Message.TYPE_REQUEST,rpcID,DHT.NODE_ID,"findNode",Arrays.asList(key,Collections.singletonMap("protocolVersion",1)));

        return DHT.sendWithFuture(socket,destination,findNodeMessage);
    }

    public static CompletableFuture<UDP.Packet> findValue(DatagramSocket socket,InetSocketAddress destination,byte[] key) throws IOException{
        byte[] rpcID = new byte[20];
        new Random().nextBytes(rpcID);

        DHT.Message<String> findNodeMessage = new DHT.Message<>(DHT.Message.TYPE_REQUEST,rpcID,DHT.NODE_ID,"findValue",Arrays.asList(key,Collections.singletonMap("protocolVersion",1)));

        return DHT.sendWithFuture(socket,destination,findNodeMessage);
    }

    protected static CompletableFuture<UDP.Packet> sendWithFuture(DatagramSocket socket,InetSocketAddress destination, DHT.Message<?> message) throws IOException{
        UDP.send(socket,new UDP.Packet(destination,message.toBencode()));
        RPCID key = new RPCID(message);
        return DHT.futureManager.createFuture(key,1,TimeUnit.SECONDS);
    }

    public static class Message<P>{

        public static final int TYPE_REQUEST = 0;
        public static final int TYPE_RESPONSE = 1;

        private int type;

        private byte[] rpcID;

        private byte[] nodeID;

        private P payload;

        private List<?> arguments;

        private Message(){}

        public Message(int type,byte[] rpcID,byte[] nodeID){
            this(type,rpcID,nodeID,null);
        }

        public Message(int type,byte[] rpcID,byte[] nodeID,P payload){
            this(type,rpcID,nodeID,payload,null);
        }

        public Message(int type,byte[] rpcID,byte[] nodeID,P payload,List<?> arguments){
            this.type = type;
            this.rpcID = rpcID;
            this.nodeID = nodeID;
            this.payload = payload;
            this.arguments = arguments;
        }

        public int getType(){
            return this.type;
        }

        public byte[] getRPCID(){
            return this.rpcID;
        }

        public byte[] getNodeID(){
            return this.nodeID;
        }

        public P getPayload(){
            return this.payload;
        }

        public List<?> getArguments(){
            return this.arguments;
        }

        public Map<String,Object> toMap(){
            Map<String,Object> dictionary = new HashMap<>();
            dictionary.put("0",this.type);
            dictionary.put("1",this.rpcID);
            dictionary.put("2",this.nodeID);
            if(this.payload!=null){
                dictionary.put("3",this.payload);
            }
            if(this.arguments!=null){
                dictionary.put("4",this.arguments);
            }
            return dictionary;
        }

        public byte[] toBencode(){
            return BencodeConverter.encode(this.toMap());
        }

        private DHT.Message<P> setFromBencode(byte[] data){
            Map<String,?> dictionary = BencodeConverter.decode(data);
            this.type = ((Long) dictionary.get("0")).intValue();
            this.rpcID = ((ByteBuffer) dictionary.get("1")).array();
            this.nodeID = ((ByteBuffer) dictionary.get("2")).array();
            this.payload = null;
            if(dictionary.containsKey("3")){
                Object payload = dictionary.get("3");
                this.payload = BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(payload);
            }
            this.arguments = null;
            if(dictionary.containsKey("4")){
                this.arguments = (List<?>) dictionary.get("4");
            }
            return this;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "type=" + type +
                    ", rpcID=" + Hex.encode(rpcID) +
                    ", nodeID=" + Hex.encode(nodeID) +
                    ", payload=" + payload +
                    ", arguments=" + arguments +
                    '}';
        }

        public static DHT.Message<?> fromBencode(byte[] data){
            return new Message<>().setFromBencode(data);
        }

    }

    public static class RPCID{

        private final byte[] id;

        public RPCID(byte[] id){
            this.id = id;
        }

        public RPCID(Message<?> message){
            this(message.rpcID);
        }

        @Override
        public boolean equals(Object obj){
            if(obj instanceof RPCID){
                RPCID other = (RPCID) obj;
                return Arrays.equals(this.id,other.id);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode(){
            return -1;
        }

        @Override
        public String toString() {
            return "RPCID{" +
                    "id=" + Hex.encode(id) +
                    '}';
        }

    }

}
