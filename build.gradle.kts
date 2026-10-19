import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.xuncorp.spw.workshop.gradle.PluginPermission
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    id("com.xuncorp.spw.workshop") version "0.1.0-dev22"
}

group = "com.spw.animated.album.art"
version = "0.3.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    sourceSets.named("main") { kotlin.setSrcDirs(listOf("src")) }
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Use the exact Salt Player runtime for host-specific Compose and Skia calls.
val saltPlayerArchive = providers.gradleProperty("saltPlayerArchive").orElse(
    "C:/Program Files (x86)/Steam/steamapps/common/Salt Player for Windows/app/ffmpeg-x64.dll"
)
val hostBytecode = layout.buildDirectory.file("host-compile/salt-player-host.jar")
val prepareHostBytecode = tasks.register<Copy>("prepareHostBytecode") {
    from(saltPlayerArchive)
    into(layout.buildDirectory.dir("host-compile"))
    rename { "salt-player-host.jar" }
    doFirst {
        require(file(saltPlayerArchive.get()).isFile) {
            "Host archive missing. Set -PsaltPlayerArchive=C:/path/to/app/ffmpeg-x64.dll"
        }
    }
}
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(files(hostBytecode).builtBy(prepareHostBytecode))
}
sourceSets.named("main") {
    java.setSrcDirs(listOf("src"))
}




// 插件元数据配置
spmod {
    PluginClass = "com.spw.animated.album.art.MainPlugin"
    PluginId = "com.spw.animated.album.art"
    PluginName = "SPW Animated Album Art"
    PluginDescription = "Native cover-canvas MP4 replacement"
    PluginVersion = "0.3.0"
    PluginProvider = "Jacob7179"
    PluginHasConfig = true
    PluginPermissions = listOf(PluginPermission.CLASS_TRANSFORM, PluginPermission.LIBRARY_READ)
    PluginOpenSourceUrl = "https://github.com/Jacob7179/SPW-Animated-Album-Art"
}


// NativeVideo opens pluginPath/bin/ffmpeg.exe, outside the classes directory.
tasks.named("plugin") {
    inputs.dir("bin")
    inputs.dir("licenses")
    doFirst {
        require(file("bin/ffmpeg.exe").isFile) { "Missing bin/ffmpeg.exe" }
    }
    doLast {
        val packages = fileTree(layout.buildDirectory.dir("libs")) { include("*.spmod") }
        require(!packages.isEmpty) { "Workshop did not produce a .spmod" }
        packages.forEach { archive ->
            val assets = listOf("bin", "licenses").flatMap { folder ->
                file(folder).walkTopDown().filter { it.isFile }.toList()
            }.associateBy { it.relativeTo(projectDir).invariantSeparatorsPath }
            val temporary = archive.resolveSibling(archive.name + ".tmp")
            ZipFile(archive).use { input ->
                ZipOutputStream(temporary.outputStream().buffered()).use { output ->
                    input.entries().asSequence().filter { it.name !in assets }.forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                    assets.forEach { (name, asset) ->
                        output.putNextEntry(ZipEntry(name))
                        asset.inputStream().use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
            }
            Files.move(temporary.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}