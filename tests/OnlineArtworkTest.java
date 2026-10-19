package com.spw.animated.album.art;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OnlineArtworkTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Path await(OnlineArtwork source,String artist,String album)throws Exception{
        long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();Path result;
        do{result=source.lookup(artist,album);if(result!=null)return result;Thread.sleep(10);}while(System.nanoTime()<end);
        throw new AssertionError("Timed out: "+artist+" / "+album);
    }
    static final String URL="https://mvod.itunes.apple.com/cover.m3u8";
    public static void main(String[]args)throws Exception{
        Path root=Files.createTempDirectory("online-artwork-test-");
        AtomicInteger searches=new AtomicInteger(),downloads=new AtomicInteger();
        CountDownLatch entered=new CountDownLatch(1),gate=new CountDownLatch(1);
        try(OnlineArtwork source=new OnlineArtwork(root,album->{
            searches.incrementAndGet();entered.countDown();gate.await();
            return new OnlineArtwork.Match(album.artist(),album.album(),URL);
        },(url,file)->{downloads.incrementAndGet();Files.write(file,new byte[128]);})){
            check(source.lookup("Artist","Album")==null&&searches.get()==0,"default off must not search");
            source.setEnabled(true);
            source.lookup("Artist","Album");check(entered.await(2,TimeUnit.SECONDS),"worker did not start");
            for(int i=0;i<100;i++)check(source.lookup("Artist","Album")==null,"partial file was exposed");
            check(searches.get()==1,"duplicate album search");gate.countDown();
            Path first=await(source,"Artist","Album");
            check(first.equals(await(source,"ARTIST","ALBUM")),"album key normalization");
            check(downloads.get()==1,"duplicate album download");
            Path second=await(source,"Artist","Different Album");
            check(!first.equals(second),"late result crossed album boundary");
            check(!first.equals(await(source,"Different Artist","Album")),"artist missing from album key");
            source.setEnabled(false);check(source.lookup("Artist","Album")==null,"off must ignore online cache");
        }
        try(OnlineArtwork reopened=new OnlineArtwork(root,album->{throw new AssertionError("cache hit searched");},(u,p)->{throw new AssertionError("cache hit downloaded");})){
            reopened.setEnabled(true);check(reopened.lookup("Artist","Album")!=null,"cache not reused across restart");
        }
        AtomicInteger missed=new AtomicInteger();
        try(OnlineArtwork source=new OnlineArtwork(root.resolve("miss"),a->{missed.incrementAndGet();return null;},(u,p)->{throw new AssertionError();})){
            source.setEnabled(true);source.lookup("A","B");Thread.sleep(100);
            for(int i=0;i<100;i++)source.lookup("A","B");
            check(missed.get()==1,"no-match retry storm");
        }
        AtomicInteger mismatches=new AtomicInteger();
        try(OnlineArtwork source=new OnlineArtwork(root.resolve("edition"),a->new OnlineArtwork.Match(a.artist(),"Album (Deluxe)",URL),(u,p)->mismatches.incrementAndGet())){
            source.setEnabled(true);source.lookup("Artist","Album");Thread.sleep(100);
            check(mismatches.get()==0,"wrong edition downloaded");
        }
        CountDownLatch downloading=new CountDownLatch(1),cancelled=new CountDownLatch(1);
        Path cancelRoot=root.resolve("cancel");
        try(OnlineArtwork source=new OnlineArtwork(cancelRoot,a->new OnlineArtwork.Match(a.artist(),a.album(),URL),(u,p)->{
            Files.write(p,new byte[128]);downloading.countDown();
            try{Thread.sleep(5000);}catch(InterruptedException ex){cancelled.countDown();throw ex;}
        })){
            source.setEnabled(true);source.lookup("A","B");check(downloading.await(2,TimeUnit.SECONDS),"download not started");
            source.setEnabled(false);check(cancelled.await(2,TimeUnit.SECONDS),"disable did not interrupt download");
        }
        Thread.sleep(100);
        try(var files=Files.list(cancelRoot)){check(files.findAny().isEmpty(),"cancelled partial file survived");}
        check(OnlineArtwork.matches(new OnlineArtwork.Album("LINKIN PARK","Living Things"),new OnlineArtwork.Match("Linkin Park","LIVING THINGS",URL)),"case matching");
        check(!OnlineArtwork.matches(new OnlineArtwork.Album("A","Album"),new OnlineArtwork.Match("B","Album",URL)),"wrong artist accepted");
        check(!OnlineArtwork.appleVideoUrl("https://mvod.itunes.apple.com.evil.test/x"),"unsafe host");
        check(!OnlineArtwork.appleVideoUrl("file:///C:/x"),"file URL accepted");
        check(!OnlineArtwork.appleVideoUrl("http://mvod.itunes.apple.com/x"),"HTTP URL accepted");
        var parsed=OnlineArtwork.parse("{\"artist\":\"A\\u0026B\",\"album\":\"Test\",\"url\":\"https://mvod.itunes.apple.com/x\",\"url_tall\":\"ignore\"}");
        check(parsed.artist().equals("A&B")&&parsed.url().equals("https://mvod.itunes.apple.com/x"),"API response parsing");
        check(OnlineArtwork.parse("{\"url\":null}").url().isEmpty(),"null square URL");
        System.out.println("PASS: default off, deduplication, persistent album cache, stale-result isolation, no-match cooldown, edition/artist matching, disable cancellation, partial cleanup, URL validation and JSON parsing");
    }
}
