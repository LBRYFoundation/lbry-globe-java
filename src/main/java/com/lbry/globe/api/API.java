package com.lbry.globe.api;

import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

public class API{

    private static final Logger LOGGER = Logger.getLogger("API");
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
                obj.put("notSeenTime",System.currentTimeMillis() - service.getLastSeen());
                points.put(obj);
            }
        }
    }

    public static void loadNodes(){
        File file = new File("nodes.json");
        if(file.exists()){
            try{
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine())!=null){
                    sb.append(line);
                }
                JSONObject obj = new JSONObject(sb.toString());
                for(String key : obj.keySet()){
                    API.NODES.put(InetAddress.getByName(key),Node.fromJSONObject(obj.getJSONObject(key)));
                }
                br.close();
            }catch(Exception e){
                API.LOGGER.log(Level.WARNING,"Failed loading nodes.",e);
            }
        }
    }

    public static void saveNodes(){
        try{
            FileOutputStream fos = new FileOutputStream("nodes.json");
            JSONObject obj = new JSONObject();
            for(Map.Entry<InetAddress,Node> entry : API.NODES.entrySet()){
                obj.put(entry.getKey().getHostAddress(),entry.getValue().toJSONObject());
            }
            fos.write(obj.toString().getBytes());
            fos.close();
        }catch(Exception e){
            API.LOGGER.log(Level.WARNING,"Failed saving nodes.",e);
        }
    }

}
