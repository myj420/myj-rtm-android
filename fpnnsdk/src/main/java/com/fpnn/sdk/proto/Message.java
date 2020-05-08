package com.fpnn.sdk.proto;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * Created by shiwangxing on 2017/11/29.
 */

public class Message {

    protected Map payload;

    //-----------------[ Constructor Functions ]-------------------
    public Message() {
        payload = new TreeMap<String, Object>();
    }

    public Message(Map body) {
        payload = body;
    }

    //-----------------[ Properties Functions ]-------------------

    public Map getPayload() {
        return payload;
    }

    public void setPayload(Map p) {
        payload = p;
    }

    //-----------------[ Data Accessing Functions ]-------------------

    public void param(String key, Object value) {
        payload.put(key, value);
    }

    public Object get(String key) {
        return payload.get(key);
    }

    public String getString(String key) {
        Object o = payload.get(key);
        return (o == null)? "" :String.valueOf(get(key));
    }

    public Object get(String key, Object def) {
        Object o = payload.get(key);
        return (o != null) ? o : def;
    }

    public boolean wantBoolean(String key) {
        return (boolean)want(key);
    }

    public int wantInt(String key) {
        int value = -1;
        Object obj = want(key);
        if (obj instanceof Integer)
            value = (Integer) obj;
        else if (obj instanceof Long)
            value = ((Long) obj).intValue();
        else if (obj instanceof BigInteger)
            value = ((BigInteger) obj).intValue();
        else if (obj instanceof Short)
            value = ((Short) obj).intValue();
        else if (obj instanceof Byte)
            value = ((Byte) obj).intValue();
        else
            value = Integer.valueOf(String.valueOf(obj));
        return value;
    }


    public long wantLong(String key) {
        long value = -1;
        Object obj = want(key);
        if (obj instanceof Integer)
            value = ((Integer) obj).intValue();
        else if (obj instanceof Long)
            value = ((Long) obj).longValue();
        else if (obj instanceof BigInteger)
            value = ((BigInteger) obj).longValue();
        else if (obj instanceof Short)
            value = ((Short) obj).shortValue();
        else if (obj instanceof Byte)
            value = ((Byte) obj).byteValue();
        else
            value = Long.valueOf(String.valueOf(obj));
        return value;
    }

    public String wantString(String key) {
        return String.valueOf(want(key));
    }

    public void wantStringMap(String key, Map<String, String> map) {
        Map<String, String> ret = (Map<String, String>) want(key);
        for (String value : ret.keySet())
            map.put(value, ret.get(key));
    }

    public void wantLongList(String key, List<Long> list) {
        List<Object> attrsList = (List<Object>) want(key);
        for (Object value : attrsList) {
            if (value instanceof Integer)
                list.add(((Integer) value).longValue());
            else if (value instanceof Long)
                list.add(((Long) value).longValue());
            else if (value instanceof BigInteger)
                list.add(((BigInteger) value).longValue());
            else
                list.add(Long.valueOf(String.valueOf(value)));
        }
    }

    public void wantListHashMap(String key, List<Map<String, String>> attributes) {
        List<Object> attrsList = (List<Object>) want(key);
        for (Object value : attrsList)
            attributes.add(new HashMap<String, String>((Map<String, String>) value));
    }

    public void wantLongHashSet(String key, HashSet<Long> uids) {
        List<Object> list = (List<Object>) want(key);
        for (Object value : list) {
            if (value instanceof Integer)
                uids.add(((Integer) value).longValue());
            else if (value instanceof Long)
                uids.add(((Long) value).longValue());
            else if (value instanceof BigInteger)
                uids.add(((BigInteger) value).longValue());
            else
                uids.add(Long.valueOf(String.valueOf(value)));
        }
    }

    public Object want(String key) throws NoSuchElementException {
        Object o = payload.get(key);
        if (o == null)
            throw new NoSuchElementException("Cannot found object for key: " + key);

        return o;
    }

    //-----------------[ To Bytes Array Functions ]-------------------
    public byte[] toByteArray() throws IOException {
        MessagePayloadPacker packer = new MessagePayloadPacker();
        packer.pack(payload);
        return packer.toByteArray();
    }

    public byte[] raw() throws IOException {
        return toByteArray();
    }
}
