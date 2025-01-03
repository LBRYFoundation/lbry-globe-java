package com.lbry.globe.thread;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

import com.lbry.globe.api.API;
import com.lbry.globe.object.Node;
import com.lbry.globe.object.Service;
import com.lbry.globe.util.GeoIP;

import org.json.JSONArray;
import org.json.JSONObject;

public class HubNodeFinderThread implements Runnable{

    public static final String[] HUBS = {
            "spv11.lbry.com",
            "spv12.lbry.com",
            "spv13.lbry.com",
            "spv14.lbry.com",
            "spv15.lbry.com",
            "spv16.lbry.com",
            "spv17.lbry.com",
            "spv18.lbry.com",
            "spv19.lbry.com",
            "hub.lbry.grin.io",
            "hub.lizard.technology",
            "s1.lbry.network",

            "hub.lbry.nl",
    };

    private static final Map<InetAddress,Long> LAST_SEEN = new TreeMap<>(Comparator.comparing(InetAddress::getHostAddress));

    @Override
    public void run() {
        while(true){
            for(String hostname : HubNodeFinderThread.HUBS){
                try{
                    for(InetAddress ip : InetAddress.getAllByName(hostname)){
                        new Thread(() -> {
                            try{
                                Socket s = new Socket(ip,50001);
                                JSONObject obj = new JSONObject();
                                obj.put("id",new Random().nextInt());
                                obj.put("method","server.banner");
                                obj.put("params",new JSONArray());
                                s.getOutputStream().write((obj+"\n").getBytes());
                                s.getOutputStream().flush();
                                InputStream in = s.getInputStream();
                                StringBuilder sb = new StringBuilder();
                                int b;
                                while((b = in.read())!='\n'){
                                    sb.append(new String(new byte[]{(byte) (b & 0xFF)}));
                                }
                                in.close();
                                JSONObject respObj = new JSONObject(sb.toString());
                                boolean successful = respObj.has("result") && !respObj.isNull("result");
                                if(successful){
                                    LAST_SEEN.put(ip,System.currentTimeMillis());
                                }
                            }catch(Exception e){
                            }
                        }).start();
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }

            List<InetAddress> removeIPs = new ArrayList<>();
            for(Map.Entry<InetAddress,Long> entry : HubNodeFinderThread.LAST_SEEN.entrySet()){
                long difference = System.currentTimeMillis()-entry.getValue();
                if(difference>60_000){
                    removeIPs.add(entry.getKey());
                }
            }
            for(InetAddress removeIP : removeIPs){
                boolean isBootstrap = false;
                for(String bootstrap : HubNodeFinderThread.HUBS){
                    if(bootstrap.equalsIgnoreCase(removeIP.getHostName())){
                        isBootstrap = true;
                        break;
                    }
                }
                if(!isBootstrap){
                    HubNodeFinderThread.LAST_SEEN.remove(removeIP);
                }
                Node n = API.NODES.get(removeIP);
                if(n!=null){
                    List<Service> removeServices = new ArrayList<>();
                    for(Service s : n.getServices()){
                        if(s.getPort()==50001 && "hub".equals(s.getType())){
                            removeServices.add(s);
                        }
                    }
                    n.getServices().removeAll(removeServices);
                }
            }
            for(Map.Entry<InetAddress,Long> entry : HubNodeFinderThread.LAST_SEEN.entrySet()){
                Node existingNode = API.NODES.get(entry.getKey());
                if(existingNode==null){
                    JSONObject geoData = GeoIP.getCachedGeoIPInformation(entry.getKey());
                    Double[] coords = GeoIP.getCoordinateFromLocation(geoData.has("loc")?geoData.getString("loc"):null);
                    existingNode = new Node(entry.getKey(),coords[0],coords[1]);
                    API.NODES.put(entry.getKey(),existingNode);
                }
                Service hubService = null;
                for(Service s : existingNode.getServices()){
                    if(s.getPort()==50001 && "hub".equals(s.getType())){
                        hubService = s;
                        break;
                    }
                }

                if(hubService==null){
                    existingNode.getServices().add(new Service(UUID.randomUUID(),50001,"hub"));
                }else{
                    hubService.updateLastSeen();
                }
            }

            API.saveNodes();

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
