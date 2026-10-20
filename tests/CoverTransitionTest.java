package com.spw.animated.album.art;
import androidx.compose.ui.node.*;
import java.lang.reflect.*;
public class CoverTransitionTest {
 public static void main(String[] args)throws Exception {
  byte[] bytes=new byte[512*512*4];for(int i=0;i<bytes.length;i+=4){bytes[i]=17;bytes[i+1]=33;bytes[i+2]=77;bytes[i+3]=(byte)255;}
  NativeVideo.Frame original=new NativeVideo.Frame(bytes);NativeVideo.Frame copy=original.snapshot();original.close();
  int[] pixels=new int[512*512];copy.getBitmap().readPixels(pixels,0,0,512,512,0,512);
  for(int pixel:pixels)if(pixel!=0xff4d2111)throw new AssertionError("Outgoing frame snapshot changed");
  copy.close();
  System.out.println("PASS independently owned outgoing video snapshot; transition transforms remain native.");System.exit(0);
 }
}

