package com.lbry.globe.util;

public final class Hex{

    private static final char[] CHARS = "0123456789ABCDEF".toCharArray();

    public static byte[] decode(String s){
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String encode(byte[] data){
        char[] hexChars = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = CHARS[v >>> 4];
            hexChars[j * 2 + 1] = CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

}