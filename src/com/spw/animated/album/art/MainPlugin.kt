@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class, androidx.compose.runtime.InternalComposeApi::class)
package com.spw.animated.album.art

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xuncorp.spw.workshop.api.*
import com.xuncorp.spw.workshop.api.hook.*
import kotlinx.coroutines.*
import java.nio.file.*
import java.nio.file.Path
import androidx.compose.ui.graphics.Path as DrawPath
import kotlin.math.roundToInt

class MainPlugin(context:PluginContext):SpwPlugin(context){
    private var handle:HookHandle?=null
    @Volatile private var enabled=false
    private var settings:com.xuncorp.spw.workshop.api.config.ConfigHelper?=null
    private lateinit var ffmpeg:Path
    private data class Source(val path:Path?)
    private class Stream(val decoder:NativeVideo,val frame:MutableState<NativeVideo.Frame?>,var users:Int)
    private val streams=mutableMapOf<Path,Stream>()
    @Synchronized private fun acquire(path:Path):Stream {
        streams[path]?.let{it.users++;return it}
        val state=mutableStateOf<NativeVideo.Frame?>(null)
        val decoder=NativeVideo(ffmpeg,path,{state.value=it},::diagnostic)
        val stream=Stream(decoder,state,1);streams[path]=stream;decoder.start();return stream
    }
    @Synchronized private fun release(path:Path,stream:Stream){
        if(streams[path]!==stream)return
        if(--stream.users<=0){streams.remove(path);stream.decoder.close()}
    }
    private fun diagnostic(message:String){
        System.err.println("Animated Cover: $message")
        runCatching{
            var root=Paths.get(pluginContext.pluginPath);if(!Files.isDirectory(root))root=root.parent
            Files.writeString(root.resolve("animated-cover.log"),"${java.time.Instant.now()} $message\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND)
        }
    }
    override fun start(){
        check(pluginContext.spwVersion=="1.19.0"){"Requires Salt Player 1.19.0"}
        check(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)&&WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)){"Grant Class transform and Library read"}
        var root=Paths.get(pluginContext.pluginPath);if(!Files.isDirectory(root))root=root.parent
        ffmpeg=root.resolve("bin/ffmpeg.exe");check(Files.isRegularFile(ffmpeg)){"Missing bundled MP4 decoder"}
        settings=WorkshopApi.manager.createConfigManager().getConfig()
        enabled=true;diagnostic("Starting 0.2.0: native cover-canvas replacement; no Swing/JavaFX surface")
        try{
            handle=WorkshopApi.hookRegistrar.replaceComposable(
                className="androidx.compose.ui.\u05d0",methodName="\u037f",inClass="androidx.compose.ui.\u05d7",
                parameterTypes=listOf("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier")
            ){call->
                val original=call.argument<Modifier>(4)
                HostCoverBridge.draw(call.arguments,if(enabled)original.composed{NativeCover()}else original,currentComposer)
            }
            diagnostic("Native cover renderer registered")
        }catch(error:Throwable){enabled=false;throw error}
    }
    @Composable private fun NativeCover():Modifier {
        val source by produceState(Source(null)){
            var last:String?=null
            while(isActive&&enabled){
                value=withContext(Dispatchers.IO){runCatching{
                    val song=WorkshopApi.playback.getCurrentMediaItem()?.path
                    val config=settings
                    config?.reload()
                    val names=(1..5).map { index ->
                        config?.get("video_filename_$index",if(index==1)"square_animated_artwork.mp4" else "")
                            ?: if(index==1)"square_animated_artwork.mp4" else ""
                    }
                    val path=song?.let { SongVideoPath.find(it,names) }
                    val status="song=$song; video=$path"
                    if(last!=status){last=status;diagnostic(status)}
                    Source(path)
                }.onFailure{diagnostic("Lookup failed: $it")}.getOrDefault(Source(null))}
                delay(100)
            }
        }
        val path=source.path?:return Modifier
        // Keep the outgoing decoder alive until its replacement has a frame.
        // Native cover movement/opacity can then animate video instead of a still.
        var displayed by remember{mutableStateOf<Pair<Path,Stream>?>(null)}
        LaunchedEffect(path) {
            val next=acquire(path)
            var adopted=false
            try {
                while(enabled && next.frame.value==null)delay(16)
                if(enabled){
                    val previous=displayed
                    displayed=path to next
                    adopted=true
                    previous?.let{release(it.first,it.second)}
                }
            } finally {
                if(!adopted)release(path,next)
            }
        }
        DisposableEffect(Unit){onDispose{
            displayed?.let{release(it.first,it.second)}
            displayed=null
        }}
        var root by remember{mutableStateOf<LayoutCoordinates?>(null)}
        val invisible=remember{Paint().apply{alpha=0f}}
        return Modifier.onGloballyPositioned{position->
            root=position
        }.drawWithContent{
            val frame=displayed?.second?.frame?.value
            val r=root
            if(!enabled||frame==null||r==null||!r.isAttached){drawContent();return@drawWithContent}
            // Run native geometry/transition drawing into a fully transparent buffer.
            // No native cover pixels are retained, and no separate surface is mounted.
            drawContext.canvas.saveLayer(Rect(0f,0f,size.width,size.height),invisible)
            try{drawContent()}finally{drawContext.canvas.restore()}
            // A cover can contain reflection/transition painters as well. Select
            // one primary image; never draw a second video over the same cover.
            val i=HostCoverBridge.imageCoordinates(r)
            if(i==null||!i.isAttached){drawContent();return@drawWithContent}
            val rect=Rect(0f,0f,i.size.width.toFloat(),i.size.height.toFloat())
            if(rect.width<=1f||rect.height<=1f)return@drawWithContent
            val alpha=HostCoverBridge.transitionAlpha(i,r)
            if(alpha<=0f)return@drawWithContent
            val transform=Matrix()
            r.transformFrom(i,transform)
            drawContext.canvas.save()
            try {
                drawContext.canvas.concat(transform)
                drawContext.canvas.saveLayer(rect,Paint().apply{this.alpha=alpha})
                try {
                    // Small previews use the tighter corners of the native artwork.
                    val shortSide=minOf(rect.width,rect.height)
                    val corner=if(shortSide<=96.dp.toPx())2.5.dp else 8.5.dp
                    val radius=minOf(corner.toPx(),shortSide/2f)
                    val clip=DrawPath().apply{addRoundRect(androidx.compose.ui.geometry.RoundRect(rect,androidx.compose.ui.geometry.CornerRadius(radius)))}
                    drawContext.canvas.clipPath(clip,ClipOp.Intersect)
                    drawImage(frame.bitmap,dstSize=IntSize(rect.width.roundToInt(),rect.height.roundToInt()),filterQuality=FilterQuality.High)
                } finally {drawContext.canvas.restore()}
            } finally {drawContext.canvas.restore()}
        }
    }

    @Synchronized override fun stop(){
        enabled=false;handle?.unhook();handle=null
        streams.values.forEach{it.decoder.close()};streams.clear();settings=null
    }
}
