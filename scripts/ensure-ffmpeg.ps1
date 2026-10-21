param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath ($ProjectRoot.Trim().Trim('"'))).Path
$bin = Join-Path $ProjectRoot 'bin'
$exe = Join-Path $bin 'ffmpeg.exe'

function Test-Ffmpeg([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $process = $null
    try {
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $Path
        $info.Arguments = '-version'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $process = [System.Diagnostics.Process]::Start($info)
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(10000)) { $process.Kill(); return $false }
        return ($process.ExitCode -eq 0 -and $stdout.Result -match '^ffmpeg version ')
    } catch { return $false }
    finally { if ($process) { $process.Dispose() } }
}

function Install-Ffmpeg([string]$Source) {
    if (!(Test-Ffmpeg $Source)) { return $false }
    # Stage and test the copied executable, including adjacent shared DLLs if needed.
    $stage = Join-Path $ProjectRoot ('.ffmpeg-stage-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $stage | Out-Null
        $stagedExe = Join-Path $stage 'ffmpeg.exe'
        Copy-Item -LiteralPath $Source -Destination $stagedExe
        Get-ChildItem -LiteralPath (Split-Path -Parent $Source) -Filter '*.dll' -File |
            Copy-Item -Destination $stage
        if (!(Test-Ffmpeg $stagedExe)) { return $false }
        New-Item -ItemType Directory -Path $bin -Force | Out-Null
        Get-ChildItem -LiteralPath $stage -File | Copy-Item -Destination $bin -Force
        if (!(Test-Ffmpeg $exe)) { throw 'Copied FFmpeg failed verification.' }
        Write-Host "Using existing FFmpeg: $Source"
        Write-Host "Packaged copy: $exe"
        return $true
    } finally {
        # $stage is a freshly created, fixed-prefix child of the verified project root.
        if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    }
}

try {
    if (Test-Ffmpeg $exe) {
        Write-Host "Using project FFmpeg: $exe"
        exit 0
    }
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($value in @($env:FFMPEG_PATH, $env:IMAGEIO_FFMPEG_EXE)) {
        if (![string]::IsNullOrWhiteSpace($value)) {
            $value = $value.Trim().Trim('"')
            if (Test-Path -LiteralPath $value -PathType Container) { $value = Join-Path $value 'ffmpeg.exe' }
            $candidates.Add($value)
        }
    }
    $candidates.Add((Join-Path $ProjectRoot 'ffmpeg.exe'))
    Get-Command ffmpeg.exe -CommandType Application -All -ErrorAction SilentlyContinue |
        ForEach-Object { $candidates.Add($_.Source) }
    foreach ($base in @('C:\ffmpeg', "$env:ProgramFiles\ffmpeg", "${env:ProgramFiles(x86)}\ffmpeg",
            "$env:USERPROFILE\scoop\apps\ffmpeg\current", "$env:USERPROFILE\scoop\apps\ffmpeg-essentials\current")) {
        $candidates.Add((Join-Path $base 'bin\ffmpeg.exe'))
        $candidates.Add((Join-Path $base 'ffmpeg.exe'))
    }
    foreach ($path in @("$env:ProgramData\chocolatey\bin\ffmpeg.exe", "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffmpeg.exe")) {
        $candidates.Add($path)
    }
    $wingetPackages = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (Test-Path -LiteralPath $wingetPackages) {
        Get-ChildItem -LiteralPath $wingetPackages -Directory -Filter '*ffmpeg*' |
            ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter ffmpeg.exe -File -Recurse -ErrorAction SilentlyContinue } |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if ([IO.Path]::GetFullPath($candidate) -eq $exe) { continue }
        if (Install-Ffmpeg $candidate) { exit 0 }
    }

    $work = Join-Path $ProjectRoot ('.ffmpeg-download-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $work | Out-Null
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        # GitHub first. These are independent Windows x64 builds; the final
        # source is the original Gyan download endpoint.
        $sources = @(
            @{ Name = 'GitHub (BtbN, GPL static)'; Url = 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip' },
            @{ Name = 'Gyan official (release essentials)'; Url = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' }
        )
        $installed = $false
        foreach ($source in $sources) {
            $attempt = Join-Path $work ([guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $attempt | Out-Null
            $archive = Join-Path $attempt 'ffmpeg.zip'
            $unpacked = Join-Path $attempt 'unpacked'
            Write-Host ('Trying ' + $source.Name + ': ' + $source.Url)
            try {
                Invoke-WebRequest -Uri $source.Url -OutFile $archive -UseBasicParsing -TimeoutSec 120 -MaximumRedirection 10
                # Reject tiny HTML error pages and broken ZIP downloads.
                if ((Get-Item -LiteralPath $archive).Length -lt 1048576) { throw 'Downloaded file is unexpectedly small.' }
                Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -ErrorAction Stop
                $found = Get-ChildItem -LiteralPath $unpacked -Filter ffmpeg.exe -File -Recurse |
                    Where-Object { $_.FullName -match '(?i)[\/]bin[\/]ffmpeg\.exe$' } |
                    Select-Object -First 1
                if (!$found) { $found = Get-ChildItem -LiteralPath $unpacked -Filter ffmpeg.exe -File -Recurse | Select-Object -First 1 }
                if (!$found -or !(Install-Ffmpeg $found.FullName)) { throw 'Downloaded archive has no usable FFmpeg.' }
                $installed = $true
                Write-Host ('FFmpeg installed successfully from ' + $source.Name)
                break
            } catch {
                Write-Warning ('FFmpeg source failed (' + $source.Name + '): ' + $_.Exception.Message)
            } finally {
                if (Test-Path -LiteralPath $attempt) { Remove-Item -LiteralPath $attempt -Recurse -Force }
            }
        }
        if (!$installed) { throw 'All Windows FFmpeg download sources failed. You can also install FFmpeg with winget, then rerun build.bat.' }
    } finally {
        if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
    }
    exit 0
} catch {
    Write-Host "[ERROR] FFmpeg setup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
