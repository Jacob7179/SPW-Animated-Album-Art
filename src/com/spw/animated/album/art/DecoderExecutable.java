package com.spw.animated.album.art;
import java.nio.file.*;
import java.util.*;

/** Never attempt to run the bundled Windows executable on Linux. */
public final class DecoderExecutable {
    public static Path resolve(Path plugin){
        return resolve(plugin,System.getProperty("os.name",""),System.getenv("PATH"),System.getenv("SPW_FFMPEG"));
    }
    static Path resolve(Path plugin,String os,String searchPath,String override){
        return resolve(plugin,os,searchPath,override,Files::isExecutable);
    }
    static Path resolve(Path plugin,String os,String searchPath,String override,java.util.function.Predicate<Path> executable){
        boolean windows=os.toLowerCase(Locale.ROOT).startsWith("windows");
        if(override!=null&&!override.isBlank()){
            Path supplied=Path.of(override).toAbsolutePath();
            if(usable(supplied,windows,executable))return supplied;
            throw new IllegalStateException("SPW_FFMPEG does not point to an executable FFmpeg for "+os+": "+supplied);
        }
        Path bundled=plugin.resolve(windows?"bin/ffmpeg.exe":"bin/ffmpeg");
        if(!windows&&Files.isRegularFile(bundled))prepareBundled(bundled);
        if(usable(bundled,windows,executable))return bundled;
        String separator=windows?";":":";
        if(searchPath!=null)for(String directory:searchPath.split(java.util.regex.Pattern.quote(separator))){
            if(directory.isBlank())continue;
            try{Path candidate=Path.of(directory).resolve(windows?"ffmpeg.exe":"ffmpeg");if(usable(candidate,windows,executable))return candidate.toAbsolutePath();}
            catch(InvalidPathException ignored){}
        }
        throw new IllegalStateException(windows?"FFmpeg missing: install FFmpeg or set SPW_FFMPEG to ffmpeg.exe"
            :"FFmpeg missing: install the native ffmpeg package, or set SPW_FFMPEG to an executable native ffmpeg path. Windows ffmpeg.exe is not supported on "+os);
    }
    // ZIP/workshop extraction may discard Unix mode bits. Repair only our bundled binary.
    static void prepareBundled(Path file){
        if(Files.isExecutable(file))return;
        try{
            var permissions=Files.getPosixFilePermissions(file);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file,permissions);
        }catch(java.io.IOException|UnsupportedOperationException|SecurityException ignored){
            try{file.toFile().setExecutable(true,true);}catch(SecurityException denied){}
        }
        if(!Files.isExecutable(file)&&FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            throw new IllegalStateException("Bundled Linux FFmpeg exists but cannot be made executable: "+file
                +". Check plugin-directory permissions or a noexec filesystem.");
    }
    private static boolean usable(Path file,boolean windows,java.util.function.Predicate<Path> executable){
        return Files.isRegularFile(file)&&(windows||executable.test(file))
            &&(windows||!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"));
    }
}


