package com.spw.animated.album.art;

import androidx.compose.ui.graphics.ImageBitmap;
import com.xuncorp.voxzen.image.RefCountedImageBitmap;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.ImageInfo;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Decode MP4 into local native-canvas frames. Never publish album-art changes. */
public final class NativeVideo implements AutoCloseable {
    public static final int SIZE=512, FPS=30;
    public static final class Frame implements AutoCloseable {
        private final RefCountedImageBitmap owner;
        private final RefCountedImageBitmap.Reference reference;
        Frame(byte[] pixels) throws IOException {
            Bitmap bitmap=new Bitmap();
            if(!bitmap.installPixels(ImageInfo.Companion.makeN32Premul(SIZE,SIZE),pixels,SIZE*4)) {
                bitmap.close();throw new IOException("Cannot allocate native video frame");
            }
            bitmap.setImmutable();
            owner=new RefCountedImageBitmap(bitmap);
            reference=owner.acquire();
        }
        public ImageBitmap getBitmap(){return reference.getBitmap();}
        public void close(){reference.close();owner.close();}
    }
    private final Path ffmpeg,video;
    private final Consumer<Frame> output;
    private final Consumer<String> log;
    private final Deque<Frame> retired=new ArrayDeque<>();
    private volatile boolean running;
    private volatile Process process;
    public NativeVideo(Path ffmpeg,Path video,Consumer<Frame> output,Consumer<String> log){
        this.ffmpeg=ffmpeg;this.video=video;this.output=output;this.log=log;
    }
    public synchronized void start(){
        if(running)return;
        running=true;
        Thread thread=new Thread(this::decode,"AnimatedCover-native-video");thread.setDaemon(true);thread.start();
    }
    private void decode(){
        Process decoder=null;
        byte[] previousTail=null;
        try {
          while(running) {
            decoder=new ProcessBuilder(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error",
                "-re","-i",video.toString(),"-map","0:v:0","-an","-sn","-dn",
                "-vf","fps="+FPS+",scale="+SIZE+":"+SIZE+":force_original_aspect_ratio=increase:flags=lanczos,crop="+SIZE+":"+SIZE,
                "-threads","1","-pix_fmt","bgra","-f","rawvideo","pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process=decoder;
            if(!running){decoder.destroyForcibly();return;}
            try(InputStream input=new BufferedInputStream(decoder.getInputStream())){
                byte[] pixels=new byte[SIZE*SIZE*4];boolean first=true;int loopFrame=0;boolean decoded=false;
                while(running){
                    int count=input.readNBytes(pixels,0,pixels.length);
                    if(count!=pixels.length){
                        if(count!=0||!decoded)throw new EOFException("MP4 decoder stopped without a complete frame");
                        previousTail=pixels.clone();break;
                    }
                    decoded=true;
                    byte[] displayed=pixels;
                    if(previousTail!=null && loopFrame<12){
                        displayed=blendBoundary(previousTail,pixels,loopFrame+1,12);
                    }
                    loopFrame++;
                    Frame frame=new Frame(displayed);
                    synchronized(this){
                        if(!running){frame.close();return;}
                        retired.addLast(frame);
                        output.accept(frame);
                        while(retired.size()>FPS*2)retired.removeFirst().close();
                    }
                    if(first){first=false;log.accept("First native video frame ready: "+video);}
                }
            }
            decoder.waitFor();
            if(running&&decoder.exitValue()!=0)throw new IOException("MP4 decoder exit "+decoder.exitValue());
            decoder=null;
          }
        }catch(Throwable error){
            if(running){log.accept("Native video failed: "+error);output.accept(null);}
        }finally{
            if(decoder!=null)decoder.destroyForcibly();
        }
    }
    // Dissolve the held ending frame into the opening 400 ms. This softens
    // mismatched source endpoints without modifying the user's MP4.
    static byte[] blendBoundary(byte[] tail,byte[] head,int step,int steps){
        byte[] result=new byte[head.length];
        for(int i=0;i<head.length;i++){
            result[i]=(byte)(((tail[i]&255)*(steps-step)+(head[i]&255)*step+steps/2)/steps);
        }
        return result;
    }
    public synchronized void close(){
        running=false;
        Process p=process;if(p!=null)p.destroyForcibly();
        output.accept(null);
        var old=new ArrayList<>(retired);retired.clear();
        CompletableFuture.delayedExecutor(3,TimeUnit.SECONDS).execute(()->old.forEach(Frame::close));
    }
}
