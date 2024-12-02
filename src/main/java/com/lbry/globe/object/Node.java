package com.lbry.globe.object;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Node{

    private final InetAddress address;
    private Double latitude;
    private Double longitude;
    private final List<Service> services = new ArrayList<>();

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

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return this.longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public List<Service> getServices(){
        return this.services;
    }

}