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
    @SuppressWarnings("unchecked")
    public static void draw(List<Object> args, Modifier modifier, Composer composer) {
        androidx.compose.ui.א.Ϳ((Function0<Unit>)args.get(0), (Boolean)args.get(1),
            (Boolean)args.get(2), (Boolean)args.get(3), modifier, composer, 0, 0);
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
            if(element.getClass().getName().contains("PainterElement")){
                try{
                    java.lang.reflect.Method getter=element.getClass().getDeclaredMethod("getPainter");
                    getter.setAccessible(true);result.add(getter.invoke(element));
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
        if(node.getModifier().any(element->element.getClass().getName().contains("PainterElement")))
            node.getInnerCoordinator$ui().invalidateLayer();
        for(LayoutNode child:node.getChildren$ui())invalidateImages(child);
    }
    private static final java.lang.reflect.Field layerAlpha;
    static {
        java.lang.reflect.Field field=null;
        try{field=NodeCoordinator.class.getDeclaredField("lastLayerAlpha");field.setAccessible(true);}
        catch(ReflectiveOperationException | RuntimeException ignored){}
        layerAlpha=field;
    }
    public static float transitionAlpha(LayoutCoordinates image,LayoutCoordinates root) {
        float alpha=1f;
        if(!(image instanceof NodeCoordinator current))return alpha;
        int guard=0;
        while(current!=null && current!=root && guard++<100) {
            if(layerAlpha!=null && current.getLayer()!=null) {
                try{alpha*=layerAlpha.getFloat(current);}catch(IllegalAccessException ignored){}
            }
            NodeCoordinator next=current.getWrappedBy$ui();
            if(next==null && current.getParentCoordinates() instanceof NodeCoordinator parent)next=parent;
            current=next;
        }
        return Math.max(0f,Math.min(1f,alpha));
    }
    private static LayoutNode find(LayoutNode node, LayoutNode best) {
        if(!node.isAttached()) return best;
        boolean image=node.getModifier().any(element -> element.getClass().getName().contains("PainterElement"));
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
