import com.spw.animated.album.art.SongVideoPath;
import java.nio.file.*;
import java.util.*;
public class SongVideoPathTest {
    static void check(boolean result,String label){if(!result)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("cover-names-");
        try {
            Path song=Files.createFile(dir.resolve("song 100%.flac"));
            Path first=Files.createFile(dir.resolve("first.mp4"));
            Path fifth=Files.createFile(dir.resolve("fifth.mp4"));
            check(first.equals(SongVideoPath.find(song.toString(),List.of("first.mp4","fifth.mp4"))),"priority");
            check(fifth.equals(SongVideoPath.find(song.toUri().toString(),List.of("missing.mp4",""," ","no.mp4","fifth.mp4"))),"fifth slot and file URI");
            check(SongVideoPath.find(song.toString(),List.of("a","b","c","d","e","first.mp4"))==null,"five-slot limit");
            check(SongVideoPath.find(song.toString(),List.of("../first.mp4",first.toString(),"bad\u0000.mp4"))==null,"invalid filenames");
            check(fifth.equals(SongVideoPath.find(song.toString(),List.of(" fifth.mp4 "))),"trim");
            check(SongVideoPath.find(null,List.of("first.mp4"))==null,"no song");
            check(SongVideoPath.find(song.toString())==null,"missing default");
            Path defaultFile=Files.createFile(dir.resolve("square_animated_artwork.mp4"));
            check(defaultFile.equals(SongVideoPath.find(song.toString())),"existing default");
            Files.delete(first);
            check(fifth.equals(SongVideoPath.find(song.toString(),List.of("first.mp4","fifth.mp4"))),"fallback after removal");
            System.out.println("PASS: filename priority, five slots, blank/invalid names, file URI, default, and fallback.");
        } finally {
            try(var children=Files.list(dir)){for(Path child:children.toList())Files.delete(child);}
            Files.delete(dir);
        }
    }
}
