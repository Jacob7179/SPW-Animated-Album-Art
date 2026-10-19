package com.spw.animated.album.art;
import java.util.*;
import java.util.concurrent.*;

public final class CoverOriginsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    // Deliberately equal objects must still retain different native resource identities.
    static final class Resource {public boolean equals(Object other){return other instanceof Resource;}public int hashCode(){return 1;}}
    public static void main(String[] args)throws Exception{
        CoverOrigins owners=new CoverOrigins();
        Object bitmapA=new Resource(),bitmapB=new Resource(),bitmapC=new Resource();
        Object shaderA=new Resource(),shaderB=new Resource(),shaderC=new Resource();
        owners.record(bitmapA,"A.m4a");owners.propagate(bitmapA,shaderA);
        // Playback can already be on C while B finishes decoding and A still exits.
        owners.record(bitmapC,"C.m4a");owners.propagate(bitmapC,shaderC);
        owners.record(bitmapB,"B.m4a");owners.propagate(bitmapB,shaderB);
        for(int repeat=0;repeat<1000;repeat++){
            for(Object image:new Object[]{shaderC,shaderA,shaderB,shaderA,shaderC}){
                String actual=owners.song(image);
                check(actual.equals(image==shaderA?"A.m4a":image==shaderB?"B.m4a":"C.m4a"),"rapid-switch owner changed");
            }
        }
        check(owners.song(new Resource())==null,"unknown image inherited playback");
        owners.propagate(new Object(),shaderA);
        check(owners.song(shaderA).equals("A.m4a"),"unknown source replaced outgoing shader");
        owners.record(bitmapA,"wrong.m4a");
        check(owners.song(bitmapA)==null,"ambiguous shared bitmap accepted");
        owners.record(bitmapA,"A.m4a");check(owners.song(bitmapA)==null,"ambiguous bitmap guessed again");
        check(owners.song(shaderA).equals("A.m4a"),"existing transition shader rebound");
        Object unowned=new Object();owners.propagate(bitmapA,unowned);
        check(owners.song(unowned)==null,"ambiguous source produced video owner");
        Object replay=new Object();owners.record(replay,"A.m4a");owners.propagate(replay,new Object());
        check(owners.song(replay).equals("A.m4a"),"A-B-C-A replay failed");
        var pool=Executors.newFixedThreadPool(4);List<Future<?>> jobs=new ArrayList<>();
        for(int t=0;t<4;t++){final int thread=t;jobs.add(pool.submit(()->{
            for(int n=0;n<100;n++){Object decode=new Object(),shader=new Object();String song=thread+"-"+n;
                owners.record(decode,song);owners.propagate(decode,shader);check(song.equals(owners.song(shader)),"concurrent provenance race");}
        }));}
        for(Future<?> job:jobs)job.get();pool.shutdown();
        check(CoverOrigins.sameSong("C:/Music/a.m4a","file:///C:/Music/a.m4a"),"file URI did not match native path");
        owners.clear();check(owners.song(shaderA)==null,"stop retained bindings");
        System.out.println("PASS: A-B-C out-of-order decode, 5000 reordered transition draws, identity isolation, ambiguous/unknown fallback, A-B-C-A replay, concurrent decode and file-URI normalization");
    }
}
