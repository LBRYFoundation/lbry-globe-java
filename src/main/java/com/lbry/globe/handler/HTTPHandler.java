package com.lbry.globe.handler;

import com.lbry.globe.api.API;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;


import org.json.JSONArray;
import org.json.JSONObject;

public class HTTPHandler extends ChannelInboundHandlerAdapter{

    public static final AttributeKey<HttpRequest> ATTR_REQUEST = AttributeKey.newInstance("request");
    public static final AttributeKey<List<HttpContent>> ATTR_CONTENT = AttributeKey.newInstance("content");

    @Override
    public void channelRead(ChannelHandlerContext ctx,Object msg){
        if(msg instanceof HttpRequest){
            HttpRequest request = (HttpRequest) msg;
            ctx.channel().attr(HTTPHandler.ATTR_REQUEST).set(request);
        }
        if(msg instanceof HttpContent){
            HttpContent content = (HttpContent) msg;
            ctx.channel().attr(HTTPHandler.ATTR_CONTENT).setIfAbsent(new ArrayList<>());
            ctx.channel().attr(HTTPHandler.ATTR_CONTENT).get().add(content);

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
            byte[] fileData = null;
            try{
                fileData = HTTPHandler.readResource(HTTPHandler.getResource(uri.getPath().substring(1)));
            }catch(Exception ignored){
            }
            boolean ok = fileData!=null;

            String contentType = null;
            if("/earth.jpg".equals(uri.getPath())){
                contentType = "image/jpg";
            }
            if("/earth.png".equals(uri.getPath())){
                contentType = "image/png";
            }
            if("/favicon.ico".equals(uri.getPath())){
                contentType = "image/vnd.microsoft.icon";
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace(); //TODO
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

}