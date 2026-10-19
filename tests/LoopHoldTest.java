import com.spw.animated.album.art.NativeVideo;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class LoopHoldTest {
 public static void main(String[] args)throws Exception {
  CountDownLatch done=new CountDownLatch(65);AtomicInteger count=new AtomicInteger(),nulls=new AtomicInteger();AtomicLong previous=new AtomicLong();long[] boundary=new long[2];AtomicReference<Throwable> failure=new AtomicReference<>();
  NativeVideo decoder=new NativeVideo(Paths.get(args[0]),Paths.get(args[1]),frame->{
   if(frame==null){nulls.incrementAndGet();return;}
   int n=count.incrementAndGet();long now=System.nanoTime(),last=previous.getAndSet(now);
   if(last!=0){long gap=now-last;if(n==31||n==61)boundary[n==31?0:1]=gap;else if(gap<25000000L)failure.set(new AssertionError("Rushed frame: "+gap));}
   done.countDown();
  },System.out::println);
  decoder.start();if(!done.await(15,TimeUnit.SECONDS))throw new AssertionError("No complete loops");
  if(nulls.get()!=0)throw new AssertionError("Video cleared at loop boundary");
  decoder.close();int stopped=count.get();Thread.sleep(150);if(count.get()!=stopped)throw new AssertionError("Frames after close");
  if(failure.get()!=null)throw new AssertionError(failure.get());
  for(long gap:boundary)if(gap<1000000000L||gap>1500000000L)throw new AssertionError("Unexpected hold: "+gap);
  System.out.println("PASS loop holds ms="+boundary[0]/1000000.0+", "+boundary[1]/1000000.0+"; no still/blank fallback, no frame bursts, stop passed.");System.exit(0);
 }
}
