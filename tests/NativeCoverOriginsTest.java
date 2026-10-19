package com.spw.animated.album.art;
import org.jetbrains.skia.*;
import java.lang.reflect.*;
public class NativeCoverOriginsTest {
    public static void main(String[] args)throws Exception{
        var owners=new CoverOrigins();
        Class<?> type=Class.forName("androidx.compose.ui.\u0199");
        Constructor<?> constructor=type.getDeclaredConstructor(Bitmap.class);constructor.setAccessible(true);
        Object[] painters=new Object[3];Bitmap[] bitmaps=new Bitmap[3];
        try{
            for(int i=0;i<3;i++){
                Bitmap bitmap=new Bitmap();bitmaps[i]=bitmap;bitmap.allocPixels(ImageInfo.Companion.makeN32Premul(8,8));bitmap.erase(0xff000000|i);
                // This is the same ImageBitmap -> Bitmap identity path used by TagParser's hook.
                var image=androidx.compose.ui.graphics.SkiaImageAsset_skikoKt.asComposeImageBitmap(bitmap);
                Object decoded=HostCoverBridge.imageSource(image);if(decoded!=bitmap)throw new AssertionError("Decoder bitmap identity changed");
                owners.record(decoded,"song-"+i+".m4a");
                Object painter=constructor.newInstance(bitmap);painters[i]=painter;
                Object shader=HostCoverBridge.painterSource(painter);
                if(!(shader instanceof Shader))throw new AssertionError("Native mipmap shader not found");
                // Emulate the makeShader after-hook with the actual native input/output.
                owners.propagate(bitmap,shader);
            }
            for(int n=0;n<100;n++)for(int i:new int[]{2,0,1,0,2}){
                String song=owners.song(HostCoverBridge.painterSource(painters[i]));
                if(!("song-"+i+".m4a").equals(song))throw new AssertionError("Native shader changed owner: "+song);
            }
            System.out.println("PASS: real Salt Player MipmapCoverPainter retains exact decoder bitmap -> native shader -> song provenance through reordered transitions");
        }finally{
            for(Object painter:painters)if(painter instanceof AutoCloseable c)c.close();
            for(Bitmap bitmap:bitmaps)if(bitmap!=null)bitmap.close();
        }
    }
}
