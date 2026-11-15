package dev.performanceclient.hud;
import java.util.*;
/** Coordinates for draggable UI elements. Actual drag events and render are TODO 26.3 HUD adapter. */
public final class HudLayout {
    public record Placement(int x,int y,boolean enabled){}
    private final Map<String,Placement> widgets=new HashMap<>();
    public void move(String id,int x,int y){Placement old=widgets.getOrDefault(id,new Placement(10,10,true));widgets.put(id,new Placement(x,y,old.enabled()));}
    public void setEnabled(String id,boolean active){Placement old=widgets.getOrDefault(id,new Placement(10,10,true));widgets.put(id,new Placement(old.x(),old.y(),active));}
    public Placement get(String id){return widgets.getOrDefault(id,new Placement(10,10,true));}
}
