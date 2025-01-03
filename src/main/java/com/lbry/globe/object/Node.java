package com.lbry.globe.object;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public JSONObject toJSONObject(){
        JSONObject obj = new JSONObject();
        obj.put("address",this.address.toString());
        obj.put("latitude",this.latitude);
        obj.put("longitude",this.longitude);
        JSONArray servicesArr = new JSONArray();
        for(Service service : this.services){
            servicesArr.put(service.toJSONObject());
        }
        obj.put("services",servicesArr);
        return obj;
    }

    public static Node fromJSONObject(JSONObject obj){
        System.out.println(obj);
        Node node = new Node(Node.addressFromString(obj.getString("address")),obj.has("latitude")?obj.getDouble("latitude"):null,obj.has("longitude")?obj.getDouble("longitude"):null);
        JSONArray servicesArr = obj.getJSONArray("services");
        for(int i=0;i<servicesArr.length();i++){
            node.services.add(Service.fromJSONObject(servicesArr.getJSONObject(i)));
        }
        return node;
    }

    private static InetAddress addressFromString(String str){
        String[] parts = str.split("/",2);
        try{
            return InetAddress.getByAddress(parts[0],InetAddress.getByName(parts[1]).getAddress());
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

}