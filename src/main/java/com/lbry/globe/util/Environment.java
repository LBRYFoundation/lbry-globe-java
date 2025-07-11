package com.lbry.globe.util;

public class Environment{

    public static String getVariable(String key){
        return Environment.getVariable(key,null);
    }

    public static String getVariable(String key,String defaultValue){
        String value = System.getenv(key);
        if(value==null){
            return defaultValue;
        }
        return value;
    }

}