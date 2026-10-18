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
