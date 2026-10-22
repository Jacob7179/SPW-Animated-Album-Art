package com.spw.animated.album.art;
import java.nio.file.*;
public class FragmentedMp4Test {
 public static void main(String[] args)throws Exception{
  if(!FragmentedMp4.rebase(Path.of(args[0]),Path.of(args[1])))throw new AssertionError("Supported Apple fMP4 not rebased");
  Path second=Path.of(args[1]+".again.mp4");
  if(!FragmentedMp4.rebase(Path.of(args[1]),second)||Files.mismatch(second,Path.of(args[1]))!=-1)throw new AssertionError("Rebase not idempotent");
  Files.delete(second);
  Path invalid=Path.of(args[1]+".invalid");Files.write(invalid,new byte[64]);
  if(FragmentedMp4.rebase(invalid,second))throw new AssertionError("Invalid MP4 accepted");Files.delete(invalid);
  System.out.println("PASS Apple fMP4 rebasing, repeated normalization and malformed-input rejection");
 }
}
