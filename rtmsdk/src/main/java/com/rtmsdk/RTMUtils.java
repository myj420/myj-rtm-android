package com.rtmsdk;

import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;

public class RTMUtils {
    static private AtomicLong orderId = new AtomicLong();

    static public long genMid() {
        long id = getCurrentMilliseconds();
        return id + orderId.incrementAndGet();
    }

    public static long getCurrentSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    public static long getCurrentMilliseconds() {
        return System.currentTimeMillis();
    }

    public static byte[] intToByteArray(int value) {
        byte[] byteArray = new byte[4];
        byteArray[0] = (byte) (value & 0xFF);
        byteArray[1] = (byte) (value >> 8 & 0xFF);
        byteArray[2] = (byte) (value >> 16 & 0xFF);
        byteArray[3] = (byte) (value >> 24 & 0xFF);
        return byteArray;
    }

    public static int byteArrayToInt(byte[] byteArray){
        if(byteArray.length != 4){
            return 0;
        }
        int value = byteArray[0] & 0xFF;
        value |= byteArray[1] << 8;
        value |= byteArray[2] << 16;
        value |= byteArray[3] << 24;
        return value;
    }
}


class MD5Utils {
    private static final String hexDigIts[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

    public static String getMd5(byte[] origin, boolean upper) {
        return getMd5(new String(origin), upper, "utf-8");
    }

    public static String getMd5(String origin, boolean upper) {
        return getMd5(new String(origin), upper, "utf-8");
    }

    public static String getMd5(byte[] origin, boolean upper, String charsetname) {
        return getMd5(new String(origin), upper, charsetname);
    }

    public static String getMd5(String origin, boolean upper, String charsetname) {
        String resultString = null;
        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("MD5");
            if (null == charsetname || "".equals(charsetname)) {
                resultString = byteArrayToHexString(md.digest(resultString.getBytes()));
            } else {
                resultString = byteArrayToHexString(md.digest(resultString.getBytes(charsetname)));
            }
        } catch (Exception e) {
        }
        if (upper)
            return resultString.toUpperCase();
        return resultString;
    }

    public static String byteArrayToHexString(byte b[]) {
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            resultSb.append(byteToHexString(b[i]));
        }
        return resultSb.toString();
    }

    public static String byteToHexString(byte b) {
        int n = b;
        if (n < 0) {
            n += 256;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        return hexDigIts[d1] + hexDigIts[d2];
    }
}