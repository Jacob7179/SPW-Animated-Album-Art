package com.spw.animated.album.art;

import java.lang.reflect.*;
import java.util.*;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import androidx.compose.ui.graphics.painter.Painter;
import com.xuncorp.voxzen.tag.TagParser;
import com.xuncorp.voxzen.service.PlaybackMonitor;
import com.xuncorp.voxzen.service.PlaybackArtworkState;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.skia.*;

/** Match semantic JVM shapes, never a platform's obfuscated/mangled member name. */
public final class HostMethods {
    public record Targets(Method readImage,Method makeShader,Method painterDraw,Method intrinsicSize,
                          Method artworkFlow,Field artworkGeneration) {}
    private static volatile Targets resolved;
    private static Targets discover(){return new Targets(
            method(TagParser.class,Object.class,false,String.class,int.class,int.class),
            method(Bitmap.class,Shader.class,false,FilterTileMode.class,FilterTileMode.class,SamplingMode.class,float[].class),
            method(Painter.class,void.class,false,DrawScope.class,long.class,float.class,ColorFilter.class),
            method(Painter.class,long.class,false),
            genericGetter(PlaybackMonitor.class,StateFlow.class,PlaybackArtworkState.class),
            instanceField(PlaybackArtworkState.class,int.class));
    }
    public static Targets resolve(){
        Targets result=resolved;if(result!=null)return result;
        synchronized(HostMethods.class){if(resolved==null)resolved=discover();return resolved;}
    }
    public static Method method(Class<?> owner,Class<?> returns,boolean isStatic,Class<?>... parameters){
        List<Method> matches=Arrays.stream(owner.getDeclaredMethods())
            .filter(m->!m.isBridge()&&!m.isSynthetic()&&Modifier.isStatic(m.getModifiers())==isStatic
                &&m.getReturnType()==returns&&Arrays.equals(m.getParameterTypes(),parameters)).toList();
        if(matches.size()!=1)throw new IllegalStateException("Unsupported host ABI: "+owner.getName()+" -> "+returns.getTypeName()
            +Arrays.toString(parameters)+" expected one "+(isStatic?"static":"instance")+" method, found "+matches.size()+" "+matches);
        Method result=matches.getFirst();result.setAccessible(true);return result;
    }
    static Method genericGetter(Class<?> owner,Class<?> raw,Class<?> argument){
        List<Method> matches=Arrays.stream(owner.getDeclaredMethods())
            .filter(m->!m.isBridge()&&!m.isSynthetic()&&!Modifier.isStatic(m.getModifiers())&&m.getParameterCount()==0)
            .filter(m->m.getGenericReturnType() instanceof ParameterizedType type&&type.getRawType()==raw
                &&Arrays.equals(type.getActualTypeArguments(),new Type[]{argument})).toList();
        if(matches.size()!=1)throw new IllegalStateException("Unsupported host ABI: expected one "+owner.getName()
            +" getter returning "+raw.getName()+"<"+argument.getName()+">, found "+matches.size());
        Method result=matches.getFirst();result.setAccessible(true);return result;
    }
    public static Field instanceField(Class<?> owner,Class<?> valueType){
        List<Field> matches=new ArrayList<>();
        for(Class<?> type=owner;type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers())&&!field.isSynthetic()&&field.getType()==valueType)matches.add(field);
        if(matches.size()!=1)throw new IllegalStateException("Unsupported host ABI: expected one "+valueType.getName()
            +" instance field in "+owner.getName()+", found "+matches.size());
        Field result=matches.getFirst();result.setAccessible(true);return result;
    }
    static Object singleton(Class<?> owner)throws ReflectiveOperationException{
        List<Field> matches=Arrays.stream(owner.getDeclaredFields())
            .filter(f->Modifier.isStatic(f.getModifiers())&&f.getType()==owner).toList();
        if(matches.size()!=1)throw new IllegalStateException("Unsupported singleton shape: "+owner.getName());
        Field field=matches.getFirst();field.setAccessible(true);return field.get(null);
    }
    public static Object readImage(String song,int width,int height)throws ReflectiveOperationException{
        return resolve().readImage().invoke(singleton(TagParser.class),song,width,height);
    }
    public static int artworkGeneration(){
        try{
            Object flow=resolve().artworkFlow().invoke(singleton(PlaybackMonitor.class));
            Object state=((StateFlow<?>)flow).getValue();
            return resolve().artworkGeneration().getInt(state);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot read native artwork generation",ex);}
    }
}

