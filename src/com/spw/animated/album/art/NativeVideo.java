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
    private boolean awaitingPresentation;
    private volatile boolean loopPause=true;
    public void setLoopPause(boolean enabled){loopPause=enabled;}
    private volatile Process process;
    public NativeVideo(Path ffmpeg,Path video,Consumer<Frame> output,Consumer<String> log){
        this.ffmpeg=ffmpeg;this.video=video;this.output=output;this.log=log;
    }
    public synchronized void start(){start(false);}
    /** Publish the opening frame, but do not advance until a native painter draws it. */
    public synchronized void prepare(){start(true);}
    public synchronized void presented(){
        if(awaitingPresentation){awaitingPresentation=false;notifyAll();}
    }
    private synchronized void start(boolean waitForPresentation){
        if(running)return;
        awaitingPresentation=waitForPresentation;
        running=true;
        Thread thread=new Thread(this::decode,"AnimatedCover-native-video");thread.setDaemon(true);thread.start();
    }
    private void decode(){
        Process decoder=null;
        long nextFrame=0;
        final long interval=1_000_000_000L/FPS;
        boolean first=true;
        boolean completedLoop=false;
        try {
            while(running){
                decoder=new ProcessBuilder(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error",
                    "-i",video.toString(),"-map","0:v:0","-an","-sn","-dn",
                    "-vf","setpts=PTS-STARTPTS,fps="+FPS+",scale="+SIZE+":"+SIZE+":force_original_aspect_ratio=increase:flags=lanczos,crop="+SIZE+":"+SIZE,
                    "-threads","1","-pix_fmt","bgra","-f","rawvideo","pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                process=decoder;
                if(!running){decoder.destroyForcibly();return;}
                boolean decoded=false;
                boolean firstInLoop=true;
                try(InputStream input=new BufferedInputStream(decoder.getInputStream())){
                    byte[] pixels=new byte[SIZE*SIZE*4];
                    while(running){
                        int count=input.readNBytes(pixels,0,pixels.length);
                        if(count!=pixels.length){
                            if(count!=0||!decoded)throw new EOFException("MP4 decoder stopped without a complete frame");
                            break;
                        }
                        decoded=true;
                        Frame frame=new Frame(pixels);
                        long remaining;
                        while(running && (remaining=nextFrame+(firstInLoop && completedLoop && loopPause?1_000_000_000L:0L)-System.nanoTime())>0)
                            java.util.concurrent.locks.LockSupport.parkNanos(Math.min(remaining,50_000_000L));
                        synchronized(this){
                            if(!running){frame.close();return;}
                            retired.addLast(frame);
                            output.accept(frame);
                            nextFrame=System.nanoTime()+interval;
                            firstInLoop=false;
                            while(retired.size()>FPS*2)retired.removeFirst().close();
                        }
                        if(first){
                            first=false;log.accept("First native video frame ready: "+video);
                            synchronized(this){
                                while(running&&awaitingPresentation)wait();
                                nextFrame=System.nanoTime()+interval;
                            }
                        }
                    }
                }
                decoder.waitFor();
                if(running && decoder.exitValue()!=0)throw new IOException("MP4 decoder exit "+decoder.exitValue());
                decoder=null;
                // Keep the final frame visible. Check the live pause setting while
                // waiting on the next opening frame, so disabling it cancels the hold.
                completedLoop=true;
            }
        }catch(Throwable error){
            if(running){log.accept("Native video failed: "+error);output.accept(null);}
        }finally{
            if(decoder!=null)decoder.destroyForcibly();
        }
    }
    public synchronized void close(){
        running=false;notifyAll();
        Process p=process;if(p!=null)p.destroyForcibly();
        output.accept(null);
        var old=new ArrayList<>(retired);retired.clear();
        CompletableFuture.delayedExecutor(3,TimeUnit.SECONDS).execute(()->old.forEach(Frame::close));
    }
}
