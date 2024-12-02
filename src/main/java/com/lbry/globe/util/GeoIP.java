package com.lbry.globe.util;

import java.io.*;
import java.net.*;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

public class GeoIP{

    private static final Map<InetAddress,JSONObject> CACHE = new TreeMap<>(Comparator.comparing(InetAddress::getHostAddress));
    private static final Logger LOGGER = Logger.getLogger("GeoIP");
    private static final String TOKEN = System.getenv("IPINFO_TOKEN");

    public static JSONObject getCachedGeoIPInformation(InetAddress ip){
        JSONObject result = CACHE.get(ip);
        if(result==null){
            try{
                result = GeoIP.getGeoIPInformation(ip);
                GeoIP.CACHE.put(ip,result);
                GeoIP.saveCache();
            }catch(Exception e){
                GeoIP.LOGGER.log(Level.WARNING,"Failed getting cached GeoIP information.",e);
            }
        }
        return result;
    }

    public static JSONObject getGeoIPInformation(InetAddress ip) throws IOException,URISyntaxException{
        HttpURLConnection conn = (HttpURLConnection) new URI("https://ipinfo.io/"+ip.getHostAddress()+"?token="+GeoIP.TOKEN).toURL().openConnection();
        conn.connect();
        InputStream in = conn.getInputStream();
        if(in==null){
            in = conn.getErrorStream();
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String line;
        StringBuilder sb = new StringBuilder();
        while((line = br.readLine())!=null){
            sb.append(line);
        }
        return new JSONObject(sb.toString());

    }

    public static Double[] getCoordinateFromLocation(String location){
        if(location==null){
            return new Double[]{null,null};
        }
        String[] parts = location.split(",");
        return new Double[]{Double.parseDouble(parts[0]),Double.parseDouble(parts[1])};
    }

    public static void loadCache(){
        try{
            BufferedReader br = new BufferedReader(new FileReader("geoip.json"));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine())!=null){
                sb.append(line);
            }
            JSONObject obj = new JSONObject(sb.toString());
            for(String key : obj.keySet()){
                GeoIP.CACHE.put(InetAddress.getByName(key),obj.getJSONObject(key));
            }
            br.close();
        }catch(Exception e){
            GeoIP.LOGGER.log(Level.WARNING,"Failed loading GeoIP cache.",e);
        }
    }

    public static void saveCache(){
        try{
            FileOutputStream fos = new FileOutputStream("geoip.json");
            JSONObject obj = new JSONObject();
            for(Map.Entry<InetAddress,JSONObject> entry : GeoIP.CACHE.entrySet()){
                obj.put(entry.getKey().getHostAddress(),entry.getValue());
            }
            fos.write(obj.toString().getBytes());
            fos.close();
        }catch(Exception e){
            GeoIP.LOGGER.log(Level.WARNING,"Failed saving GeoIP cache.",e);
        }
    }

}