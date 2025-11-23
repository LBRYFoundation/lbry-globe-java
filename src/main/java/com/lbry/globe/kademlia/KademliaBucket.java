package com.lbry.globe.kademlia;

import java.util.*;

public class KademliaBucket{

    private final List<KademliaTriple> list = new ArrayList<>();
    private final KademliaSystem system;

    public KademliaBucket(KademliaSystem system){
        this.system = system;
    }

    public List<KademliaTriple> getList(){
        return Collections.unmodifiableList(this.list);
    }

    public KademliaTriple getHead(){
        return !this.list.isEmpty()?this.list.getFirst():null;
    }

    public KademliaTriple removeFromHead(){
        return this.list.removeFirst();
    }

    public void insertAtTail(KademliaTriple triple){
        if(this.size()<this.system.getK()) {
            this.list.addLast(triple);
        }
    }

    public void moveToTail(KademliaTriple triple){
        if(this.list.remove(triple)){
            this.insertAtTail(triple);
        }
    }

    public int size(){
        return this.list.size();
    }

}