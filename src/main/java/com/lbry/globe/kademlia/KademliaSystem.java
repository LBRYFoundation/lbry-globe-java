package com.lbry.globe.kademlia;

import java.util.ArrayList;
import java.util.List;

public class KademliaSystem{

    private final List<KademliaBucket> buckets = new ArrayList<>();

    private int alpha;
    private int k;

    private byte[] nodeID;

    public KademliaSystem(int alpha,int k,int n){
        this.alpha = alpha;
        this.k = k;
        for(int i=0;i<n;i++){
            this.buckets.add(new KademliaBucket(this));
        }
    }

    public int getAlpha() {
        return this.alpha;
    }

    public int getK() {
        return this.k;
    }

    public KademliaBucket getBucket(int i){
        return this.buckets.size()-1>=i?this.buckets.get(i):null;
    }

    public void setK(int k){
        if(this.k>k){
            int d = this.k-k;
            for(KademliaBucket bucket : this.buckets){
                for(int i=0;i<d;i++){
                    bucket.removeFromHead();
                }
            }
            return;
        }
        this.k = k;
    }

}