package dev.performanceclient.profile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Merge base -> operating-system -> GPU -> GPU+OS from editable config directory. */
public final class ProfileManager {
    private final Path directory;
    private String selected="auto";
    private String currentName="general";
    private ProfileData current=new ProfileData();
    public ProfileManager(Path configDir){directory=configDir.resolve("performanceclient/profiles");}
    public void initialize(){
        try{
            Files.createDirectories(directory);
            String[] shipped={"general","linux","windows","macos","amd","nvidia","intel","apple","amd-linux","nvidia-linux","amd-windows","nvidia-windows","apple-macos"};
            for(String name:shipped){Path file=directory.resolve(name+".json");
                try(InputStream in=ProfileManager.class.getResourceAsStream("/profiles/"+name+".json")){
                    if(in!=null&&!Files.exists(file))Files.copy(in,file);
                }
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public synchronized void select(String override){selected=(override==null||override.isBlank())?"auto":override;}
    public synchronized String selection(){return selected;}
    public synchronized ProfileData reload(PlatformInfo os,GpuIdentity gpu){
        ProfileData merged=new ProfileData();
        String family=gpu.family();String system=os.os();
        List<String> order=new ArrayList<>(List.of("general",system,family,family+"-"+system));
        if(!selected.equals("auto"))order.add(selected);
        for(String candidate:order){
            Path file=directory.resolve(candidate+".json").normalize();
            if(!file.startsWith(directory)||!Files.isRegularFile(file))continue;
            try{merged.merge(ProfileJson.parse(Files.readString(file,StandardCharsets.UTF_8)));currentName=candidate;}
            catch(IOException | IllegalArgumentException e){System.err.println("Invalid profile "+file+": "+e.getMessage());}
        }
        current=merged;return current;
    }
    public synchronized String activeName(){return currentName;}
    public synchronized ProfileData active(){return current;}
    public Path directory(){return directory;}
}
