package com.lbry.globe.util;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BencodeConverter{

    private static final Bencode BENCODE = new Bencode(StandardCharsets.US_ASCII,true);

    public static byte[] encode(Map<String,?> map){
        return BencodeConverter.BENCODE.encode(map);
    }

    public static Map<String,?> decode(byte[] bytes){
        return BencodeConverter.decode(bytes,ToStringConversion.ASCII);
    }

    public static Map<String,?> decode(byte[] bytes,ToStringConversion stringConversion){
        // Fix invalid B-encoding
        if(bytes[0]=='d'){
            bytes[0] = 'l';

            List<Object> list = BencodeConverter.BENCODE.decode(bytes,Type.LIST);
            for(int i=0;i<list.size();i++){
                if(i%2==0){
                    Object key = list.get(i);
                    if(key instanceof Number){
                        key = String.valueOf(key);
                    }
                    list.set(i,BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(key,stringConversion));
                }
            }
            bytes = BencodeConverter.BENCODE.encode(list);

            bytes[0] = 'd';
        }

        // Normal B-decoding
        return BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(BencodeConverter.BENCODE.decode(bytes,Type.DICTIONARY),stringConversion);
    }

    private static <V> V walkAndConvertByteBufferToByteArrayOrString(Object value,ToStringConversion stringConversion){
        if(value instanceof ByteBuffer){
            ByteBuffer bb = (ByteBuffer) value;
            byte[] ba = bb.array();
            boolean hasControlOrNonASCII = false;
            for(byte b : ba){
                int bv = b & 0xFF;
                if(bv<0x20 || bv>=0x7F){
                    hasControlOrNonASCII = true;
                    break;
                }
            }
            if(!ToStringConversion.NONE.equals(stringConversion) && (!hasControlOrNonASCII || ToStringConversion.ALL.equals(stringConversion))){
                return (V) new String(ba);
            }
            return (V) ba;
        }
        if(value instanceof List){
            List<?> list = (List<?>) value;
            list.replaceAll((v) -> BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(v,stringConversion));
        }
        if(value instanceof Map){
            Map<?,?> map = (Map<?,?>) value;
            map.replaceAll((k,v) -> BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(v,stringConversion));
        }

        return (V) value;
    }

    public enum ToStringConversion{
        NONE,
        ASCII,
        ALL,
    }

}
