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
        URL location=anchor.getProtectionDomain().getCodeSource().getLocation();
        if(!location.getProtocol().equals("file"))throw new IOException("Unsupported host code location: "+location.getProtocol());
        Target target=inspect(Path.of(location.toURI()));
        Class<?> owner=Class.forName(target.owner(),false,anchor.getClassLoader());
        original=owner.getDeclaredMethod(target.method(),
            Class.forName("kotlin.jvm.functions.Function0",false,loader),boolean.class,boolean.class,boolean.class,
            Class.forName("androidx.compose.ui.Modifier",false,loader),Class.forName("androidx.compose.runtime.Composer",false,loader),int.class,int.class);
        original.setAccessible(true);return target;
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
