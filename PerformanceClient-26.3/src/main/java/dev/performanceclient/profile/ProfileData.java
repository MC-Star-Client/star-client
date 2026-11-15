package dev.performanceclient.profile;
import java.util.*;
/** Flat String/number JSON profile schema; arbitrary keys survive as strings. */
public final class ProfileData {
    private final LinkedHashMap<String,String> values=new LinkedHashMap<>();
    public void put(String k,String v){values.put(k,v);}
    public String get(String key,String fallback){return values.getOrDefault(key,fallback);}
    public int integer(String key,int fallback){try{return Integer.parseInt(values.get(key));}catch(Exception e){return fallback;}}
    public boolean enabled(String key,boolean fallback){return Boolean.parseBoolean(get(key,Boolean.toString(fallback)));}
    public void merge(ProfileData override){values.putAll(override.values);}
    public Map<String,String> entries(){return Collections.unmodifiableMap(values);}
}
