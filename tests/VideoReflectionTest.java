package com.spw.animated.album.art;
import androidx.compose.ui.graphics.SkiaBackedCanvas;
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope;
import org.jetbrains.skia.*;
public class VideoReflectionTest {
 static void render(Bitmap target,NativeVideo.Frame frame)throws Exception {
  target.erase(0);
  try(org.jetbrains.skia.Canvas canvas=new org.jetbrains.skia.Canvas(target,new SurfaceProps())){
   CanvasDrawScope scope=new CanvasDrawScope();
   scope.getDrawParams().setCanvas(new SkiaBackedCanvas(canvas));
   long size=((long)Float.floatToRawIntBits(100f)<<32)|(Float.floatToRawIntBits(160f)&0xffffffffL);
   for(var method:scope.getDrawParams().getClass().getMethods())if(method.getName().startsWith("setSize-"))method.invoke(scope.getDrawParams(),size);
   MainPluginKt.drawVideoReflection(scope,frame.getBitmap(),100f,100f,8f,2f);
  }
 }
 public static void main(String[] args)throws Exception {
  byte[] pixels=new byte[512*512*4];
  for(int y=0;y<512;y++)for(int x=0;x<512;x++) {int i=(y*512+x)*4;pixels[i]=(byte)(y>=256?255:0);pixels[i+2]=(byte)(y<256?255:0);pixels[i+3]=(byte)255;}
  try(Bitmap target=new Bitmap()){
   target.allocPixels(ImageInfo.Companion.makeN32Premul(100,160));
   NativeVideo.Frame first=new NativeVideo.Frame(pixels);render(target,first);first.close();
   int near=target.getColor(50,105),far=target.getColor(50,144);
   if((near&255)<200 || ((near>>>16)&255)>10)throw new AssertionError("Not mirrored from video bottom");
   if((near>>>24)<40 || (far>>>24)>=5)throw new AssertionError("Reflection did not fade");
   if(target.getColor(50,50)!=0 || target.getColor(50,150)!=0)throw new AssertionError("Reflection escaped its bounds");
   for(int i=0;i<pixels.length;i+=4){pixels[i]=0;pixels[i+2]=(byte)255;}
   NativeVideo.Frame next=new NativeVideo.Frame(pixels);render(target,next);next.close();
   if(((target.getColor(50,105)>>>16)&255)<200)throw new AssertionError("Reflection did not update with video frame");
  }
  System.out.println("PASS live-frame mirroring, fade opacity, bounds, and frame update.");System.exit(0);
 }
}
