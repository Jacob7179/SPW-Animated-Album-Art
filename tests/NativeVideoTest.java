import com.spw.animated.album.art.NativeVideo;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class NativeVideoTest {
 public static void main(String[] args)throws Exception {
  AtomicInteger count=new AtomicInteger();AtomicLong hash=new AtomicLong();CountDownLatch got=new CountDownLatch(45);
  NativeVideo decoder=new NativeVideo(Paths.get(args[0]),Paths.get(args[1]),frame->{
   if(frame==null)return;
   int n=count.incrementAndGet();if(n==1){int[] pixels=new int[512*512];frame.getBitmap().readPixels(pixels,0,0,512,512,0,512);long h=0;for(int p:pixels)h=(h*31+(p&0xffffffffL));hash.set(h);}
   got.countDown();
  },System.out::println);
  decoder.start();if(!got.await(12,TimeUnit.SECONDS))throw new AssertionError("No loop frames: "+count.get());decoder.close();int stopped=count.get();Thread.sleep(300);if(count.get()!=stopped)throw new AssertionError("Frames after stop");
  System.out.println("PASS native-canvas decoder: frames="+count.get()+" firstFrameHash="+hash.get()+"; looping and stop passed");System.exit(0);
 }
}
