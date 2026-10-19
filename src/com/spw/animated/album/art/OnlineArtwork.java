package com.spw.animated.album.art;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import kotlinx.serialization.json.*;

/** Album-scoped cache. Polling never performs network or starts a process on the UI thread. */
public final class OnlineArtwork implements AutoCloseable {
    record Album(String artist,String album) {
        String key(){return normalize(artist).length()+":"+normalize(artist)+":"+normalize(album);}
    }
    record Match(String artist,String album,String url) {}
    interface Search { Match find(Album album) throws Exception; }
    interface Download { void save(String url,Path output) throws Exception; }
    private final Path cache,ffmpeg;
    private final Consumer<String> log;
    private final Search search;
    private final Download download;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AnimatedCover-online");t.setDaemon(true);return t;});
    private final Map<String,Future<?>> pending=new HashMap<>();
    private final Map<String,Long> retries=new HashMap<>();
    private volatile boolean enabled,closed;
    private volatile long generation;
    private volatile Process process;
    public OnlineArtwork(Path cache,Path ffmpeg,Consumer<String> log){
        this.cache=cache;this.ffmpeg=ffmpeg;this.log=log;
        this.search=this::search;this.download=this::download;
    }
    OnlineArtwork(Path cache,Search search,Download download){
        this.cache=cache;this.ffmpeg=null;this.log=System.err::println;this.search=search;this.download=download;
    }
    public static Path cacheDirectory(Object config,Path pluginRoot){
        try{
            Object path=Class.forName("com.xuncorp.spw.workshop.api.config.ConfigHelper")
                .getMethod("getConfigPath").invoke(config);
            if(path instanceof Path p)return p.toAbsolutePath().getParent().resolve("cache/animated-artwork");
        }catch(ReflectiveOperationException | RuntimeException ignored){}
        return pluginRoot.resolve("cache/animated-artwork");
    }
    public synchronized void setEnabled(boolean value){
        if(enabled==value||closed)return;
        enabled=value;generation++;
        if(!value){
            pending.values().forEach(f->f.cancel(true));pending.clear();
            Process p=process;if(p!=null)p.destroyForcibly();
        }
    }
    /** Returns only this album's completed cache entry. Starts at most one job per album. */
    public synchronized Path lookup(String artist,String album){
        if(!enabled||closed||artist==null||album==null||artist.isBlank()||album.isBlank())return null;
        Album request=new Album(artist.trim(),album.trim());String key=request.key();
        Path file=cache.resolve(hash(key)+".mp4");
        try{
            if(Files.isRegularFile(file)&&Files.size(file)>32)return file;
            if(Files.isRegularFile(file))Files.delete(file);
        }catch(IOException ignored){}
        if(pending.containsKey(key)||retries.getOrDefault(key,0L)>System.currentTimeMillis())return null;
        long token=generation;
        // FutureTask is registered before execution, avoiding completion/registration races.
        FutureTask<Void> job=new FutureTask<>(()->{fetch(request,key,file,token);return null;});
        pending.put(key,job);worker.execute(job);return null;
    }
    private boolean active(long token){return enabled&&!closed&&generation==token&&!Thread.currentThread().isInterrupted();}
    private void fetch(Album album,String key,Path file,long token){
        Path temp=null;long retry=Duration.ofMinutes(10).toMillis();
        try{
            if(!active(token))return;
            Match found=search.find(album);
            if(!active(token))return;
            if(found==null||!matches(album,found)) {retry=Duration.ofHours(24).toMillis();return;}
            if(!appleVideoUrl(found.url()))throw new IOException("Unrecognized artwork video host");
            Files.createDirectories(cache);temp=Files.createTempFile(cache,"download-",".mp4");
            download.save(found.url(),temp);
            if(!active(token))return;
            if(Files.size(temp)<=32)throw new IOException("Empty artwork download");
            synchronized(this){
                if(!active(token))return;
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            }
            log.accept("Online artwork cached for "+album.artist()+" / "+album.album());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Exception error){if(active(token))log.accept("Online artwork unavailable: "+error.getClass().getSimpleName());}
        finally{
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}
            synchronized(this){if(generation==token){pending.remove(key);retries.put(key,System.currentTimeMillis()+retry);}}
        }
    }
    static String normalize(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }
    static boolean matches(Album requested,Match found){
        return !normalize(requested.artist()).isEmpty()&&!normalize(requested.album()).isEmpty()
            &&normalize(requested.artist()).equals(normalize(found.artist()))
            &&normalize(requested.album()).equals(normalize(found.album()));
    }
    static boolean appleVideoUrl(String value){
        try{URI uri=URI.create(value);String host=uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())&&host!=null&&uri.getUserInfo()==null
                &&(host.endsWith(".apple.com")||host.endsWith(".mzstatic.com"));
        }catch(RuntimeException ex){return false;}
    }
    private static String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    static Match parse(String json){
        if(!(Json.Default.parseToJsonElement(json) instanceof JsonObject obj))return null;
        return new Match(field(obj,"artist"),field(obj,"album"),field(obj,"url"));
    }
    private static String field(JsonObject obj,String key){
        JsonElement element=obj.get(key);
        return element instanceof JsonPrimitive p&&p.isString()?p.getContent():"";
    }
    private Match search(Album album) throws Exception {
        URI uri=URI.create("https://artwork.m8tec.top/api/v1/artwork/search?artist="+
            URLEncoder.encode(album.artist(),StandardCharsets.UTF_8)+"&album="+URLEncoder.encode(album.album(),StandardCharsets.UTF_8));
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","SPW-Animated-Album-Art/0.4.1");
        try{
            int status=connection.getResponseCode();
            if(status==404)return null;
            if(status!=200)throw new IOException("Artwork service HTTP "+status);
            try(InputStream body=connection.getInputStream()){
                byte[] bytes=body.readNBytes(262145);
                if(bytes.length>262144)throw new IOException("Artwork response too large");
                return parse(new String(bytes,StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
    private void download(String url,Path output) throws Exception {
        run(List.of(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error","-y",
            "-protocol_whitelist","https,tls,tcp,crypto","-rw_timeout","15000000","-i",url,
            "-map","0:v:0","-an","-sn","-dn","-t","120","-fs","104857600",
            "-vf","scale=720:720:force_original_aspect_ratio=increase,crop=720:720",
            "-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-movflags","+faststart",output.toString()),90);
        // Reject incomplete/non-video output before exposing the file to the renderer.
        run(List.of(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error","-xerror",
            "-i",output.toString(),"-map","0:v:0","-frames:v","1","-f","null","-"),15);
    }
    private void run(List<String> command,int seconds)throws Exception{
        if(!enabled||closed||Thread.currentThread().isInterrupted())throw new InterruptedException();
        Process p=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process=p;
        if(!enabled||closed||Thread.currentThread().isInterrupted()){p.destroyForcibly();throw new InterruptedException();}
        try{
            if(!p.waitFor(seconds,TimeUnit.SECONDS)){throw new IOException("Artwork conversion timed out");}
            if(p.exitValue()!=0)throw new IOException("Artwork conversion failed");
        }finally{p.destroyForcibly();if(process==p)process=null;}
    }
    public synchronized void close(){
        setEnabled(false);closed=true;worker.shutdownNow();
    }
}


