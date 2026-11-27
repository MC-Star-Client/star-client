package dev.performanceclient.pvp;
/** Editable 16x16 RGBA grid (0 = transparent) with mirroring, disk persistence delegated to UI. */
public final class PixelCrosshair {
    public static final int SIDE=16;
    private final int[] rgba=new int[SIDE*SIDE];
    public void set(int x,int y,int color){if(x<0||x>=SIDE||y<0||y>=SIDE)throw new IndexOutOfBoundsException();rgba[y*SIDE+x]=color;}
    public int get(int x,int y){return rgba[y*SIDE+x];}
    public void drawSymmetric(int x,int y,int color){set(x,y,color);set(SIDE-1-x,y,color);set(x,SIDE-1-y,color);set(SIDE-1-x,SIDE-1-y,color);}
    public int[] copyPixels(){return rgba.clone();}
}
