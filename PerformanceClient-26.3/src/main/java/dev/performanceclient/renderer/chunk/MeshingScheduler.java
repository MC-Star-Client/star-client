package dev.performanceclient.renderer.chunk;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Bounded worker queue; never touches live Minecraft world on a worker thread. */
public final class MeshingScheduler implements AutoCloseable {
    private final ThreadPoolExecutor pool;
    private final PackedMesher mesher=new PackedMesher();
    public MeshingScheduler(int threads) {
        int n=Math.clamp(threads,1,16);
        AtomicInteger id=new AtomicInteger();
        pool=new ThreadPoolExecutor(n,n,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{
            Thread t=new Thread(r,"pc-mesh-"+id.incrementAndGet());t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public Future<PackedMesher.Result> submit(ChunkSnapshot immutableSnapshot) {
        return pool.submit(()->mesher.mesh(immutableSnapshot));
    }
    public int queued(){return pool.getQueue().size();}
    @Override public void close(){pool.shutdownNow();}
}
