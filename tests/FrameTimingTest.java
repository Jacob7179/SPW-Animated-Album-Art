import com.spw.animated.album.art.NativeVideo;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class FrameTimingTest {
 public static void main(String[] args)throws Exception {
  CountDownLatch done=new CountDownLatch(95);AtomicLong previous=new AtomicLong();AtomicLong minimum=new AtomicLong(Long.MAX_VALUE);AtomicInteger bursts=new AtomicInteger();
  NativeVideo decoder=new NativeVideo(Paths.get(args[0]),Paths.get(args[1]),frame->{if(frame==null)return;long now=System.nanoTime(),last=previous.getAndSet(now);if(last!=0){long gap=now-last;minimum.accumulateAndGet(gap,Math::min);if(gap<20000000)bursts.incrementAndGet();}done.countDown();},System.out::println);
  decoder.start();if(!done.await(15,TimeUnit.SECONDS))throw new AssertionError("missing frames");decoder.close();
  System.out.println("Minimum interval ms="+minimum.get()/1000000.0+"; frames under 20 ms="+bursts.get());
  if(args.length>2 && bursts.get()!=0)throw new AssertionError("frames rushed");System.exit(0);
 }
}
