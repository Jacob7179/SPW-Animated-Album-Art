package com.spw.animated.album.art;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.*;
public class HostLocationTest {
 static class Loader extends ClassLoader {
  URL resource;
  Class<?> anchor(byte[] bytes){return defineClass("fixture.NoLocation",bytes,0,bytes.length);}
  public URL getResource(String name){return name.equals("fixture/NoLocation.class")?resource:super.getResource(name);}
 }
 public static void main(String[] args)throws Exception {
  byte[] bytes=ClassFile.of().build(ClassDesc.of("fixture.NoLocation"),builder->{});
  Path root=Files.createTempDirectory("host space-");Path archive=root.resolve("host archive.dll");
  try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))){zip.putNextEntry(new ZipEntry("fixture/NoLocation.class"));zip.write(bytes);zip.closeEntry();}
  Loader loader=new Loader();Class<?> anchor=loader.anchor(bytes);
  if(anchor.getProtectionDomain().getCodeSource()!=null&&anchor.getProtectionDomain().getCodeSource().getLocation()!=null)throw new AssertionError();
  loader.resource=URI.create("jar:"+archive.toUri()+"!/fixture/NoLocation.class").toURL();
  if(!HostCoverTarget.hostArchive(anchor).equals(archive))throw new AssertionError("Resource fallback");
  loader.resource=null;String previous=System.getProperty("java.class.path");
  try{
   System.setProperty("java.class.path",archive.toString());
   if(!HostCoverTarget.hostArchive(anchor).equals(archive))throw new AssertionError("Classpath fallback");
   System.setProperty("java.class.path",root.resolve("absent.jar").toString());
   try{HostCoverTarget.hostArchive(anchor);throw new AssertionError("Missing source accepted");}catch(java.io.IOException expected){}
  }finally{System.setProperty("java.class.path",previous);}
  if(args.length>0){Class<?> actual=Class.forName("com.xuncorp.voxzen.tag.TagParser");System.out.println("Actual host: "+HostCoverTarget.hostArchive(actual));HostCoverTarget.resolve(actual.getClassLoader());}
  System.out.println("PASS null CodeSource location, jar resource, DLL classpath, spaces, missing-host diagnostic and actual host resolution");
 }
}
