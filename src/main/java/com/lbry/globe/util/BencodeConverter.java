package com.lbry.globe.util;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class BencodeConverter{

    private static final Bencode BENCODE = new Bencode(true);

    public static byte[] encode(Map<String,?> map){
        return BencodeConverter.BENCODE.encode(map);
    }

    public static Map<String,?> decode(byte[] bytes){
        // Fix invalid B-encoding
        if(bytes[0]=='d'){
            bytes[0] = 'l';
        }
        List<Object> list = BencodeConverter.BENCODE.decode(bytes,Type.LIST);
        for(int i=0;i<list.size();i++){
            if(i%2==0){
                list.set(i,String.valueOf(list.get(i)));
            }
        }
        bytes = BencodeConverter.BENCODE.encode(list);
        if(bytes[0]=='l'){
            bytes[0] = 'd';
        }

        // Normal B-decoding
        return BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(BencodeConverter.BENCODE.decode(bytes,Type.DICTIONARY));
    }

    private static <V> V walkAndConvertByteBufferToByteArrayOrString(Object value){
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
            if(hasControlOrNonASCII){
                return (V) ba;
            }
            return (V) new String(ba);
        }
        if(value instanceof List){
            List<Object> l = (List<Object>) value;
            l.replaceAll(BencodeConverter::walkAndConvertByteBufferToByteArrayOrString);
        }
        if(value instanceof Map){
            Map<Object,Object> m = (Map<Object,Object>) value;
            m.replaceAll((k,v) -> BencodeConverter.walkAndConvertByteBufferToByteArrayOrString(v));
        }
        return (V) value;
    }

}
