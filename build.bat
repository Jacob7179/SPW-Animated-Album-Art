@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if not exist "gradlew.bat" goto missing
if not exist "build.gradle.kts" goto missing

echo.
echo ===== SPW Animated Album Art Workshop Build =====
echo Project: "%CD%"
echo.
echo Checking project, PATH and installed FFmpeg; downloading only if needed...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\ensure-ffmpeg.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto ffmpegfail
echo Checking Linux x64 FFmpeg for cross-platform .spmod packaging...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\ensure-linux-ffmpeg.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto ffmpegfail
set "PATH=%CD%\bin;%PATH%"
echo.
echo Checking JDK 25; downloading it automatically if needed...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\ensure-jdk25.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto javafail

set "JDK_PATH_FILE=%~dp0.jdk\current-jdk.txt"
if not exist "%JDK_PATH_FILE%" goto javafail
set /p "JAVA_HOME="<"%JDK_PATH_FILE%"
if not exist "%JAVA_HOME%\bin\java.exe" goto javafail
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using Java from "%JAVA_HOME%"
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 goto javafail

echo.
echo Removing previous build folder...
set "SPW_BUILD_PROJECT=%~dp0"
powershell.exe -NoProfile -Command "$ErrorActionPreference = 'Stop'; $root = (Resolve-Path -LiteralPath $env:SPW_BUILD_PROJECT).Path.TrimEnd('\'); $target = [IO.Path]::GetFullPath((Join-Path $root 'build')); if ([IO.Path]::GetDirectoryName($target) -ne $root) { throw 'Build folder is outside the project' }; if (Test-Path -LiteralPath $target) { $item = Get-Item -LiteralPath $target -Force; if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing to recursively remove a linked build folder' }; Remove-Item -LiteralPath $target -Recurse -Force }; if (Test-Path -LiteralPath $target) { throw 'Build folder could not be removed' }"
if errorlevel 1 goto cleanfail

echo.
echo Building .spmod...
call "%~dp0gradlew.bat" plugin --no-daemon
if errorlevel 1 goto gradlefail
if not exist "dist" mkdir "dist"
set "FOUND=0"
if exist "build" for /r "build" %%F in (*.spmod) do call :collect "%%~fF"
if "%FOUND%"=="0" goto missingoutput
echo.
echo [SUCCESS] .spmod copied into "%CD%\dist"
pause
exit /b 0

:collect
copy /y "%~1" "dist\" >nul
if not errorlevel 1 set "FOUND=1"
exit /b 0
:missing
echo [ERROR] Missing gradlew.bat or build.gradle.kts.
goto fail
:ffmpegfail
echo [ERROR] Could not find or prepare FFmpeg. Check the error above.
goto fail
:javafail
echo [ERROR] Could not prepare Java 25. Check the download error above.
goto fail
:cleanfail
echo [ERROR] Could not remove the build folder. Close programs using its files and try again.
goto fail
:gradlefail
echo [ERROR] Gradle build failed. See the error above.
goto fail
:missingoutput
echo [ERROR] Gradle completed, but no .spmod was found under build.
:fail
pause
exit /b 1
