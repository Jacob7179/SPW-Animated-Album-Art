package com.spw.animated.album.art;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/** Weak identity provenance: never infer an old image's owner from current playback. */
public final class CoverOrigins {
    private static final class Entry {
        final WeakReference<Object> image;
        String song;
        Entry(Object image,String song){this.image=new WeakReference<>(image);this.song=song;}
    }
    private final ArrayList<Entry> entries=new ArrayList<>();
    public synchronized void record(Object image,String song){
        if(image==null||song==null||song.isBlank())return;
        for(Iterator<Entry> it=entries.iterator();it.hasNext();){
            Entry entry=it.next();Object candidate=entry.image.get();
            if(candidate==null){it.remove();continue;}
            if(candidate==image){
                // A shared object with conflicting origins is ambiguous. Never reassign it.
                if(!sameSong(entry.song,song))entry.song=null;
                return;
            }
        }
        entries.add(new Entry(image,song));
    }
    public synchronized String song(Object image){
        if(image==null)return null;
        for(Iterator<Entry> it=entries.iterator();it.hasNext();){
            Entry entry=it.next();Object candidate=entry.image.get();
            if(candidate==null){it.remove();continue;}
            if(candidate==image)return entry.song;
        }
        return null;
    }
    public synchronized void propagate(Object source,Object rendered){
        String owner=song(source);
        if(owner!=null)record(rendered,owner);
    }
    public synchronized void clear(){entries.clear();}
    public static boolean sameSong(String first,String second){
        if(first==null||second==null)return false;
        try{
            var a=SongVideoPath.songPath(first).toAbsolutePath().normalize();
            var b=SongVideoPath.songPath(second).toAbsolutePath().normalize();
            return a.equals(b);
        }catch(RuntimeException ex){return first.equals(second);}
    }
}
