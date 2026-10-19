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
        public Frame snapshot() throws IOException {
            int[] colors=new int[SIZE*SIZE];
            getBitmap().readPixels(colors,0,0,SIZE,SIZE,0,SIZE);
            byte[] pixels=new byte[SIZE*SIZE*4];
            java.nio.ByteBuffer.wrap(pixels).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer().put(colors);
            return new Frame(pixels);
        }
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
        try {
            decoder=new ProcessBuilder(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error",
                "-stream_loop","-1","-i",video.toString(),"-map","0:v:0","-an","-sn","-dn",
                "-vf","fps="+FPS+",scale="+SIZE+":"+SIZE+":force_original_aspect_ratio=increase:flags=lanczos,crop="+SIZE+":"+SIZE,
                "-threads","1","-pix_fmt","bgra","-f","rawvideo","pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process=decoder;
            if(!running){decoder.destroyForcibly();return;}
            try(InputStream input=new BufferedInputStream(decoder.getInputStream())){
                byte[] pixels=new byte[SIZE*SIZE*4];boolean first=true;
                long nextFrame=0;
                final long interval=1_000_000_000L/FPS;
                while(running){
                    if(input.readNBytes(pixels,0,pixels.length)!=pixels.length)
                        throw new EOFException("MP4 decoder stopped");
                    Frame frame=new Frame(pixels);
                    // Raw decoder output can arrive in bursts. Pace presentation rather
                    // than input packets, and never rush frames to catch up after a stall.
                    long remaining;
                    while(running && (remaining=nextFrame-System.nanoTime())>0)
                        java.util.concurrent.locks.LockSupport.parkNanos(remaining);
                    synchronized(this){
                        if(!running){frame.close();return;}
                        retired.addLast(frame);
                        output.accept(frame);
                        nextFrame=System.nanoTime()+interval;
                        while(retired.size()>FPS*2)retired.removeFirst().close();
                    }
                    if(first){first=false;log.accept("First native video frame ready: "+video);}
                }
            }
        }catch(Throwable error){
            if(running){log.accept("Native video failed: "+error);output.accept(null);}
        }finally{
            if(decoder!=null)decoder.destroyForcibly();
        }
    }
    public synchronized void close(){
        running=false;
        Process p=process;if(p!=null)p.destroyForcibly();
        output.accept(null);
        var old=new ArrayList<>(retired);retired.clear();
        CompletableFuture.delayedExecutor(3,TimeUnit.SECONDS).execute(()->old.forEach(Frame::close));
    }
}
