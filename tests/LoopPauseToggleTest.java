import com.spw.animated.album.art.NativeVideo;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class LoopPauseToggleTest {
 public static void main(String[] args)throws Exception {
  CountDownLatch done=new CountDownLatch(125);AtomicInteger count=new AtomicInteger();AtomicLong previous=new AtomicLong();AtomicReference<NativeVideo> ref=new AtomicReference<>();long[] gaps=new long[4];
  NativeVideo decoder=new NativeVideo(Paths.get(args[0]),Paths.get(args[1]),frame->{
   if(frame==null)return;
   int n=count.incrementAndGet();long now=System.nanoTime(),last=previous.getAndSet(now);
   if(n==31||n==61||n==91||n==121)gaps[(n-31)/30]=now-last;
   if(n==31)ref.get().setLoopPause(false);
   if(n==61)ref.get().setLoopPause(true);
   if(n==120){Thread cancel=new Thread(()->{try{Thread.sleep(250);}catch(InterruptedException ignored){}ref.get().setLoopPause(false);});cancel.setDaemon(true);cancel.start();}
   done.countDown();
  },System.out::println);ref.set(decoder);
  decoder.start();if(!done.await(20,TimeUnit.SECONDS))throw new AssertionError("Missing frames");decoder.close();
  if(gaps[0]<1000000000L||gaps[2]<1000000000L)throw new AssertionError("Enabled pause missing");
  if(gaps[1]>800000000L)throw new AssertionError("Disabled pause still active");
  if(gaps[3]<200000000L||gaps[3]>800000000L)throw new AssertionError("Active hold did not cancel");
  System.out.println("PASS pause on/off/on and cancellation, boundary gaps ms="+java.util.Arrays.toString(java.util.Arrays.stream(gaps).mapToDouble(x->x/1000000.0).toArray()));System.exit(0);
 }
}
