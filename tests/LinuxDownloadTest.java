package com.spw.animated.album.art;
import java.io.*;
import java.nio.file.*;
public class LinuxDownloadTest {
 public static void main(String[] args)throws Exception{
  byte[] bytes=new byte[8192];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)i;
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  if(OnlineArtwork.copyMp4(new ByteArrayInputStream(bytes),out,bytes.length,()->false)!=bytes.length||!java.util.Arrays.equals(bytes,out.toByteArray()))throw new AssertionError();
  for(int size:new int[]{0,32,8193}){
   try{OnlineArtwork.copyMp4(new ByteArrayInputStream(size>32?bytes:new byte[size]),new ByteArrayOutputStream(),size,()->false);throw new AssertionError("Incomplete accepted");}
   catch(IOException expected){}
  }
  try{OnlineArtwork.copyMp4(new ByteArrayInputStream(bytes),out,-1,()->true);throw new AssertionError("Cancellation ignored");}catch(InterruptedException expected){}
  Path root=Files.createTempDirectory("portable-log-");Path log=root.resolve("nested/animated-cover.log");
  PluginLog.append(log,"search started\n");PluginLog.append(log,"download failed\n");
  if(!Files.readString(log).equals("search started\ndownload failed\n"))throw new AssertionError("Log append failed");
  if(!PluginLog.userPath().isAbsolute())throw new AssertionError();
  System.out.println("PASS complete Java downloads, truncated/empty rejection, cancellation and persistent log append");
 }
}
