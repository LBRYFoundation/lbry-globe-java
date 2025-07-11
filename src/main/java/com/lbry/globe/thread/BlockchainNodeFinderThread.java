package com.lbry.globe.thread;

import com.lbry.globe.api.API;
import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;
import com.lbry.globe.util.GeoIP;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class BlockchainNodeFinderThread implements Runnable{

    @Override
    public void run() {
        while(true){
            try{
                HttpURLConnection conn = (HttpURLConnection) new URI(System.getenv("BLOCKCHAIN_RPC_URL")).toURL().openConnection();
                conn.setDoOutput(true);
                conn.addRequestProperty("Authorization","Basic "+ Base64.getEncoder().encodeToString((System.getenv("BLOCKCHAIN_USERNAME")+":"+System.getenv("BLOCKCHAIN_PASSWORD")).getBytes()));
                conn.connect();
                conn.getOutputStream().write(new JSONObject().put("id",new Random().nextInt()).put("method","getnodeaddresses").put("params",new JSONArray().put(2147483647)).toString().getBytes());
                InputStream in = conn.getInputStream();
                if(in==null){
                    in = conn.getErrorStream();
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine())!=null){
                    sb.append(line);
                }
                JSONObject json = new JSONObject(sb.toString());
                manageBlockchainNodes(json.getJSONArray("result"));
            }catch(Exception e){
                e.printStackTrace();
            }
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void manageBlockchainNodes(JSONArray nodes){
        Map<InetAddress,JSONObject> data = new TreeMap<>(Comparator.comparing(InetAddress::getHostAddress));
        for(int i=0;i<nodes.length();i++){
            JSONObject node = nodes.getJSONObject(i);
            String hostname = node.getString("address");
            try{
                for(InetAddress ip : InetAddress.getAllByName(hostname)){
                    data.put(ip,node);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        for(Node n : API.NODES.values()){
            for(Service s : n.getServices()){
                long difference = System.currentTimeMillis()-s.getLastSeen();
                if(difference>60_000){
                    s.markedForRemoval = true;
                }
            }
        }

        for(Map.Entry<InetAddress,JSONObject> node : data.entrySet()){
            Node existingNode = API.NODES.get(node.getKey());
            if(existingNode==null){
                JSONObject geoData = GeoIP.getCachedGeoIPInformation(node.getKey());
                Double[] coords = GeoIP.getCoordinateFromLocation((geoData!=null && geoData.has("loc"))?geoData.getString("loc"):null);
                existingNode = new Node(node.getKey(),coords[0],coords[1]);
                API.NODES.put(node.getKey(),existingNode);
            }

            Service blockchainService = null;
            for(Service s : existingNode.getServices()){
                if(node.getValue().getInt("port")==s.getPort() && "blockchain".equals(s.getType())){
                    blockchainService = s;
                    blockchainService.markedForRemoval = false;
                    break;
                }
            }
            if(blockchainService==null){
                existingNode.getServices().add(new Service(UUID.randomUUID(),node.getValue().getInt("port"),"blockchain"));
            }else{
                blockchainService.updateLastSeen();
            }
        }

        for(Node n : API.NODES.values()){
            List<Service> removedService = new ArrayList<>();
            for(Service s : n.getServices()){
                if(s.markedForRemoval && "blockchain".equals(s.getType())){
                    removedService.add(s);
                }
            }
            n.getServices().removeAll(removedService);
        }
        API.saveNodes();
    }

}
