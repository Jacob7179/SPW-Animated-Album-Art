package com.spw.animated.album.art;
import java.nio.file.*;
public class ArtworkTimestampTest {
 public static void main(String[] args)throws Exception{
  var command=OnlineArtwork.normalizeCommand(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
  if(!command.contains("copy")||command.contains("-ss")||!command.contains("-copyts")||!command.contains("-start_at_zero")||!command.contains("-copytb")||command.contains("-xerror")||!command.contains("warning"))throw new AssertionError();
  Process process=new ProcessBuilder(command).inheritIO().start();
  if(process.waitFor()!=0)throw new AssertionError("Timestamp normalization failed");
  System.out.println("PASS no-seek, no-reencode timestamp normalization");
 }
}
