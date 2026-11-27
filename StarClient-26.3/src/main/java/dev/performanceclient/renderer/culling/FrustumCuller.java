package dev.performanceclient.renderer.culling;
/** Plain frustum-vs-AABB for pre-extracted normalized plane equations. */
public final class FrustumCuller {
    private final float[] planes=new float[24];
    public void setPlanes(float[] p){
        if(p.length!=24)throw new IllegalArgumentException("6 planes * 4 coefficients");
        System.arraycopy(p,0,planes,0,24);
    }
    public boolean visible(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
        for(int i=0;i<24;i+=4){
            float a=planes[i],b=planes[i+1],c=planes[i+2];
            float px=a>=0?maxX:minX,py=b>=0?maxY:minY,pz=c>=0?maxZ:minZ;
            if(a*px+b*py+c*pz+planes[i+3]<0)return false;
        }
        return true;
    }
}
