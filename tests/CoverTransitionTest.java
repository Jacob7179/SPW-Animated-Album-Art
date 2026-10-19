package com.spw.animated.album.art;
import androidx.compose.ui.node.*;
import java.lang.reflect.*;
public class CoverTransitionTest {
 public static void main(String[] args)throws Exception {
  NodeCoordinator root=new LayoutNode().getInnerCoordinator$ui();
  NodeCoordinator parent=new LayoutNode().getInnerCoordinator$ui();
  NodeCoordinator image=new LayoutNode().getInnerCoordinator$ui();
  image.setWrappedBy$ui(parent);parent.setWrappedBy$ui(root);
  Field alpha=NodeCoordinator.class.getDeclaredField("lastLayerAlpha");alpha.setAccessible(true);
  Field layer=NodeCoordinator.class.getDeclaredField("layer");layer.setAccessible(true);
  OwnedLayer fake=(OwnedLayer)Proxy.newProxyInstance(OwnedLayer.class.getClassLoader(),new Class[]{OwnedLayer.class},(obj,method,values)->null);
  layer.set(image,fake);layer.set(parent,fake);layer.set(root,fake);
  alpha.setFloat(image,.5f);alpha.setFloat(parent,.4f);alpha.setFloat(root,.1f);
  if(Math.abs(HostCoverBridge.transitionAlpha(image,root)-.2f)>.0001)throw new AssertionError("Transition opacity chain");
  byte[] bytes=new byte[512*512*4];for(int i=0;i<bytes.length;i+=4){bytes[i]=17;bytes[i+1]=33;bytes[i+2]=77;bytes[i+3]=(byte)255;}
  NativeVideo.Frame original=new NativeVideo.Frame(bytes);NativeVideo.Frame copy=original.snapshot();original.close();
  int[] pixels=new int[512*512];copy.getBitmap().readPixels(pixels,0,0,512,512,0,512);
  for(int pixel:pixels)if(pixel!=0xff4d2111)throw new AssertionError("Outgoing frame snapshot changed");
  copy.close();
  System.out.println("PASS transition opacity inheritance and independently owned outgoing video snapshot.");System.exit(0);
 }
}
