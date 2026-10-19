package com.spw.animated.album.art;
import java.nio.file.*;
public class OnlineArtworkLiveTest {
    public static void main(String[] args)throws Exception{
        try(OnlineArtwork source=new OnlineArtwork(Path.of(args[0]),Path.of(args[1]),System.out::println)){
            source.setEnabled(true);long end=System.nanoTime()+120_000_000_000L;Path file;
            while((file=source.lookup("LINKIN PARK","LIVING THINGS"))==null&&System.nanoTime()<end)Thread.sleep(250);
            if(file==null)throw new AssertionError("Live service lookup/conversion failed; static-cover fallback remains active");
            System.out.println("PASS live API -> validated cached MP4: "+file+" ("+Files.size(file)+" bytes)");
        }
    }
}
