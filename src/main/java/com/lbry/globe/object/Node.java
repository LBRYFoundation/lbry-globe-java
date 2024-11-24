package com.lbry.globe.object;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Node{

    private final InetAddress address;
    private Double latitude;
    private Double longitude;
    private List<Service> services = new ArrayList<>();

    public Node(InetAddress address,Double latitude,Double longitude){
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public InetAddress getAddress() {
        return this.address;
    }

    public Double getLatitude() {
        return this.latitude;
    }

    public Double getLongitude() {
        return this.longitude;
    }

    public List<Service> getServices(){
        return this.services;
    }

}