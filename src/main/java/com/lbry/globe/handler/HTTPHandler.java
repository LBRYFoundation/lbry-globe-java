package com.lbry.globe.handler;

import com.lbry.globe.Main;
import com.lbry.globe.api.API;

import com.lbry.globe.kademlia.KademliaBucket;
import com.lbry.globe.kademlia.KademliaTriple;
import com.lbry.globe.util.DHT;
import com.lbry.globe.util.Hex;
import com.lbry.globe.util.TimeoutFutureManager;
import com.lbry.globe.util.UDP;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

public class HTTPHandler extends ChannelInboundHandlerAdapter{

    public static final AttributeKey<HttpRequest> ATTR_REQUEST = AttributeKey.newInstance("request");
    public static final AttributeKey<List<HttpContent>> ATTR_CONTENT = AttributeKey.newInstance("content");
    private static final Logger LOGGER = Logger.getLogger("Handler");

    private static byte[] jsHash;

    @Override
    public void channelRead(ChannelHandlerContext ctx,Object msg){
        if(msg instanceof HttpRequest){
            ctx.channel().attr(HTTPHandler.ATTR_REQUEST).set((HttpRequest) msg);
        }
        if(msg instanceof HttpContent){
            ctx.channel().attr(HTTPHandler.ATTR_CONTENT).setIfAbsent(new ArrayList<>());
            ctx.channel().attr(HTTPHandler.ATTR_CONTENT).get().add((HttpContent) msg);

            if(msg instanceof LastHttpContent){
                this.handleResponse(ctx);
            }
        }
    }

    private void handleResponse(ChannelHandlerContext ctx){
        HttpRequest request = ctx.channel().attr(HTTPHandler.ATTR_REQUEST).get();
        List<HttpContent> content = ctx.channel().attr(HTTPHandler.ATTR_CONTENT).get();
        ctx.channel().attr(HTTPHandler.ATTR_REQUEST).set(null);
        ctx.channel().attr(HTTPHandler.ATTR_CONTENT).set(null);

        assert content!=null;

        if(request.method().equals(HttpMethod.GET)){
            URI uri = URI.create(request.uri());

            if("/".equals(uri.getPath())){
                int status = 200;
                byte[] indexData;
                try{
                    indexData = HTTPHandler.readResource(HTTPHandler.getResource("index.html"));
                }catch(Exception ignored){
                    status = 500;
                    indexData = "Some error occured.".getBytes();
                }
                indexData = new String(indexData).replace("${GLOBE_JS_VERSION}",Base64.getEncoder().encodeToString(HTTPHandler.getJSHash()).replaceAll("=","")).replace("<div class=\"version\"></div>","<div class=\"version\">"+Main.class.getPackage().getImplementationVersion()+"</div>").getBytes();
                ByteBuf responseContent = Unpooled.copiedBuffer(indexData);
                FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),HttpResponseStatus.valueOf(status),responseContent);
                response.headers().add("Content-Length",responseContent.capacity());
                response.headers().add("Content-Type","text/html");
                ctx.write(response);
                return;
            }
            if("/api".equals(uri.getPath())){
                JSONArray points = new JSONArray();
                API.fillPoints(points);

                JSONObject json = new JSONObject().put("points",points);//new JSONObject(new String(jsonData));
                ByteBuf responseContent = Unpooled.copiedBuffer(json.toString().getBytes());
                FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),HttpResponseStatus.OK,responseContent);
                response.headers().add("Content-Length",responseContent.capacity());
                response.headers().add("Content-Type","application/json");
                ctx.write(response);
                return;
            }
            if("/api/command".equals(uri.getPath())){
                JSONObject json = new JSONObject();

                String[] queryParts = uri.getQuery()!=null?uri.getQuery().split(";"):new String[]{""};
                if("ping".equals(queryParts[0]) || "findNode".equals(queryParts[0]) || "findValue".equals(queryParts[0])){
                    //STORE IS NOT SUPPORTED
                    json.put("query",queryParts);

                    // Using just a single bucket is wrong, but easy for now.
                    KademliaBucket SINGLE_BUCKET = DHT.KADEMLIA.getBucket(0);

                    CompletableFuture<UDP.Packet>[] futures = new CompletableFuture[SINGLE_BUCKET.size()];
                    int i=0;
                    for(KademliaTriple triple : SINGLE_BUCKET.getList()){
                        try{
                            if("ping".equals(queryParts[0])){
                                futures[i] = DHT.ping(DHT.getSocket(),triple.getInetSocketAddress());
                            }
                            if("findNode".equals(queryParts[0])){
                                futures[i] = DHT.findNode(DHT.getSocket(),triple.getInetSocketAddress(),queryParts.length>=2?Hex.decode(queryParts[1]):new byte[48]);
                            }
                            if("findValue".equals(queryParts[0])){
                                futures[i] = DHT.findValue(DHT.getSocket(),triple.getInetSocketAddress(),queryParts.length>=2?Hex.decode(queryParts[1]):new byte[48]);
                            }
                        }catch(IOException ignored){}
                        i++;
                    }

                    CompletableFuture<List<UDP.Packet>> total = TimeoutFutureManager.getBulk(futures);

                    JSONObject jsonData = new JSONObject();
                    json.put("data",jsonData);
                    try{
                        List<UDP.Packet> responses = total.get();
                        for(UDP.Packet resp : responses){
                            if(resp!=null){
                                DHT.Message<?> message = DHT.Message.fromBencode(resp.getData());
                                if("ping".equals(queryParts[0])){
                                    String pong = (String) message.getPayload();
                                    jsonData.put(resp.getAddress().getAddress().getHostAddress()+":"+resp.getAddress().getPort(),pong);
                                }
                                if("findNode".equals(queryParts[0])){
                                    JSONArray payload = new JSONArray();
                                    List<List<Object>> nodes = (List<List<Object>>) message.getPayload();
                                    for(List<Object> node : nodes){
                                        JSONObject p = new JSONObject();
                                        p.put("nodeID", Hex.encode((byte[]) node.get(0)));
                                        p.put("hostname",node.get(1));
                                        p.put("port",node.get(2));
                                        payload.put(p);
                                    }
                                    jsonData.put(resp.getAddress().getAddress().getHostAddress()+":"+resp.getAddress().getPort(),payload);
                                }
                                if("findValue".equals(queryParts[0])){
                                    Map<String,Object> map = (Map<String, Object>) message.getPayload();
                                    JSONObject payload = new JSONObject();
                                    payload.put("p",map.get("p"));
                                    payload.put("protocolVersion",map.get("protocolVersion"));
                                    JSONArray contacts = new JSONArray();
                                    List<List<Object>> nodes = (List<List<Object>>) map.get("contacts");
                                    for(List<Object> node : nodes){
                                        JSONObject p = new JSONObject();
                                        p.put("nodeID", Hex.encode((byte[]) node.get(0)));
                                        p.put("hostname",node.get(1));
                                        p.put("port",node.get(2));
                                        contacts.put(p);
                                    }
                                    payload.put("contacts",contacts);
                                    //payload.put("token",Hex.encode((byte[]) map.get("token")));
                                    jsonData.put(resp.getAddress().getAddress().getHostAddress()+":"+resp.getAddress().getPort(),payload);
                                }
                            }
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }



//                for(Map.Entry<InetSocketAddress,Boolean> dest : DHT.getPeers().entrySet()){
//                    if(!dest.getValue()){
//                        try{
//
//                            UDP.Packet packet = DHT.ping(DHT.getSocket(),dest.getKey()).get(1, TimeUnit.SECONDS);
//                            DHT.Message<?> message = DHT.Message.fromBencode(packet.getData());
//                            json.put(dest.getKey().toString(),message.getPayload());
//                        }catch(Exception e){
//                            json.put(dest.getKey().toString(),e.toString());
//                        }
//                    }
//                }
                }else{
                    json.put("error","Expecting one of 'ping','findNode' or 'findValue'.");
                }

                ByteBuf responseContent = Unpooled.copiedBuffer(json.toString().getBytes());
                FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),HttpResponseStatus.OK,responseContent);
                response.headers().add("Content-Length",responseContent.capacity());
                response.headers().add("Content-Type","application/json");
                ctx.write(response);
                return;
            }
            byte[] fileData = null;
            try{
                fileData = HTTPHandler.readResource(HTTPHandler.getResource(uri.getPath().substring(1)));
            }catch(Exception ignored){
            }
            boolean ok = fileData!=null;

            String contentType = null;
            if("/earth.png".equals(uri.getPath())){
                contentType = "image/png";
            }
            if("/favicon.ico".equals(uri.getPath())){
                contentType = "image/vnd.microsoft.icon";
            }
            if("/globe.css".equals(uri.getPath())){
                contentType = "text/css";
            }
            if("/globe.js".equals(uri.getPath())){
                contentType = "text/javascript";
            }

            ByteBuf responseContent = ok?Unpooled.copiedBuffer(fileData):Unpooled.copiedBuffer("File not found.\r\n".getBytes());
            FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),ok?HttpResponseStatus.OK:HttpResponseStatus.NOT_FOUND,responseContent);
            response.headers().add("Content-Length",responseContent.capacity());
            response.headers().add("Content-Type",contentType==null?"text/html":contentType);
            ctx.write(response);
            return;
        }

        ByteBuf responseContent = Unpooled.copiedBuffer("Method not allowed.\r\n".getBytes());
        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),HttpResponseStatus.METHOD_NOT_ALLOWED,responseContent);
        response.headers().add("Content-Length",responseContent.capacity());
        response.headers().add("Content-Type","text/html");
        ctx.write(response);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx){
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        HTTPHandler.LOGGER.log(Level.WARNING,"Exception during HTTP handling",cause);
        ctx.close();
    }

    private static byte[] readResource(InputStream in) throws IOException{
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static InputStream getResource(String name){
        return HTTPHandler.class.getClassLoader().getResourceAsStream(name);
    }

    private static byte[] getJSHash(){
        if(HTTPHandler.jsHash==null){
            try{
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                HTTPHandler.jsHash = md.digest(HTTPHandler.readResource(HTTPHandler.getResource("globe.js")));
            }catch(Exception ignored){}
        }
        return HTTPHandler.jsHash;
    }

}