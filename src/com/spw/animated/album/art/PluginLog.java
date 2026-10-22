package com.spw.animated.album.art;
import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
/** Always attempt a user-writable log as well as the legacy plugin directory. */
public final class PluginLog {
 public static Path userPath(){
  String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
  String base=System.getenv(os.startsWith("windows")?"LOCALAPPDATA":"XDG_STATE_HOME");
  Path root=base!=null&&!base.isBlank()&&Path.of(base).isAbsolute()?Path.of(base):
   Path.of(System.getProperty("user.home"),os.startsWith("windows")?"AppData/Local":".local/state");
  return root.resolve("SPW-Animated-Album-Art/animated-cover.log");
 }
 public static synchronized void write(String plugin,String message){
  String line=Instant.now()+" "+message+System.lineSeparator();
  System.err.print("Animated Cover: "+line);
  try{append(userPath(),line);}catch(IOException|RuntimeException error){System.err.println("Animated Cover user log failed: "+error);}
  try{
   Path root=Path.of(plugin);if(!Files.isDirectory(root))root=root.getParent();
   if(root!=null)append(root.resolve("animated-cover.log"),line);
  }catch(IOException|RuntimeException error){System.err.println("Animated Cover plugin log failed: "+error);}
 }
 static void append(Path path,String line)throws IOException{
  Files.createDirectories(path.toAbsolutePath().getParent());
  Files.writeString(path,line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
 }
}
