package com.lbry.globe.object;

import java.util.UUID;

import org.json.JSONObject;

public class Service{

    private final UUID id;

    private final int port;

    private final String type;

    private long lastSeen;

    public boolean markedForRemoval;

    public Service(UUID id,int port,String type){
        this.id = id;
        this.port = port;
        this.type = type;
        this.updateLastSeen();
    }

    public UUID getId() {
        return this.id;
    }

    public int getPort() {
        return this.port;
    }

    public String getType() {
        return this.type;
    }

    public void updateLastSeen(){
        this.lastSeen = System.currentTimeMillis();
    }

    public long getLastSeen(){
        return this.lastSeen;
    }

    public JSONObject toJSONObject(){
        JSONObject obj = new JSONObject();
        obj.put("id",this.id.toString());
        obj.put("port",this.port);
        obj.put("type",this.type);
        obj.put("lastSeen",this.lastSeen);
        return obj;
    }

    public static Service fromJSONObject(JSONObject obj){
        Service service = new Service(UUID.fromString(obj.getString("id")),obj.getInt("port"),obj.getString("type"));
        service.lastSeen = obj.getLong("lastSeen");
        return service;
    }

}