package com.spw.animated.album.art;
import org.jetbrains.skia.*;
import java.util.Arrays;
public class NativeCoverBootstrapTest {
    static Bitmap pattern(int seed){
        Bitmap bitmap=new Bitmap();byte[] pixels=new byte[128*128*4];
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){int i=(y*128+x)*4;pixels[i]=(byte)(x+seed);pixels[i+1]=(byte)y;pixels[i+2]=(byte)(x^y^seed);pixels[i+3]=(byte)255;}
        bitmap.installPixels(ImageInfo.Companion.makeN32Premul(128,128),pixels,512);return bitmap;
    }
    public static void main(String[] args)throws Exception{
        try(Bitmap first=pattern(3);Bitmap same=pattern(3);Bitmap different=pattern(44);Paint retained=new Paint()){
            try(Shader shader=NativeCoverBootstrap.shader(first)){retained.setShader(shader);}
            // The original shader wrapper has closed, as it can when a cover exits.
            int[] original=NativeCoverBootstrap.sample(retained,128,128);
            try(Shader expected=NativeCoverBootstrap.shader(same);Paint paint=new Paint()){
                paint.setShader(expected);
                if(!Arrays.equals(original,NativeCoverBootstrap.sample(paint,128,128)))throw new AssertionError("Matching cover rejected");
            }
            try(Shader expected=NativeCoverBootstrap.shader(different);Paint paint=new Paint()){
                paint.setShader(expected);
                if(Arrays.equals(original,NativeCoverBootstrap.sample(paint,128,128)))throw new AssertionError("Wrong cover accepted");
            }
            if(Arrays.stream(original).distinct().count()<16)throw new AssertionError("Test sample unexpectedly solid");
        }
        System.out.println("PASS: read-only native cover comparison accepts matching pixels, rejects another cover, and retains shader safely after original wrapper closes");
    }
}
