package com.spw.animated.album.art;

import androidx.compose.ui.graphics.painter.Painter;
import com.xuncorp.voxzen.service.PlaybackMonitor;
import com.xuncorp.voxzen.tag.TagParser;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.jetbrains.skia.*;

/** Read-only recovery for an image created before the plugin's decoder hooks existed. */
public final class NativeCoverBootstrap implements AutoCloseable {
    private final CoverOrigins origins;
    private final Supplier<String> currentSong;
    private final Consumer<String> log;
    private final ArrayList<WeakReference<Object>> attempted=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AnimatedCover-existing-cover");t.setDaemon(true);return t;});
    private volatile boolean closed;
    public NativeCoverBootstrap(CoverOrigins origins,Supplier<String> currentSong,Consumer<String> log){
        this.origins=origins;this.currentSong=currentSong;this.log=log;
    }
    public synchronized void offer(Object painter,String song){
        if(closed||song==null||!CoverOrigins.sameSong(song,currentSong.get()))return;
        Object token=HostCoverBridge.painterSource(painter);
        if(!(token instanceof Shader shader)||origins.song(token)!=null)return;
        for(Iterator<WeakReference<Object>> it=attempted.iterator();it.hasNext();){
            Object previous=it.next().get();if(previous==null)it.remove();else if(previous==token)return;
        }
        Paint retained=null;
        try{
            long size=(Long)Painter.class.getMethod("getIntrinsicSize-NH-jbRc").invoke(painter);
            int width=Math.round(Float.intBitsToFloat((int)(size>>>32)));
            int height=Math.round(Float.intBitsToFloat((int)size));
            if(width<=0||height<=0||width>8192||height>8192)return;
            // Paint takes its own native reference; disposing a transition painter
            // cannot close the shader being sampled by the background worker.
            retained=new Paint();retained.setShader(shader);
            Job job=new Job(token,song,generation(),width,height,retained);retained=null;
            attempted.add(new WeakReference<>(token));worker.execute(job);
        }catch(ReflectiveOperationException | RuntimeException ignored){}
        finally{if(retained!=null)retained.close();}
    }
    private static int generation(){
        return PlaybackMonitor.INSTANCE.getArtworkState$Voxzen_composeApp().getValue().getGeneration();
    }
    private final class Job implements Runnable {
        final Object token;final String song;final int revision,width,height;final Paint retained;
        Job(Object token,String song,int revision,int width,int height,Paint retained){
            this.token=token;this.song=song;this.revision=revision;this.width=width;this.height=height;this.retained=retained;
        }
        boolean valid(){return !closed&&!Thread.currentThread().isInterrupted()&&generation()==revision&&CoverOrigins.sameSong(song,currentSong.get());}
        public void run(){
            try(retained){
                if(!valid())return;
                int[] existing=sample(retained,width,height);
                if(Arrays.stream(existing).distinct().limit(16).count()<16)return; // Ignore solid placeholders.
                Method read=TagParser.class.getMethod("readImage-0E7RQCE",String.class,int.class,int.class);
                Object image=read.invoke(TagParser.INSTANCE,song,width,height);
                Object decoded=HostCoverBridge.imageSource(image);
                if(!(decoded instanceof Bitmap bitmap)||!valid())return;
                try(Shader expected=shader(bitmap);Paint expectedPaint=new Paint()){
                    expectedPaint.setShader(expected);
                    if(!Arrays.equals(existing,sample(expectedPaint,bitmap.getWidth(),bitmap.getHeight())))return;
                }
                synchronized(NativeCoverBootstrap.this){
                    if(!valid())return;
                    origins.record(token,song);
                }
                log.accept("Existing native cover verified without reload: "+song);
            }catch(ReflectiveOperationException | RuntimeException ignored){ /* Keep native pixels if unverified. */ }
        }
    }
    static Shader shader(Bitmap bitmap)throws ReflectiveOperationException{
        return (Shader)Bitmap.class.getMethod("makeShader-lLMeGNU",FilterTileMode.class,FilterTileMode.class,SamplingMode.class,float[].class)
            .invoke(bitmap,FilterTileMode.CLAMP,FilterTileMode.CLAMP,new FilterMipmap(FilterMode.LINEAR,MipmapMode.LINEAR),null);
    }
    static int[] sample(Paint paint,int width,int height){
        try(Bitmap output=new Bitmap()){
            output.allocPixels(ImageInfo.Companion.makeN32Premul(64,64));output.erase(0);
            try(Canvas canvas=new Canvas(output,new SurfaceProps())){
                canvas.scale(64f/width,64f/height);canvas.drawRect(0,0,width,height,paint);
            }
            int[] pixels=new int[64*64];
            androidx.compose.ui.graphics.SkiaImageAsset_skikoKt.asComposeImageBitmap(output).readPixels(pixels,0,0,64,64,0,64);
            return pixels;
        }
    }
    public synchronized void close(){
        closed=true;
        for(Runnable task:worker.shutdownNow())if(task instanceof NativeCoverBootstrap.Job job)job.retained.close();
        attempted.clear();
    }
}
