import com.spw.animated.album.art.VideoHandoff;
import java.nio.file.*;import java.util.*;
public class VideoHandoffTest {
 static final class Decoder {final Path path;int closes;Decoder(Path path){this.path=path;}}
 static void check(boolean result,String label){if(!result)throw new AssertionError(label);}
 public static void main(String[] args){
  Path a=Paths.get("a.mp4"),b=Paths.get("b.mp4"),c=Paths.get("c.mp4");
  List<Decoder> created=new ArrayList<>();
  VideoHandoff<Decoder> handoff=new VideoHandoff<>(path->{Decoder decoder=new Decoder(path);created.add(decoder);return decoder;},decoder->{if(++decoder.closes!=1)throw new AssertionError("Double release");});
  Decoder first=handoff.request(a);check(!handoff.isCurrent(a),"not ready");check(handoff.promote(first),"initial ready");
  Decoder second=handoff.request(b);check(handoff.isCurrent(a)&&first.closes==0,"outgoing stays playing while next loads");
  Decoder third=handoff.request(c);check(second.closes==1&&first.closes==0,"rapid switch releases only pending");
  check(!handoff.promote(second),"stale first-frame callback ignored");
  handoff.cancel(second);check(third.closes==0,"stale cancellation preserves newest pending");
  check(handoff.promote(third)&&handoff.isCurrent(c)&&first.closes==1,"replace only when ready");
  check(handoff.request(c)==null&&third.closes==0,"same source stays running");
  Decoder pending=handoff.request(b);handoff.cancel(pending);check(handoff.isCurrent(c)&&third.closes==0,"cancel preserves outgoing");
  check(handoff.request(null)==null&&handoff.isCurrent(c),"no-video exit keeps outgoing until transition ends");
  handoff.clear();check(third.closes==1&&handoff.isCurrent(null),"no-video handoff completes");
  Decoder finalPending=handoff.request(a);handoff.close();handoff.close();check(finalPending.closes==1,"dispose pending exactly once");
  check(handoff.request(b)==null,"no decoder starts after close");
  for(Decoder decoder:created)check(decoder.closes==1,"no leaked decoder");
  System.out.println("PASS: delayed readiness, rapid song changes, stale callbacks, no-video transition and exact decoder cleanup.");
 }
}
