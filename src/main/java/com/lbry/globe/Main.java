package com.lbry.globe;

import com.lbry.globe.api.API;
import com.lbry.globe.handler.HTTPHandler;
import com.lbry.globe.logging.LogLevel;
import com.lbry.globe.thread.BlockchainNodeFinderThread;
import com.lbry.globe.thread.DHTNodeFinderThread;
import com.lbry.globe.thread.HubNodeFinderThread;
import com.lbry.globe.util.GeoIP;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Arrays;
import java.util.logging.Logger;

public class Main implements Runnable{

    private static final Logger LOGGER = Logger.getLogger("Main");

    static{
        System.setProperty("java.util.logging.SimpleFormatter.format","%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL [%4$s/%3$s]: %5$s%6$s%n");
    }

    public Main(String... args){
        Main.LOGGER.info("Arguments = "+ Arrays.toString(args));
    }

    @Override
    public void run(){
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(new DefaultThreadFactory("Netty Event Loop"),NioIoHandler.newFactory());
        this.runTCPServerHTTP(group);
    }

    private void runTCPServerHTTP(EventLoopGroup group){
        ServerBootstrap b = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class);

        b.childHandler(new ChannelInitializer<SocketChannel>(){
            protected void initChannel(SocketChannel socketChannel){
                socketChannel.pipeline().addLast(new HttpRequestDecoder());
                socketChannel.pipeline().addLast(new HttpResponseEncoder());
                socketChannel.pipeline().addLast("http",new HTTPHandler());
            }
        });

        try{
            b.bind(80).sync();
        }catch(InterruptedException e){
            Main.LOGGER.log(LogLevel.ERROR,"Failed starting server",e);
        }
    }

    public static void main(String... args){
        Main.LOGGER.info("Loading nodes cache");
        Runtime.getRuntime().addShutdownHook(new Thread(API::saveNodes,"Save Nodes"));
        API.loadNodes();
        Main.LOGGER.info("Loading GeoIP cache");
        Runtime.getRuntime().addShutdownHook(new Thread(GeoIP::saveCache,"Save Cache"));
        GeoIP.loadCache();
        Main.LOGGER.info("Starting finder thread for blockchain nodes");
        new Thread(new BlockchainNodeFinderThread(),"Block Node Finder").start();
        Main.LOGGER.info("Starting finder thread for DHT nodes");
        new DHTNodeFinderThread().run();
        Main.LOGGER.info("Starting finder thread for hub nodes");
        new HubNodeFinderThread().run();
        Main.LOGGER.info("Starting server");
        new Main(args).run();
    }

}