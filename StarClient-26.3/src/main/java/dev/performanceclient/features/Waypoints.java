package dev.performanceclient.features;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
/** CSV storage only. Identifiers are never sent over network. */
public final class Waypoints {
    public record Point(String dimension,String name,int x,int y,int z){}
    public static void save(Path file,List<Point> points) throws IOException{
        Files.createDirectories(file.getParent());
        List<String> rows=new ArrayList<>();rows.add("dimension,name,x,y,z");
        for(Point p:points){
            if(p.name().contains(",")||p.name().contains("\n")||p.dimension().contains(","))throw new IllegalArgumentException("Invalid characters");
            rows.add(p.dimension()+","+p.name()+","+p.x()+","+p.y()+","+p.z());
        }
        Files.write(file,rows);
    }
    private Waypoints(){}
}
