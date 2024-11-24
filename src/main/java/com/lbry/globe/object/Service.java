package com.lbry.globe.object;

import java.util.UUID;

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

}