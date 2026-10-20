package com.spw.animated.album.art;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;
public class PlatformCompatibilityTest {
    static byte[] cover(String owner,String method){
        return ClassFile.of().build(ClassDesc.of(owner),c->c.withMethodBody(method,MethodTypeDesc.ofDescriptor(HostCoverTarget.DESCRIPTOR),ClassFile.ACC_PUBLIC|ClassFile.ACC_STATIC,
            b->b.ldc(HostCoverTarget.COVER+"96)").pop().return_()));
    }
    static byte[] caller(String owner,String callee,String method,int calls){
        return ClassFile.of().build(ClassDesc.of(owner),c->c.withMethodBody("render",MethodTypeDesc.ofDescriptor(HostCoverTarget.DESCRIPTOR),ClassFile.ACC_PUBLIC|ClassFile.ACC_STATIC,b->{
            b.ldc(HostCoverTarget.CALLER+"13)").pop();
            for(int i=0;i<calls;i++)b.aload(0).iload(1).iload(2).iload(3).aload(4).aload(5).iload(6).iload(7)
                .invokestatic(ClassDesc.of(callee),method,MethodTypeDesc.ofDescriptor(HostCoverTarget.DESCRIPTOR));
            b.return_();
        }));
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(String tag:List.of("windows","linux","renamed")){
            String target="fixture."+tag+".Cover",method="different_"+tag,site="fixture."+tag+".Caller";
            var found=HostCoverTarget.inspectClasses(List.of(cover(target,method),caller(site,target,method,1)));
            check(found.owner().equals(target)&&found.method().equals(method)&&found.caller().equals(site),"Obfuscation-specific name leaked");
            try{HostCoverTarget.inspectClasses(List.of(cover(target,method),caller(site,target,method,2)));throw new AssertionError("Ambiguous call accepted");}
            catch(java.io.IOException expected){}
        }
        if(args.length>0){
            var nativeTarget=HostCoverTarget.inspect(Path.of(args[0]));
            check(nativeTarget.owner().equals("androidx.compose.ui.\u05d0")&&nativeTarget.caller().equals("androidx.compose.ui.\u05d7"),"Windows target changed");
            HostCoverTarget.resolve(PlatformCompatibilityTest.class.getClassLoader());
            System.out.println("PASS actual Windows host discovery and reflective native signature resolution");
        }
        Path root=Files.createTempDirectory("decoder-platform-");Path bin=Files.createDirectories(root.resolve("bin"));
        Path windows=Files.writeString(bin.resolve("ffmpeg.exe"),"fixture");
        check(DecoderExecutable.resolve(root,"Windows 11","",null).equals(windows),"Windows bundled decoder missing");
        try{DecoderExecutable.resolve(root,"Linux","",null);throw new AssertionError("Linux selected Windows ffmpeg.exe");}
        catch(IllegalStateException expected){}
        Path system=Files.createDirectories(root.resolve("system"));Path nativeBinary=Files.writeString(system.resolve("ffmpeg"),"fixture");nativeBinary.toFile().setExecutable(true);
        check(DecoderExecutable.resolve(root,"Linux",Path.of("").toAbsolutePath().relativize(system.toAbsolutePath()).toString(),null,p->true).equals(nativeBinary.toAbsolutePath()),"Linux PATH lookup failed");
        check(DecoderExecutable.resolve(root,"Linux","",nativeBinary.toString(),p->true).equals(nativeBinary.toAbsolutePath()),"Explicit native decoder path failed");
        try{DecoderExecutable.resolve(root,"Linux","",windows.toString(),p->true);throw new AssertionError("Linux override accepted .exe");}
        catch(IllegalStateException expected){}
        System.out.println("PASS renamed OS cover fixtures, ambiguous-call rejection, Windows decoder, Linux PATH/override and .exe rejection");
    }
}


