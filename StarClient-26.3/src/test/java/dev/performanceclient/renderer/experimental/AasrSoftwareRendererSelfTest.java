package dev.performanceclient.renderer.experimental;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Tests that directional-mask ray hits agree with an independently traced occupancy grid. */
public final class AasrSoftwareRendererSelfTest {
    public static void main(String[] args) {
        Random rng=new Random(0x26A5L);
        int checks=0;
        for(int test=0;test<24;test++) {
            byte[] map=new byte[4096];
            for(int i=0;i<4096;i++) if(rng.nextInt(100)<(test%6)*16) map[i]=1;
            AasrSoftwareRenderer.Scene scene=new AasrSoftwareRenderer.Scene() {
                public boolean solid(int x,int y,int z){return material(x,y,z)>0;}
                public int material(int x,int y,int z){
                    return x<0||x>=16||y<0||y>=16||z<0||z>=16?0:map[(y*16+z)*16+x];
                }
            };
            var renderer=new AasrSoftwareRenderer(scene);
            for(int k=0;k<500;k++) {
                var eye=new AasrSoftwareRenderer.Vec(rng.nextDouble(2,28),rng.nextDouble(2,28),-10);
                var dir=new AasrSoftwareRenderer.Vec(rng.nextDouble(-1,1),rng.nextDouble(-1,1),rng.nextDouble(.2,1));
                var actual=renderer.ray(eye,dir);
                var expected=renderer.referenceRay(eye,dir);
                if(actual==null?expected!=null:expected==null||actual.x()!=expected.x()||actual.y()!=expected.y()||actual.z()!=expected.z()||actual.face()!=expected.face())
                    throw new AssertionError("AASR disagrees with oracle at scenario="+test+" ray="+k+" actual="+actual+" expected="+expected);
                checks++;
            }
        }
        var demo=new AasrSoftwareRenderer(AasrSceneDemo.scene(2639950L));
        BufferedImage image=demo.render(96,64,new AasrSoftwareRenderer.Vec(26,20,-24),new AasrSoftwareRenderer.Vec(8,4,8),60);
        int hits=0; for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) if(image.getRGB(x,y)!=0xff142334)hits++;
        if(hits<200)throw new AssertionError("No meaningful rendered geometry: "+hits);
        if(demo.surfaceFaces()<=0)throw new AssertionError("No faces");
        System.out.println("PASS: "+checks+" masked rays agree with occupancy oracle, PNG-sized image had "+hits+" rendered pixels");
    }
}
