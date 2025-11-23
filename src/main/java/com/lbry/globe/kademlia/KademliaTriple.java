package com.lbry.globe.kademlia;

import com.lbry.globe.util.Hex;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class KademliaTriple{

    private final InetAddress ip_address;
    private final int udp_port;
    private final byte[] node_id;

    public KademliaTriple(InetAddress ip_address,int udp_port,byte[] node_id){
        this.ip_address = ip_address;
        this.udp_port = udp_port & 0xFFFF;
        this.node_id = node_id;
    }

    public InetAddress getIPAddress(){
        return this.ip_address;
    }

    public int getUDPPort(){
        return this.udp_port;
    }

    public byte[] getNodeID(){
        return this.node_id;
    }

    public InetSocketAddress getInetSocketAddress(){
        return new InetSocketAddress(this.ip_address,this.udp_port & 0xFFFF);
    }

    @Override
    public String toString() {
        return "KademliaTriple{" +
                "ip_address=" + this.ip_address +
                ", udp_port=" + this.udp_port +
                ", node_id=" + Hex.encode(this.node_id) +
                '}';
    }

}