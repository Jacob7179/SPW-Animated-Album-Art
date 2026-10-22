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
    private String metadataWarning="";
    public OnlineArtwork(Path cache,Path ffmpeg,Consumer<String> log){
        this.cache=cache;this.ffmpeg=ffmpeg;this.log=log;
        this.search=this::search;this.download=this::download;
    }
    OnlineArtwork(Path cache,Search search,Download download){
        this.cache=cache;this.ffmpeg=null;this.log=System.err::println;this.search=search;this.download=download;
    }
    public static Path cacheDirectory(Object config,Path pluginRoot){
        try{
            Object path=HostMethods.method(Class.forName("com.xuncorp.spw.workshop.api.config.ConfigHelper"),Path.class,false).invoke(config);
            if(path instanceof Path p)return p.toAbsolutePath().getParent().resolve("cache/animated-artwork");
        }catch(ReflectiveOperationException | RuntimeException ignored){}
        return pluginRoot.resolve("cache/animated-artwork");
    }
    public synchronized void setEnabled(boolean value){
        if(enabled==value||closed)return;
        enabled=value;generation++;
        log.accept("Online artwork "+(value?"enabled":"disabled")+"; cache="+cache);
        if(!value){
            pending.values().forEach(f->f.cancel(true));pending.clear();
            Process p=process;if(p!=null)p.destroyForcibly();
        }
    }
    /** Returns only this album's completed cache entry. Starts at most one job per album. */
    public synchronized Path lookup(String artist,String album){
        if(!enabled||closed)return null;
        if(artist==null||album==null||artist.isBlank()||album.isBlank()){
            String warning="Online artwork skipped: missing artist/album; artist="+artist+"; album="+album;
            if(!warning.equals(metadataWarning)){metadataWarning=warning;log.accept(warning);}return null;
        }
        metadataWarning="";
        Album request=new Album(artist.trim(),album.trim());String key=request.key();
        Path file=cache.resolve("complete-"+hash(key)+".mp4");
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
            log.accept("Online artwork searching: "+album.artist()+" / "+album.album());
            Match found=search.find(album);
            if(!active(token))return;
            if(found==null||!matches(album,found)) {log.accept("Online artwork no matching square video: "+album.artist()+" / "+album.album());retry=Duration.ofHours(24).toMillis();return;}
            if(!appleVideoUrl(found.url()))throw new IOException("Unrecognized artwork video host");
            Files.createDirectories(cache);temp=Files.createTempFile(cache,"download-",".mp4");
            log.accept("Online artwork downloading: "+album.artist()+" / "+album.album());
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
        catch(Exception error){if(active(token))log.accept("Online artwork unavailable: "+error.getClass().getSimpleName()+": "+error.getMessage()+"; retry in 10 minutes");}
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
        connection.setRequestProperty("User-Agent","SPW-Animated-Album-Art/");
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
    // Prefer a moderate SDR H.264 variant, rather than whichever stream FFmpeg picks first.
    static String variant(String text,String base){
        String[] lines=text.split("\\R");String best=null;long score=Long.MAX_VALUE;
        var resolution=java.util.regex.Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
        for(int i=0;i+1<lines.length;i++){
            String line=lines[i];
            if(!line.startsWith("#EXT-X-STREAM-INF:")||!line.contains("avc1")||line.contains("VIDEO-RANGE=PQ")||line.contains("VIDEO-RANGE=HLG"))continue;
            var match=resolution.matcher(line);if(!match.find())continue;
            int width=Integer.parseInt(match.group(1)),height=Integer.parseInt(match.group(2));
            if(width!=height||width>1080)continue;
            String next=lines[i+1].trim();if(next.isEmpty()||next.startsWith("#"))continue;
            String url=URI.create(base).resolve(next).toString();if(!appleVideoUrl(url))continue;
            long distance=Math.abs(width-720);if(distance<score){best=url;score=distance;}
        }
        return best;
    }
    // Apple often describes one complete MP4 using consecutive HLS byte ranges.
    // Reading that MP4 once avoids repeated-range responses corrupting later fragments.
    static String singleFile(String text,String base){
        if(!text.contains("#EXT-X-ENDLIST")||text.contains("#EXT-X-KEY:"))return null;
        var map=java.util.regex.Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\",BYTERANGE=\"(\\d+)@0\"").matcher(text);
        if(!map.find())return null;
        String target=URI.create(base).resolve(map.group(1)).toString();
        if(!appleVideoUrl(target))return null;
        long expected=Long.parseLong(map.group(2));boolean ranged=false,found=false;
        for(String line:text.split("\\R")){
            line=line.trim();
            if(line.startsWith("#EXT-X-BYTERANGE:")){
                if(ranged)return null;
                String[] parts=line.substring(17).split("@");
                if(parts.length>2)return null;
                long size=Long.parseLong(parts[0]);
                if(size<=0||(parts.length==2&&Long.parseLong(parts[1])!=expected))return null;
                expected+=size;ranged=true;
            }else if(!line.isEmpty()&&!line.startsWith("#")){
                if(!ranged||!URI.create(base).resolve(line).toString().equals(target))return null;
                ranged=false;found=true;
            }
        }
        return found&&!ranged?target:null;
    }
    private String playlist(String url)throws Exception{
        if(!appleVideoUrl(url))throw new IOException("Unrecognized playlist host");
        HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(25000);connection.setInstanceFollowRedirects(false);
        try{
            int status=connection.getResponseCode();if(status!=200)throw new IOException("Artwork playlist HTTP "+status);
            try(InputStream body=connection.getInputStream()){
                byte[] bytes=body.readNBytes(524289);if(bytes.length>524288)throw new IOException("Artwork playlist too large");
                String text=new String(bytes,StandardCharsets.UTF_8);if(!text.startsWith("#EXTM3U"))throw new IOException("Invalid artwork playlist");return text;
            }
        }finally{connection.disconnect();}
    }
    private String downloadSource(String url)throws Exception{
        if(!URI.create(url).getPath().endsWith(".m3u8"))return url;
        String text=playlist(url),selected=variant(text,url);
        if(selected!=null){url=selected;text=playlist(url);}
        String direct=singleFile(text,url);
        if(direct!=null){log.accept("Online artwork: downloading complete single-file MP4");return direct;}
        return url;
    }
    private void download(String url,Path output) throws Exception {
        url=downloadSource(url);
        if(URI.create(url).getPath().toLowerCase(Locale.ROOT).endsWith(".mp4")){
            log.accept("Online artwork: Java HTTPS MP4 download (no FFmpeg TLS dependency)");
            downloadMp4(url,output);
        }else{
            log.accept("Online artwork: FFmpeg HLS download");
            run(List.of(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error","-xerror","-y",
                "-protocol_whitelist","https,tls,tcp,crypto","-rw_timeout","15000000","-i",url,
                "-map","0:v:0","-an","-sn","-dn","-t","120","-fs","104857600",
                "-c:v","copy","-movflags","+faststart",output.toString()),90);
        }
        // Use rawvideo explicitly: stripped Linux builds may lack null's default encoder.
        log.accept("Online artwork: validating complete video");
        run(List.of(ffmpeg.toString(),"-nostdin","-hide_banner","-loglevel","error","-xerror",
            "-i",output.toString(),"-map","0:v:0","-an","-sn","-dn","-c:v","rawvideo","-threads","1","-f","null","-"),90);
    }
    private void downloadMp4(String url,Path output)throws Exception{
        if(!appleVideoUrl(url))throw new IOException("Unrecognized video host");
        HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","video/mp4");connection.setRequestProperty("User-Agent","SPW-Animated-Album-Art");
        try{
            int status=connection.getResponseCode();if(status!=200)throw new IOException("Artwork MP4 HTTP "+status);
            long expected=connection.getContentLengthLong();if(expected>104857600)throw new IOException("Artwork exceeds 100 MiB");
            try(InputStream input=connection.getInputStream();OutputStream sink=Files.newOutputStream(output)){
                copyMp4(input,sink,expected,()->!enabled||closed||Thread.currentThread().isInterrupted());
            }
        }finally{connection.disconnect();}
    }
    static long copyMp4(InputStream input,OutputStream output,long expected,java.util.function.BooleanSupplier cancelled)throws Exception{
        byte[] buffer=new byte[65536];long total=0,deadline=System.nanoTime()+90_000_000_000L;
        while(true){
            if(cancelled.getAsBoolean())throw new InterruptedException();
            if(System.nanoTime()>deadline)throw new IOException("Artwork MP4 download timed out");
            int count=input.read(buffer);if(count<0)break;
            total+=count;if(total>104857600)throw new IOException("Artwork exceeds 100 MiB");
            output.write(buffer,0,count);
        }
        if(total<=32||(expected>=0&&total!=expected))throw new IOException("Incomplete artwork MP4: received "+total+", expected "+expected);
        return total;
    }
    private void run(List<String> command,int seconds)throws Exception{
        if(!enabled||closed||Thread.currentThread().isInterrupted())throw new InterruptedException();
        Path errors=Files.createTempFile(cache,"ffmpeg-",".log");
        Process p;
        try{p=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(errors.toFile()).start();}
        catch(Exception failure){Files.deleteIfExists(errors);throw failure;}
        process=p;
        if(!enabled||closed||Thread.currentThread().isInterrupted()){p.destroyForcibly();Files.deleteIfExists(errors);throw new InterruptedException();}
        try{
            if(!p.waitFor(seconds,TimeUnit.SECONDS)){throw new IOException("Artwork conversion timed out");}
            if(p.exitValue()!=0){
                String detail;
                try(InputStream input=Files.newInputStream(errors)){detail=new String(input.readNBytes(4096),StandardCharsets.UTF_8);}
                throw new IOException("FFmpeg exit "+p.exitValue()+": "+detail);
            }
        }finally{p.destroyForcibly();if(process==p)process=null;try{Files.deleteIfExists(errors);}catch(IOException ignored){}}
    }
    public synchronized void close(){
        setEnabled(false);closed=true;worker.shutdownNow();
    }
}



