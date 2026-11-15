package dev.performanceclient.config;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
public final class ClientPreferences {
    private final Path file;
    private final Properties properties=new Properties();
    public ClientPreferences(Path base){file=base.resolve("performanceclient/settings.properties");}
    public void load(){
        if(!Files.isRegularFile(file))return;
        try(InputStream in=Files.newInputStream(file)){properties.load(in);}catch(IOException e){System.err.println("Client preferences: "+e);}
    }
    public void save(){
        try{Files.createDirectories(file.getParent());try(OutputStream out=Files.newOutputStream(file)){properties.store(out,"PerformanceClient 26.3 options");}}
        catch(IOException e){System.err.println("Cannot save preferences: "+e);}
    }
    public String get(String key,String defaultValue){return properties.getProperty(key,defaultValue);}
    public boolean toggle(String key,boolean defaultValue){boolean next=!Boolean.parseBoolean(get(key,""+defaultValue));properties.setProperty(key,Boolean.toString(next));save();return next;}
    public boolean enabled(String key,boolean fallback){return Boolean.parseBoolean(get(key,""+fallback));}
    public void put(String key,String value){properties.setProperty(key,value);save();}
    public int integer(String key,int fallback){try{return Integer.parseInt(get(key,""+fallback));}catch(NumberFormatException e){return fallback;}}
}
