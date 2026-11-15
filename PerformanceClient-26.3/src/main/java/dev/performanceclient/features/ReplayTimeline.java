package dev.performanceclient.features;
import java.util.*;
/** Stores local camera keyframes only. Does not intercept packets or record the game world. */
public final class ReplayTimeline {
    public record Keyframe(long tick,double x,double y,double z,float yaw,float pitch){}
    private final NavigableMap<Long,Keyframe> frames=new TreeMap<>();
    public void add(Keyframe frame){frames.put(frame.tick(),frame);}
    public Keyframe sample(long tick){
        var a=frames.floorEntry(tick);var b=frames.ceilingEntry(tick);
        if(a==null)return b==null?null:b.getValue();if(b==null)return a.getValue();if(a.getKey().equals(b.getKey()))return a.getValue();
        double t=(tick-a.getKey())/(double)(b.getKey()-a.getKey());Keyframe v=a.getValue(),w=b.getValue();
        return new Keyframe(tick,lerp(v.x,w.x,t),lerp(v.y,w.y,t),lerp(v.z,w.z,t),(float)lerp(v.yaw,w.yaw,t),(float)lerp(v.pitch,w.pitch,t));
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
}
