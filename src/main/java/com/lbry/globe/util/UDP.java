package com.lbry.globe.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UDP{

    private static final int BUFFER_SIZE = 4096;

    public static void send(DatagramSocket socket,Packet packet) throws IOException{
        socket.send(packet.getDatagramPacket());
    }

    public static Packet receive(DatagramSocket socket) throws IOException{
        Packet packet = new Packet(new DatagramPacket(new byte[UDP.BUFFER_SIZE],UDP.BUFFER_SIZE));
        socket.receive(packet.getDatagramPacket());
        return packet;
    }

    public static class Packet{

        private final DatagramPacket packet;

        protected Packet(DatagramPacket packet){
            this.packet = packet;
        }

        public Packet(InetSocketAddress address,byte[] data){
            this.packet = new DatagramPacket(data,data.length,address);
        }

        public InetSocketAddress getAddress(){
            return (InetSocketAddress) this.packet.getSocketAddress();
        }

        public byte[] getData(){
            byte[] data = new byte[this.packet.getLength()];
            System.arraycopy(this.packet.getData(),0,data,0,data.length);
            return data;
        }

        public DatagramPacket getDatagramPacket(){
            return this.packet;
        }

    }

}