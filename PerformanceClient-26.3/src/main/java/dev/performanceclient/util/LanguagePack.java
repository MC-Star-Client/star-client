package dev.performanceclient.util;
import java.util.Map;
public final class LanguagePack {
    private static final Map<String,String> DE=Map.of("settings","Einstellungen","profile","Profil","benchmark","Benchmark","mode","Modus","back","Zurueck");
    private static final Map<String,String> EN=Map.of("settings","Settings","profile","Profile","benchmark","Benchmark","mode","Mode","back","Back");
    public static String translate(String key,String locale){return (locale.startsWith("de")?DE:EN).getOrDefault(key,key);}
    private LanguagePack(){}
}
