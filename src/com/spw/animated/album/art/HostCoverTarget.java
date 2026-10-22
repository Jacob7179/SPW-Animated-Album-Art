package com.spw.animated.album.art;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Locate the same desktop cover function across independently obfuscated OS builds. */
public final class HostCoverTarget {
    static final String DESCRIPTOR="(Lkotlin/jvm/functions/Function0;ZZZLandroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V";
    static final String COVER="com.xuncorp.voxzen.ui.player.FullScreenCover (FullScreenCover.kt:";
    static final String CALLER="com.xuncorp.voxzen.ui.player.FloatPlaybackCover (FloatPlaybackCover.kt:";
    public record Target(String owner,String method,String caller) {}
    private static volatile Method original;
    public static Target resolve(ClassLoader loader)throws Exception{
        Class<?> anchor=Class.forName("com.xuncorp.voxzen.tag.TagParser",false,loader);
        Target target=inspect(hostArchive(anchor));
        Class<?> owner=Class.forName(target.owner(),false,anchor.getClassLoader());
        original=owner.getDeclaredMethod(target.method(),
            Class.forName("kotlin.jvm.functions.Function0",false,loader),boolean.class,boolean.class,boolean.class,
            Class.forName("androidx.compose.ui.Modifier",false,loader),Class.forName("androidx.compose.runtime.Composer",false,loader),int.class,int.class);
        original.setAccessible(true);return target;
    }
    static Path hostArchive(Class<?> anchor)throws IOException{
        String entry=anchor.getName().replace('.','/')+".class";
        LinkedHashSet<Path> candidates=new LinkedHashSet<>();
        try{
            var domain=anchor.getProtectionDomain();
            var source=domain==null?null:domain.getCodeSource();
            addLocation(candidates,source==null?null:source.getLocation(),entry,false);
        }catch(SecurityException ignored){}
        try{addLocation(candidates,anchor.getResource("/"+entry),entry,true);}catch(SecurityException ignored){}
        for(ClassLoader loader=anchor.getClassLoader();loader!=null;loader=loader.getParent()){
            if(loader instanceof URLClassLoader urls)for(URL url:urls.getURLs())addLocation(candidates,url,entry,false);
        }
        for(String value:System.getProperty("java.class.path","").split(java.util.regex.Pattern.quote(File.pathSeparator))){
            if(value.isBlank())continue;
            try{candidates.add(Path.of(value).toAbsolutePath().normalize());}catch(InvalidPathException ignored){}
        }
        for(Path path:candidates){
            if(Files.isDirectory(path)){if(Files.isRegularFile(path.resolve(entry)))return path;}
            else if(Files.isRegularFile(path))try(ZipFile zip=new ZipFile(path.toFile())){
                if(zip.getEntry(entry)!=null)return path;
            }catch(IOException ignored){}
        }
        throw new IOException("Cannot locate Salt Player host bytecode: CodeSource/resource/classpath contain no "+entry
            +". Include the full startup log and JVM launch classpath when reporting this host loader.");
    }
    private static void addLocation(Set<Path> paths,URL url,String entry,boolean classResource){
        if(url==null)return;
        try{
            if(url.getProtocol().equals("jar")){
                var connection=(JarURLConnection)url.openConnection();connection.setUseCaches(false);
                addLocation(paths,connection.getJarFileURL(),entry,false);
            }else if(url.getProtocol().equals("file")){
                Path path=Path.of(url.toURI());
                if(classResource)for(int i=0;i<entry.split("/").length&&path!=null;i++)path=path.getParent();
                if(path!=null)paths.add(path.toAbsolutePath().normalize());
            }
        }catch(IOException|URISyntaxException|RuntimeException ignored){}
    }
    public static void draw(Object[] args){
        Method method=original;if(method==null)throw new IllegalStateException("Native cover has not been resolved");
        try{method.invoke(null,args);}
        catch(java.lang.reflect.InvocationTargetException ex){
            Throwable cause=ex.getCause();if(cause instanceof RuntimeException r)throw r;if(cause instanceof Error e)throw e;
            throw new IllegalStateException("Native cover call failed",cause);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot call native cover",ex);}
    }
    static Target inspect(Path archive)throws IOException{
        List<byte[]> candidates=new ArrayList<>();
        if(Files.isDirectory(archive)){
            try(var paths=Files.walk(archive)){
                for(Path path:paths.filter(p->p.toString().endsWith(".class")).toList())addCandidate(candidates,Files.readAllBytes(path));
            }
        }else try(ZipFile zip=new ZipFile(archive.toFile())){
            var entries=zip.entries();while(entries.hasMoreElements()){
                var entry=entries.nextElement();if(!entry.getName().endsWith(".class"))continue;
                // Only parse classes bearing a stable source trace, not every dependency.
                try(InputStream input=zip.getInputStream(entry)){addCandidate(candidates,input.readAllBytes());}
            }
        }
        return inspectClasses(candidates);
    }
    private static void addCandidate(List<byte[]> result,byte[] bytes){
        String ascii=new String(bytes,StandardCharsets.ISO_8859_1);
        if(ascii.contains(COVER)||ascii.contains(CALLER))result.add(bytes);
    }
    static Target inspectClasses(List<byte[]> bytes)throws IOException{
        List<ClassModel> models=bytes.stream().map(b->ClassFile.of().parse(b)).toList();
        record Found(ClassModel owner,MethodModel method){}
        List<Found> targets=new ArrayList<>();
        for(ClassModel model:models)for(MethodModel method:model.methods()){
            if(method.methodType().stringValue().equals(DESCRIPTOR)&&hasTrace(method,COVER))targets.add(new Found(model,method));
        }
        if(targets.size()!=1)throw new IOException("Unsupported Salt Player cover ABI: expected one FullScreenCover, found "+targets.size());
        Found found=targets.getFirst();String owner=found.owner().thisClass().asInternalName();String name=found.method().methodName().stringValue();
        List<String> callers=new ArrayList<>();
        for(ClassModel model:models){
            if(model.methods().stream().noneMatch(m->hasTrace(m,CALLER)))continue;
            int calls=0;
            for(MethodModel method:model.methods())if(method.code().isPresent())for(CodeElement element:method.code().get()){
                if(element instanceof InvokeInstruction invoke&&invoke.owner().asInternalName().equals(owner)
                    &&invoke.name().stringValue().equals(name)&&invoke.type().stringValue().equals(DESCRIPTOR))calls++;
            }
            if(calls==1)callers.add(model.thisClass().asInternalName().replace('/','.'));
        }
        if(callers.size()!=1)throw new IOException("Unsupported Salt Player cover ABI: expected one FloatPlaybackCover call site, found "+callers.size());
        return new Target(owner.replace('/','.'),name,callers.getFirst());
    }
    private static boolean hasTrace(MethodModel method,String prefix){
        if(method.code().isEmpty())return false;
        for(CodeElement element:method.code().get())if(element instanceof ConstantInstruction constant
            &&constant.constantValue() instanceof String value&&value.startsWith(prefix))return true;
        return false;
    }
}
