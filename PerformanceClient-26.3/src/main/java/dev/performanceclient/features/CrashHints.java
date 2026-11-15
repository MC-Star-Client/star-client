package dev.performanceclient.features;
import java.util.Locale;
public final class CrashHints {
    public static String explain(String log){
        String l=log.toLowerCase(Locale.ROOT);
        if(l.contains("mixin transformation")||l.contains("invalidinjectionexception"))return "Possible incompatible Mixin or changed Minecraft mapping; inspect first 'Caused by:'";
        if(l.contains("outofmemoryerror"))return "Check memory usage and -Xmx; avoid extreme allocation or too many mods.";
        if(l.contains("vk_error_device_lost"))return "GPU device lost; check driver, shader setup and Vulkan backend.";
        if(l.contains("could not find")&&l.contains("fabric"))return "Loader/mod dependency missing or version mismatch.";
        return "No known signature. Share the complete stack trace with sensitive tokens removed.";
    }
    private CrashHints(){}
}
