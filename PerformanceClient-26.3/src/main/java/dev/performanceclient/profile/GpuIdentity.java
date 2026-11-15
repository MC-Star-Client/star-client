package dev.performanceclient.profile;
import dev.performanceclient.renderer.adapter.Minecraft263Adapter;
import java.util.Locale;
public record GpuIdentity(String vendor,String renderer,String driver,Minecraft263Adapter.Backend backend) {
    public String family(){
        String s=(vendor+" "+renderer).toLowerCase(Locale.ROOT);
        if(s.contains("nvidia")||s.contains("geforce"))return "nvidia";
        if(s.contains("advanced micro devices")||s.contains("amd")||s.contains("radeon")||s.contains("ati "))return "amd";
        if(s.contains("apple"))return "apple";
        if(s.contains("intel"))return "intel";
        return "other";
    }
    public static GpuIdentity from(Minecraft263Adapter.DeviceDescriptor d){
        return new GpuIdentity(d.vendor(),d.gpu(),d.driver(),d.backend());
    }
}
