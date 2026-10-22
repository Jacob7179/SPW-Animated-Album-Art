@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class, androidx.compose.runtime.InternalComposeApi::class)
package com.spw.animated.album.art

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.IntSize
import com.xuncorp.spw.workshop.api.*
import com.xuncorp.spw.workshop.api.hook.*
import kotlinx.coroutines.*
import java.nio.file.*
import java.nio.file.Path
import androidx.compose.ui.graphics.Path as DrawPath
import kotlin.math.roundToInt

class MainPlugin(context:PluginContext):SpwPlugin(context){
    private val handles=mutableListOf<HookHandle>()
    @Volatile private var enabled=false
    private var settings:com.xuncorp.spw.workshop.api.config.ConfigHelper?=null
    @Volatile private var pauseLoops=true
    private lateinit var ffmpeg:Path
    private var online:OnlineArtwork?=null
    private var onlineMonitor:CoroutineScope?=null
    private data class Source(val song:String?=null,val path:Path?=null,
        val names:List<String> = listOf("square_animated_artwork.mp4","","","",""),val reflection:Boolean=true)
    private class Stream(val decoder:NativeVideo,val frame:MutableState<NativeVideo.Frame?>,var users:Int)
    private data class Session(val source:Source,val fallback:Stream?,val bindings:Bindings)
    private class Slot(val song:String,var path:Path?=null,var stream:Stream?=null,var resolved:Boolean=false,var seen:Long=0,var videoDrawn:Boolean=false)
    private val origins=CoverOrigins()
    private var bootstrap:NativeCoverBootstrap?=null
    private val painterBindings=java.util.Collections.synchronizedMap(java.util.IdentityHashMap<Any,Bindings>())
    private inner class Bindings {
        var session:Session?=null
        val slots=java.util.IdentityHashMap<Any,Slot>()
        var logged=false
        var frameLogged=false
        fun close(){
            slots.forEach{(painter,slot)->
                if(painterBindings[painter]===this)painterBindings.remove(painter)
                slot.path?.let{path->slot.stream?.let{release(path,it)}}
            }
            slots.clear();session=null
        }
        fun prune(){
            val cutoff=System.nanoTime()-3_000_000_000L
            val iterator=slots.entries.iterator()
            while(iterator.hasNext()){
                val entry=iterator.next();val slot=entry.value
                if(slot.seen<cutoff){
                    if(painterBindings[entry.key]===this)painterBindings.remove(entry.key)
                    slot.path?.let{path->slot.stream?.let{release(path,it)}};iterator.remove()
                }
            }
        }
    }
    private val paintingContext=ThreadLocal<Session?>()
    private val streams=mutableMapOf<Path,Stream>()
    @Synchronized private fun acquire(path:Path):Stream {
        streams[path]?.let{it.users++;return it}
        val state=mutableStateOf<NativeVideo.Frame?>(null)
        val decoder=NativeVideo(ffmpeg,path,{state.value=it},::diagnostic)
        decoder.setLoopPause(pauseLoops)
        val stream=Stream(decoder,state,1);streams[path]=stream;decoder.start();return stream
    }
    @Synchronized private fun release(path:Path,stream:Stream){
        if(streams[path]!==stream)return
        if(--stream.users<=0){streams.remove(path);stream.decoder.close()}
    }
    @Synchronized private fun updateLoopPause(value:Boolean){
        if(pauseLoops==value)return
        pauseLoops=value
        streams.values.forEach{it.decoder.setLoopPause(value)}
    }
    private fun diagnostic(message:String){
        PluginLog.write(pluginContext.pluginPath,message)
    }
    override fun start(){
        diagnostic("Persistent user log: ${PluginLog.userPath()}")
        diagnostic("Starting plugin on Salt Player ${pluginContext.spwVersion}; compatibility determined by native API features")
        check(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM)&&WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)){"Grant Class transform and Library read"}
        var root=Paths.get(pluginContext.pluginPath);if(!Files.isDirectory(root))root=root.parent
        ffmpeg=runCatching{DecoderExecutable.resolve(root)}.onFailure{diagnostic("FFmpeg discovery failed: ${it.stackTraceToString()}")}.getOrThrow()
        val coverTarget=runCatching{HostCoverTarget.resolve(javaClass.classLoader)}.onFailure{diagnostic("Cover target discovery failed: ${it.stackTraceToString()}")}.getOrThrow()
        val nativeMethods=runCatching{HostMethods.resolve()}.onFailure{diagnostic("Native API discovery failed: ${it.stackTraceToString()}")}.getOrThrow()
        diagnostic("Resolved native methods: $nativeMethods")
        diagnostic("Native cover target: ${coverTarget.owner()}.${coverTarget.method()} in ${coverTarget.caller()}; decoder=$ffmpeg")
        settings=WorkshopApi.manager.createConfigManager().getConfig()
        online=OnlineArtwork(OnlineArtwork.cacheDirectory(settings,root),ffmpeg,java.util.function.Consumer<String>{diagnostic(it)})
        bootstrap=NativeCoverBootstrap(origins,java.util.function.Supplier<String?>{WorkshopApi.playback.getCurrentMediaItem()?.path},java.util.function.Consumer<String>{diagnostic(it)})
        enabled=true;diagnostic("Starting native image-painter renderer: host owns small/large cover transitions")
        try{
            // Capture the actual decoder request, not a later playback-state snapshot.
            handles+=WorkshopApi.hookRegistrar.hookMethod(
                className=nativeMethods.readImage().declaringClass.name,methodName=nativeMethods.readImage().name,
                parameterTypes=nativeMethods.readImage().parameterTypes.map{it.name}
            ){
                after{call->
                    if(enabled&&call.throwable==null){
                        origins.record(HostCoverBridge.imageSource(call.result),call.args[0] as? String)
                    }
                }
            }
            // The host discards the bitmap after making its mipmap shader. Carry
            // provenance onto that shader before it is attached to a native painter.
            handles+=WorkshopApi.hookRegistrar.hookMethod(
                className=nativeMethods.makeShader().declaringClass.name,methodName=nativeMethods.makeShader().name,
                parameterTypes=nativeMethods.makeShader().parameterTypes.map{it.name}
            ){
                after{call->if(enabled&&call.throwable==null)origins.propagate(call.thisObject,call.result)}
            }
            handles+=WorkshopApi.hookRegistrar.replaceComposable(
                className=coverTarget.owner(),methodName=coverTarget.method(),inClass=coverTarget.caller(),
                parameterTypes=listOf("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier")
            ){call->CoverScope(call)}
            handles+=WorkshopApi.hookRegistrar.hookMethod(
                className=nativeMethods.painterDraw().declaringClass.name,methodName=nativeMethods.painterDraw().name,
                parameterTypes=nativeMethods.painterDraw().parameterTypes.map{it.name}
            ){
                before{call->
                    val painter=call.thisObject
                    val context=painterBindings[painter]?.session
                    if(enabled&&context!=null&&painter!=null){
                        val binding=context.bindings
                        if(!binding.logged){binding.logged=true;diagnostic("Native cover painter draw intercepted")}
                        var slot=binding.slots[painter]
                        if(slot==null)return@before
                        slot.seen=System.nanoTime()
                        val source=context.source
                        // Resolve the painter's own song, even if playback already moved on.
                        val ownPath=if(CoverOrigins.sameSong(slot.song,source.song))source.path else if(slot.resolved)slot.path else SongVideoPath.find(slot.song,source.names)
                        if(!slot.resolved||slot.path!=ownPath){
                            slot.path?.let{path->slot.stream?.let{release(path,it)}}
                            slot.path=ownPath;slot.stream=ownPath?.let{acquire(it)};slot.resolved=true
                            diagnostic("Painter source bound: ${slot.song} -> ${slot.path}")
                        }
                        // Never borrow another song's frame while this decoder warms up.
                        val frame=slot.stream?.frame?.value
                        if(frame!=null){
                            if(!slot.videoDrawn){slot.videoDrawn=true;diagnostic("Video frame drawn inside native painter: ${slot.song}")}
                            (call.args[0] as DrawScope).drawNativeVideoFrame(frame.bitmap,call.args[2] as Float,call.args[3] as ColorFilter?)
                            call.setResult(null)
                        }
                    }
                }
            }
            diagnostic("Native image painter registered; no video overlay or bounding-box redraw")
            // Fetching must not depend on the full-screen cover composable being mounted.
            onlineMonitor=CoroutineScope(SupervisorJob()+Dispatchers.IO).also{scope->scope.launch{
                var previous=""
                while(isActive&&enabled){
                    runCatching{
                        val config=settings;config?.reload()
                        val allow=config?.get("online_artwork",true)?:true
                        online?.setEnabled(allow)
                        val media=WorkshopApi.playback.getCurrentMediaItem()
                        val names=(1..5).map{index->config?.get("video_filename_$index",if(index==1)"square_animated_artwork.mp4" else "")?:""}
                        val local=media?.path?.let{SongVideoPath.find(it,names)}
                        val artist=if(media?.albumArtist.isNullOrBlank())media?.artist else media?.albumArtist
                        val status="Online artwork monitor: enabled=$allow; song=${media?.path}; artist=$artist; album=${media?.album}; local=$local"
                        if(status!=previous){previous=status;diagnostic(status)}
                        if(allow&&local==null)online?.lookup(artist,media?.album)
                    }.onFailure{diagnostic("Online artwork monitor failed: $it")}
                    delay(1000)
                }
            }}
        }catch(error:Throwable){diagnostic("Plugin startup/hooks failed: ${error.stackTraceToString()}");enabled=false;onlineMonitor?.cancel();onlineMonitor=null;bootstrap?.close();bootstrap=null;online?.close();online=null;handles.forEach{it.unhook()};handles.clear();throw error}
    }
    @Composable private fun CoverScope(call:UiHookCall){
        val source by produceState(Source()){
            var last:String?=null
            while(isActive&&enabled){
                value=withContext(Dispatchers.IO){runCatching{
                    val media=WorkshopApi.playback.getCurrentMediaItem()
                    val song=media?.path
                    val config=settings;config?.reload()
                    updateLoopPause(config?.get("loop_pause",true)?:true)
                    val names=(1..5).map{index->config?.get("video_filename_$index",if(index==1)"square_animated_artwork.mp4" else "") ?: if(index==1)"square_animated_artwork.mp4" else ""}
                    val allowOnline=config?.get("online_artwork",true)?:true
                    online?.setEnabled(allowOnline)
                    val local=song?.let{SongVideoPath.find(it,names)}
                    val artist=if(media?.albumArtist.isNullOrBlank())media?.artist else media?.albumArtist
                    val path=local ?: if(allowOnline)online?.lookup(artist,media?.album) else null
                    val status="song=$song; video=$path"
                    if(last!=status){last=status;diagnostic(status)}
                    Source(song,path,names,config?.get("video_reflection",true)?:true)
                }.onFailure{diagnostic("Lookup failed: $it")}.getOrDefault(Source())}
                delay(100)
            }
        }
        // Prewarm the current decoder; painters only render their own streams.
        var displayed by remember{mutableStateOf<Pair<Path,Stream>?>(null)}
        LaunchedEffect(source.path){
            val path=source.path
            if(path==null){displayed?.let{release(it.first,it.second)};displayed=null}
            else{
                val next=acquire(path);var adopted=false
                try{
                    while(enabled&&next.frame.value==null)delay(16)
                    if(enabled){val previous=displayed;displayed=path to next;adopted=true;previous?.let{release(it.first,it.second)}}
                }finally{if(!adopted)release(path,next)}
            }
        }
        DisposableEffect(Unit){onDispose{displayed?.let{release(it.first,it.second)};displayed=null}}
        val bindings=remember{Bindings()}
        DisposableEffect(bindings){onDispose{bindings.close()}}
        val session=remember(source,displayed){Session(source,displayed?.second,bindings)}
        bindings.session=session
        val original=call.argument<Modifier>(4)
        HostCoverBridge.draw(call.arguments,original.composed{NativePaintingContext(session)},currentComposer)
    }
    @Composable private fun NativePaintingContext(session:Session):Modifier {
        val presented=remember{arrayOfNulls<NativeVideo.Frame>(1)}
        var coordinates by remember{mutableStateOf<LayoutCoordinates?>(null)}
        return Modifier.onGloballyPositioned{
            coordinates=it;registerPainters(it,session)
        }.drawWithContent{
            val previous=paintingContext.get()
            paintingContext.set(session)
            try{
                // Native graphics layers may cache the initial still. Observe video
                // frames here and invalidate only the cover's image layer caches.
                val videoFrame=session.fallback?.frame?.value
                val root=coordinates
                if(root!=null&&root.isAttached)registerPainters(root,session)
                if(videoFrame!=null&&videoFrame!==presented[0]&&root!=null&&root.isAttached){
                    if(!session.bindings.frameLogged){session.bindings.frameLogged=true;diagnostic("Root observed live video frame; refreshing image layers")}
                    presented[0]=videoFrame;HostCoverBridge.invalidateImages(root)
                }
                val image=root?.let{HostCoverBridge.imageCoordinates(it)}
                val hideReflection=!session.source.reflection&&(session.source.path!=null||session.fallback!=null)
                if(hideReflection&&root!=null&&image!=null&&root.isAttached&&image.isAttached){
                    val bounds=root.localBoundingBoxOf(image,false)
                    drawContext.canvas.save()
                    try{drawContext.canvas.clipRect(0f,0f,size.width,bounds.bottom);drawContent()}
                    finally{drawContext.canvas.restore()}
                }else drawContent()
            }finally{paintingContext.set(previous);session.bindings.prune()}
        }
    }
    private fun registerPainters(root:LayoutCoordinates,session:Session){
        for(painter in HostCoverBridge.painters(root)){
            // A native painter can move between the small and expanded cover roots.
            // Transfer its existing slot rather than rebinding it to current playback.
            val previous=painterBindings[painter]
            var slot=session.bindings.slots[painter]
            if(slot==null){
                slot=previous?.slots?.remove(painter)
                if(slot==null){
                    val song=origins.song(HostCoverBridge.painterSource(painter))
                    // Unknown/ambiguous provenance stays native; never guess current song.
                    if(song==null){
                        if(session.source.path!=null)bootstrap?.offer(painter,session.source.song)
                        continue
                    }
                    slot=Slot(song)
                }
                session.bindings.slots[painter]=slot
            }
            painterBindings[painter]=session.bindings
            slot.seen=System.nanoTime()
        }
    }
    @Synchronized override fun stop(){
        enabled=false;onlineMonitor?.cancel();onlineMonitor=null;bootstrap?.close();bootstrap=null;online?.close();online=null;handles.reversed().forEach{it.unhook()};handles.clear()
        streams.values.forEach{it.decoder.close()};streams.clear();painterBindings.clear();origins.clear();settings=null
    }
}

/** Native cover layers own clipping and corner shape; only replace the pixels. */
internal fun DrawScope.drawNativeVideoFrame(bitmap:ImageBitmap,alpha:Float=1f,colorFilter:ColorFilter?=null){
    drawImage(bitmap,alpha=alpha,colorFilter=colorFilter,
        dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.High)
}
/** Mirror the live frame below the cover and fade it out in an isolated layer. */
internal fun DrawScope.drawVideoReflection(bitmap:ImageBitmap,width:Float,height:Float,radius:Float,gap:Float) {
    val top=height+gap
    val reflectionHeight=height*.45f
    val bounds=Rect(0f,top,width,top+reflectionHeight)
    val canvas=drawContext.canvas
    canvas.save()
    try {
        canvas.clipRect(bounds)
        canvas.saveLayer(bounds,Paint().apply{alpha=.24f})
        try {
            canvas.save()
            try {
                canvas.translate(0f,2f*height+gap)
                canvas.scale(1f,-1f)
                val clip=DrawPath().apply{addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(0f,0f,width,height),androidx.compose.ui.geometry.CornerRadius(radius)))}
                canvas.clipPath(clip,ClipOp.Intersect)
                drawImage(bitmap,dstSize=IntSize(width.roundToInt(),height.roundToInt()),filterQuality=FilterQuality.High)
            } finally {canvas.restore()}
            drawRect(brush=Brush.verticalGradient(listOf(Color.Black,Color.Transparent),startY=top,endY=top+reflectionHeight),
                topLeft=Offset(0f,top),size=Size(width,reflectionHeight),blendMode=BlendMode.DstIn)
        } finally {canvas.restore()}
    } finally {canvas.restore()}
}













