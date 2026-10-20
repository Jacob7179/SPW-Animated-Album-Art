package com.spw.animated.album.art;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
public class BundledDecoderPermissionTest {
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("bundled-ffmpeg-");
  Path binary=Files.createDirectories(root.resolve("bin")).resolve("ffmpeg");
  Files.writeString(binary,"fixture");
  boolean posix=FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
  if(posix)Files.setPosixFilePermissions(binary,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
  DecoderExecutable.prepareBundled(binary);
  if(posix&&!Files.isExecutable(binary))throw new AssertionError("Execute permission not restored");
  Path resolved=DecoderExecutable.resolve(root,"Linux","",null,p->true);
  if(!resolved.equals(binary))throw new AssertionError("Bundled Linux binary not preferred");
  if(!Files.readString(binary).equals("fixture"))throw new AssertionError("Binary content changed");
  System.out.println("PASS bundled binary selection/content; POSIX chmod "+(posix?"verified":"requires Linux runner"));
 }
}
