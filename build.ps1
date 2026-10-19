param([Parameter(Mandatory=$true)][string]$JdkHome,
 [string]$SaltPlayerApp='C:\Program Files (x86)\Steam\steamapps\common\Salt Player for Windows\app',
 [string]$KotlinCache="$env:USERPROFILE\.gradle\caches\modules-2\files-2.1")
$ErrorActionPreference='Stop'
$hostArchive=Join-Path $SaltPlayerApp 'ffmpeg-x64.dll'
$compiler=(Get-ChildItem "$KotlinCache/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.4.20" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$compose=(Get-ChildItem "$KotlinCache/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.20" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$annotations=(Get-ChildItem "$KotlinCache/org.jetbrains/annotations" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
if (!$compiler -or !$compose -or !$annotations) { throw 'Kotlin 2.4.20 compiler, Compose compiler, and JetBrains annotations must be available in KotlinCache.' }
$stage=Join-Path $PSScriptRoot ('build/stage-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $stage 'classes'
New-Item -ItemType Directory -Force "$classes/META-INF","$stage/bin","$stage/licenses" | Out-Null
& "$JdkHome/bin/javac.exe" -proc:none --release 25 -encoding UTF-8 -classpath $hostArchive -d $classes "$PSScriptRoot/src/com/spw/animated/album/art/NativeVideo.java" "$PSScriptRoot/src/com/spw/animated/album/art/SongVideoPath.java" "$PSScriptRoot/src/com/spw/animated/album/art/HostCoverBridge.java" "$PSScriptRoot/src/com/spw/animated/album/art/OnlineArtwork.java" "$PSScriptRoot/src/com/spw/animated/album/art/CoverOrigins.java"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
& "$JdkHome/bin/java.exe" -cp "$compiler;$hostArchive;$annotations" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 25 "-Xplugin=$compose" -classpath "$hostArchive;$classes" -d $classes "$PSScriptRoot/src/com/spw/animated/album/art/MainPlugin.kt"
if($LASTEXITCODE -ne 0){throw 'Kotlin compilation failed'}
$manifest=@'
Manifest-Version: 1.0
Plugin-Class: com.spw.animated.album.art.MainPlugin
Plugin-Id: com.spw.animated.album.art
Plugin-Version: 0.4.2
Plugin-Provider: Jacob
Plugin-Name: Animated Cover Video
Plugin-Description: Native cover-canvas MP4 replacement
Plugin-Has-Config: true
Plugin-Permissions: class-transform,library-read
'@
[IO.File]::WriteAllText("$classes/META-INF/MANIFEST.MF",($manifest.TrimEnd()+"`r`n`r`n"),[Text.UTF8Encoding]::new($false))
Copy-Item "$PSScriptRoot/bin/ffmpeg.exe" "$stage/bin/ffmpeg.exe"
Copy-Item "$PSScriptRoot/licenses/FFmpeg-*" "$stage/licenses/"
Copy-Item "$PSScriptRoot/README.md" "$stage/README.md"
Copy-Item "$PSScriptRoot/src/main/resources/*" $classes
Add-Type -AssemblyName System.IO.Compression.FileSystem
$package=Join-Path $PSScriptRoot 'AnimatedCover-0.4.2.spmod'
if(Test-Path -LiteralPath $package){Remove-Item -LiteralPath $package}
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$package,[IO.Compression.CompressionLevel]::Optimal,$false)
Write-Host "Built: $package"


