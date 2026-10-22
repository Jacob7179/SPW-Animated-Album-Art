package com.spw.animated.album.art;
import java.nio.file.*;
public class ArtworkTimelineTest {
 public static void main(String[] args)throws Exception{
  String source="#tb 0: 1/30\n0, 300, 302, 1, 100, 0xa\n0, 301, 301, 1, 50, 0xb\n";
  String valid="#tb 0: 1/15360\n0, 0, 1024, 512, 100, 0xa\n0, 512, 512, 512, 50, 0xb\n";
  if(!OnlineArtwork.sameTimeline(source,valid))throw new AssertionError("Timebase conversion rejected");
  if(OnlineArtwork.sameTimeline(source,valid.replace("0, 512, 512","0, 256, 256")))throw new AssertionError("Damaged timestamps accepted");
  if(OnlineArtwork.sameTimeline(source,valid.replace("0xb","0xc")))throw new AssertionError("Changed packet accepted");
  if(OnlineArtwork.sameTimeline(source,""))throw new AssertionError();
  if(args.length>=2){boolean same=OnlineArtwork.sameTimeline(Files.readString(Path.of(args[0])),Files.readString(Path.of(args[1])));if(same!=(args.length==2))throw new AssertionError("Unexpected real timeline result");}
  System.out.println("PASS packet timing, frame-order preservation, timebase conversion and damaged-output rejection");
 }
}
