package dev.performanceclient.features;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.io.IOException;
/** PNG editor data for a 64x64 texture. Does not upload textures into player skin system. */
public final class PixelPlayer {
    private final BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
    public void pixel(int x,int y,int color){image.setRGB(x,y,color);}
    public void exportPng(Path target)throws IOException{if(!ImageIO.write(image,"png",target.toFile()))throw new IOException("No PNG writer");}
}
