package dev.performanceclient.renderer.experimental;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * TEST REFERENCE RENDERER, not a Minecraft/RenderPearl replacement.
 * Uses six directional AASR occupancy masks to accept the frontmost exposed cube face.
 * Samples a ray through an immutable 16^3 scene and shades by a toy material palette.
 * No textures, lightmaps, transparency, fluids or non-cube models are represented.
 */
public final class AasrSoftwareRenderer {
    private static final double EPS = 1e-8;
    private static final int GRID = 16;
    private final AxisAlgebraSurface masks = new AxisAlgebraSurface();
    private final Scene scene;

    public interface Scene extends AxisAlgebraSurface.SolidSource {
        /** 0 for air, 1..255 for toy opaque materials. */
        int material(int x, int y, int z);
    }

    public record Hit(int x, int y, int z, int face, double distance) {}
    public record Vec(double x, double y, double z) {
        Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        Vec mul(double k) { return new Vec(x * k, y * k, z * k); }
        Vec sub(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
        Vec cross(Vec v) { return new Vec(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x); }
        Vec normal() {
            double n = Math.sqrt(x*x+y*y+z*z);
            if (n < 1e-12) throw new IllegalArgumentException("Zero vector");
            return mul(1/n);
        }
    }

    public AasrSoftwareRenderer(Scene immutableScene) {
        scene = Objects.requireNonNull(immutableScene);
        masks.encode(scene);
    }

    public int surfaceFaces() { return masks.faceCount(); }

    private static boolean in(int x, int y, int z) {
        return x >= 0 && x < GRID && y >= 0 && y < GRID && z >= 0 && z < GRID;
    }
    private boolean exposed(int x,int y,int z,int face) {
        int plane = switch(face) { case 0,1 -> x; case 2,3 -> y; default -> z; };
        int row = switch(face) { case 0,1,4,5 -> y; default -> z; };
        int bit = switch(face) { case 0,1 -> z; default -> x; };
        return (masks.faceMask(face,plane,row) & (1 << bit)) != 0;
    }

    /** Ray-box slab intersection followed by a front-to-back voxel traversal. */
    public Hit ray(Vec eye, Vec dir) {
        return trace(eye, dir, true);
    }

    /** Independent occupancy-path oracle (does not consult face masks). */
    Hit referenceRay(Vec eye, Vec dir) {
        return trace(eye, dir, false);
    }

    private Hit trace(Vec eye, Vec direction, boolean useMasks) {
        Vec d = direction.normal();
        double[] origins = {eye.x,eye.y,eye.z};
        double[] dirs = {d.x,d.y,d.z};
        double lo = 0, hi = Double.POSITIVE_INFINITY;
        int entranceFace = -1;
        for (int a=0; a<3; a++) {
            double p=origins[a], v=dirs[a];
            if (Math.abs(v)<1e-14) {
                if(p<0 || p>=GRID) return null;
                continue;
            }
            double near=(0-p)/v, far=(GRID-p)/v;
            int enter = switch(a) {case 0 -> v>0 ? 1:0; case 1 -> v>0 ? 3:2; default -> v>0 ? 5:4;};
            if(near>far) {double t=near;near=far;far=t;}
            if(near>lo) {lo=near;entranceFace=enter;}
            hi=Math.min(hi,far);
            if(lo>hi) return null;
        }
        if (hi < 0) return null;
        double start=Math.max(0,lo);
        if(start + EPS > hi) return null;
        int x=(int)Math.floor(eye.x+(start+EPS)*d.x);
        int y=(int)Math.floor(eye.y+(start+EPS)*d.y);
        int z=(int)Math.floor(eye.z+(start+EPS)*d.z);
        int sx=d.x>0 ? 1 : (d.x<0 ? -1:0);
        int sy=d.y>0 ? 1 : (d.y<0 ? -1:0);
        int sz=d.z>0 ? 1 : (d.z<0 ? -1:0);
        double tx=sx==0 ? Double.POSITIVE_INFINITY : ((sx>0 ? x+1:x)-eye.x)/d.x;
        double ty=sy==0 ? Double.POSITIVE_INFINITY : ((sy>0 ? y+1:y)-eye.y)/d.y;
        double tz=sz==0 ? Double.POSITIVE_INFINITY : ((sz>0 ? z+1:z)-eye.z)/d.z;
        int face=entranceFace;
        double t=start;
        // At most 48 cell crossings in a 16-cube grid (plus starting cell).
        for(int i=0;i<50 && in(x,y,z) && t<=hi+EPS;i++) {
            if (scene.solid(x,y,z)) {
                // First solid cell only. An interior camera can have face -1.
                if(face<0) return null;
                if(!useMasks || exposed(x,y,z,face)) return new Hit(x,y,z,face,t);
                // A non-exposed boundary face must never be rendered.
                return null;
            }
            if(tx<=ty && tx<=tz) {
                t=tx; x+=sx; face=sx>0 ? 1:0;
                tx=sx==0 ? Double.POSITIVE_INFINITY : ((sx>0 ? x+1:x)-eye.x)/d.x;
            } else if(ty<=tz) {
                t=ty; y+=sy; face=sy>0 ? 3:2;
                ty=sy==0 ? Double.POSITIVE_INFINITY : ((sy>0 ? y+1:y)-eye.y)/d.y;
            } else {
                t=tz; z+=sz; face=sz>0 ? 5:4;
                tz=sz==0 ? Double.POSITIVE_INFINITY : ((sz>0 ? z+1:z)-eye.z)/d.z;
            }
        }
        return null;
    }

    public BufferedImage render(int width,int height, Vec eye,Vec target,double fovDegrees) {
        if(width<1 || height<1 || width>8192 || height>8192) throw new IllegalArgumentException("Invalid size");
        if(fovDegrees<10 || fovDegrees>140) throw new IllegalArgumentException("Invalid FOV");
        Vec f=target.sub(eye).normal();
        Vec r=f.cross(new Vec(0,1,0)).normal();
        Vec u=r.cross(f).normal();
        double extent=Math.tan(Math.toRadians(fovDegrees/2));
        double aspect=width/(double)height;
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int py=0;py<height;py++) {
            double sy=(1-2*(py+0.5)/height)*extent;
            for(int px=0;px<width;px++) {
                double sx=(2*(px+0.5)/width-1)*extent*aspect;
                Vec d=f.add(r.mul(sx)).add(u.mul(sy)).normal();
                Hit hit=ray(eye,d);
                out.setRGB(px,py,hit==null?0xff142334:shade(hit));
            }
        }
        return out;
    }
    private int shade(Hit h) {
        int mat=scene.material(h.x,h.y,h.z);
        int rgb=switch(mat) {
            case 1 -> 0x8192a0;
            case 2 -> 0x4fa36c;
            case 3 -> 0xc4944b;
            case 4 -> 0x5f6e8d;
            default -> 0xc4a070;
        };
        double shade=switch(h.face) {case 2 -> 1.0;case 3 -> .47;case 0,1 -> .75;default -> .62;};
        int rr=(int)(((rgb>>>16)&255)*shade);
        int gg=(int)(((rgb>>>8)&255)*shade);
        int bb=(int)((rgb&255)*shade);
        return 0xff000000|(rr<<16)|(gg<<8)|bb;
    }
}
