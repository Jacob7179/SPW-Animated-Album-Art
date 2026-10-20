package com.spw.animated.album.art;
import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.NodeCoordinator;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import java.util.List;

/** Call the original cover from a different call site, outside the registered UI replacement. */
public final class HostCoverBridge {
    private static final ClassValue<java.util.Optional<java.lang.reflect.Field>> painterField=new ClassValue<>(){
        protected java.util.Optional<java.lang.reflect.Field> computeValue(Class<?> type){
            if(!androidx.compose.ui.node.ModifierNodeElement.class.isAssignableFrom(type))return java.util.Optional.empty();
            try{return java.util.Optional.of(HostMethods.instanceField(type,androidx.compose.ui.graphics.painter.Painter.class));}
            catch(IllegalStateException unavailable){return java.util.Optional.empty();}
        }
    };
    @SuppressWarnings("unchecked")
    public static void draw(List<Object> args, Modifier modifier, Composer composer) {
        HostCoverTarget.draw(new Object[]{args.get(0),args.get(1),args.get(2),args.get(3),modifier,composer,0,0});
    }
/** The decoder and mipmap painter share these exact native resource identities. */
    public static Object imageSource(Object result) {
        if(result instanceof androidx.compose.ui.graphics.ImageBitmap image)
            return androidx.compose.ui.graphics.SkiaImageAsset_skikoKt.asSkiaBitmap(image);
        return null;
    }
    public static Object painterSource(Object painter) {
        if(painter==null)return null;
        // Salt Player's MipmapCoverPainter holds its immutable Shader, not the Bitmap.
        // Shader ownership is captured when Bitmap.makeShader returns.
        for(Class<?> type=painter.getClass();type!=null;type=type.getSuperclass()) {
            for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(field.getType()==org.jetbrains.skia.Shader.class) {
                    try {field.setAccessible(true);return field.get(painter);}
                    catch(IllegalAccessException | RuntimeException ignored){}
                }
                if(field.getType()==androidx.compose.ui.graphics.ImageBitmap.class) {
                    try {field.setAccessible(true);return imageSource(field.get(painter));}
                    catch(IllegalAccessException | RuntimeException ignored){}
                }
            }
        }
        return null;
    }
    public static LayoutCoordinates imageCoordinates(LayoutCoordinates root) {
        if(!(root instanceof NodeCoordinator coordinator)) return null;
        LayoutNode best=find(coordinator.getLayoutNode(), null);
        return best==null ? null : best.getCoordinates();
    }
    public static java.util.List<Object> painters(LayoutCoordinates root) {
        java.util.List<Object> result=new java.util.ArrayList<>();
        if(root instanceof NodeCoordinator coordinator)collectPainters(coordinator.getLayoutNode(),result);
        return result;
    }
    private static void collectPainters(LayoutNode node,java.util.List<Object> result){
        if(!node.isAttached())return;
        node.getModifier().any(element->{
            if(painterField.get(element.getClass()).isPresent()){
                try{
                    result.add(painterField.get(element.getClass()).orElseThrow().get(element));
                }catch(ReflectiveOperationException | RuntimeException ignored){}
            }
            return false;
        });
        for(LayoutNode child:node.getChildren$ui())collectPainters(child,result);
    }
    public static void invalidateImages(LayoutCoordinates root) {
        if(root instanceof NodeCoordinator coordinator)invalidateImages(coordinator.getLayoutNode());
    }
    private static void invalidateImages(LayoutNode node) {
        if(!node.isAttached())return;
        if(node.getModifier().any(element->painterField.get(element.getClass()).isPresent()))
            node.getInnerCoordinator$ui().invalidateLayer();
        for(LayoutNode child:node.getChildren$ui())invalidateImages(child);
    }
    private static LayoutNode find(LayoutNode node, LayoutNode best) {
        if(!node.isAttached()) return best;
        boolean image=node.getModifier().any(element -> painterField.get(element.getClass()).isPresent());
        if(image) {
            if(best==null || area(node)>area(best)) best=node;
        }
        for(LayoutNode child:node.getChildren$ui()) best=find(child,best);
        return best;
    }
    private static long area(LayoutNode node) {
        return (long)node.getWidth()*node.getHeight();
    }
}





