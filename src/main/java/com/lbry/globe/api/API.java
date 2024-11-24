package com.lbry.globe.api;

import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class API{

    public static final Map<InetAddress, Node> NODES = new TreeMap<>(Comparator.comparing(InetAddress::getHostAddress));

    public static void fillPoints(JSONArray points){
        for(Node node : API.NODES.values()){
            for(Service service : node.getServices()){
                JSONObject obj = new JSONObject();
                obj.put("id",service.getId().toString());
                String hostname = node.getAddress().toString().split("/")[0];
                String address = node.getAddress().getHostAddress();
                if(node.getAddress() instanceof Inet6Address){
                    address = "["+address+"]";
                }
                obj.put("label",(!hostname.isEmpty()?(hostname+":"+service.getPort()+" - "):"")+address+":"+service.getPort()+" ("+service.getType()+")");
                obj.put("lat",node.getLatitude());
                obj.put("lng",node.getLongitude());
                obj.put("type",service.getType());
                points.put(obj);
            }
        }
    }

}
