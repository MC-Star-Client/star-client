package dev.performanceclient.renderer.experimental;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Produces a real PNG frame from the compressed face masks without Minecraft dependencies. */
public final class AasrSceneDemo {
    private AasrSceneDemo() {}

    public static AasrSoftwareRenderer.Scene scene(long seed) {
        byte[] material = new byte[16*16*16];
        Random random = new Random(seed);
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int height=(int)Math.round(4.5+2*Math.sin(x*.52)+1.7*Math.cos(z*.41));
            height=Math.clamp(height,1,12);
            for(int y=0;y<=height;y++) {
                int idx=(y*16+z)*16+x;
                material[idx]=(byte)(y==height?2:y>=height-2?3:1);
            }
            if(random.nextInt(5)==0) material[((height+1)*16+z)*16+x]=4;
        }
        return new AasrSoftwareRenderer.Scene() {
            @Override public boolean solid(int x,int y,int z) {return material(x,y,z)!=0;}
            @Override public int material(int x,int y,int z) {
                if(x<0||y<0||z<0||x>=16||y>=16||z>=16)return 0;
                return Byte.toUnsignedInt(material[(y*16+z)*16+x]);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        Path path=Path.of(args.length>0?args[0]:"aasr-demo.png");
        AasrSoftwareRenderer renderer=new AasrSoftwareRenderer(scene(2639950L));
        var img=renderer.render(640,360,new AasrSoftwareRenderer.Vec(26,20,-24),
                new AasrSoftwareRenderer.Vec(8,4,8),60);
        if(path.getParent()!=null) Files.createDirectories(path.getParent());
        if(!ImageIO.write(img,"png",path.toFile())) throw new IllegalStateException("PNG writer missing");
        System.out.println("AASR prototype image: "+path.toAbsolutePath());
        System.out.println("Visible cube faces (all directions): "+renderer.surfaceFaces());
        System.out.println("This is NOT an in-game Minecraft renderer or an FPS optimization.");
    }
}
