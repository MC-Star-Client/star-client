package dev.performanceclient.renderer.buffer;
import java.util.*;
/** Region suballocator for packed mesh uploads; CPU bookkeeping only, no GPU residency implied. */
public final class RegionAllocator {
    private final NavigableMap<Long,Long> free = new TreeMap<>();
    private final long capacity;
    public RegionAllocator(long size){if(size<=0)throw new IllegalArgumentException();capacity=size;free.put(0L,size);}
    public synchronized Slice allocate(long bytes,long alignment){
        if(bytes<=0||alignment<=0||(alignment&(alignment-1))!=0)throw new IllegalArgumentException();
        for(var e:new ArrayList<>(free.entrySet())){
            long base=e.getKey(),length=e.getValue(),aligned=(base+alignment-1)&-alignment;
            if(aligned<base || aligned-base>length || length-(aligned-base)<bytes)continue;
            free.remove(base);
            if(aligned>base)free.put(base,aligned-base);
            long end=aligned+bytes,remain=base+length-end;
            if(remain>0)free.put(end,remain);
            return new Slice(aligned,bytes);
        }
        return null;
    }
    public synchronized void release(Slice slice){
        if(slice.offset<0||slice.length<=0||slice.offset>capacity-slice.length)throw new IllegalArgumentException();
        long start=slice.offset,end=start+slice.length;
        var lo=free.floorEntry(start);
        if(lo!=null&&lo.getKey()+lo.getValue()>start)throw new IllegalStateException("Overlap/double free");
        var hi=free.ceilingEntry(start);
        if(hi!=null&&hi.getKey()<end)throw new IllegalStateException("Overlap/double free");
        if(lo!=null&&lo.getKey()+lo.getValue()==start){start=lo.getKey();free.remove(lo.getKey());}
        if(hi!=null&&hi.getKey()==end){end=hi.getKey()+hi.getValue();free.remove(hi.getKey());}
        free.put(start,end-start);
    }
    public record Slice(long offset,long length) {}
}
