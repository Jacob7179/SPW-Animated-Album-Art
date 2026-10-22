import com.spw.animated.album.art.NativeVideo;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class FirstPresentationTest {
 public static void main(String[] args)throws Exception {
  AtomicInteger count=new AtomicInteger();AtomicReference<NativeVideo.Frame> initial=new AtomicReference<>();
  CountDownLatch opening=new CountDownLatch(1),playing=new CountDownLatch(4);
  NativeVideo decoder=new NativeVideo(Path.of(args[0]),Path.of(args[1]),frame->{
   if(frame==null)return;if(count.incrementAndGet()==1){initial.set(frame);opening.countDown();}else playing.countDown();
  },System.out::println);
  try{
   decoder.prepare();if(!opening.await(10,TimeUnit.SECONDS))throw new AssertionError("No opening frame");
   Thread.sleep(1500);if(count.get()!=1)throw new AssertionError("Opening advanced before visible: "+count);
   if(initial.get()==null)throw new AssertionError();
   decoder.presented();decoder.presented();
   if(!playing.await(3,TimeUnit.SECONDS))throw new AssertionError("Playback did not resume");
  }finally{decoder.close();}
  CountDownLatch ready=new CountDownLatch(1);
  NativeVideo hidden=new NativeVideo(Path.of(args[0]),Path.of(args[1]),frame->{if(frame!=null)ready.countDown();},System.out::println);
  hidden.prepare();if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError();
  hidden.close();
  System.out.println("PASS first frame stays held until drawn, playback resumes, repeated presentation is safe and hidden close does not block");
  System.exit(0);
 }
}
