param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'

function Test-LinuxFfmpeg([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        if ($stream.Length -lt 100000) { return $false }
        $bytes = New-Object byte[] 5
        if ($stream.Read($bytes, 0, 5) -ne 5) { return $false }
        return ($bytes[0] -eq 127 -and $bytes[1] -eq 69 -and $bytes[2] -eq 76 -and $bytes[3] -eq 70 -and $bytes[4] -eq 2)
    } finally { $stream.Dispose() }
}

try {
    $ProjectRoot = (Resolve-Path -LiteralPath ($ProjectRoot.Trim().Trim('"'))).Path
    $bin = Join-Path $ProjectRoot 'bin'
    $target = Join-Path $bin 'ffmpeg'
    if (Test-LinuxFfmpeg $target) {
        Write-Host "Using project Linux FFmpeg: $target"
        exit 0
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $work = Join-Path $ProjectRoot ('.ffmpeg-linux-download-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    try {
        $sources = @(
            @{ Name = 'GitHub (Shaka static FFmpeg)'; Url = 'https://github.com/shaka-project/static-ffmpeg-binaries/releases/download/n8.0.3-1/ffmpeg-linux-x64'; Kind = 'binary'; Sha256 = '9eac5b2b5076db5ff853a6fa0dcd6b8de7d0cac8481eadda6c47cd935825f1ee' },
            @{ Name = 'npm registry (FFmpeg Linux x64)'; Url = 'https://registry.npmjs.org/@ffmpeg-installer/linux-x64/-/linux-x64-4.1.0.tgz'; Kind = 'tgz'; Sha256 = '' },
            @{ Name = 'John Van Sickle (original source)'; Url = 'https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz'; Kind = 'txz'; Sha256 = '' }
        )
        $success = $false
        foreach ($source in $sources) {
            Write-Host "Trying $($source.Name)..."
            Write-Host "Source: $($source.Url)"
            $download = Join-Path $work ('download.' + $source.Kind)
            $extract = Join-Path $work 'extract'
            try {
                if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
                if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download -Force }
                Invoke-WebRequest -Uri $source.Url -OutFile $download -UseBasicParsing -MaximumRedirection 8 -TimeoutSec 60 -Headers @{ 'User-Agent' = 'SPW-Animated-Album-Art-Builder' }
                if ($source.Sha256) {
                    $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
                    if ($actual -ne $source.Sha256) { throw "SHA256 mismatch (received $actual)" }
                }
                $candidate = $download
                if ($source.Kind -ne 'binary') {
                    $tarCommand = Get-Command tar.exe -ErrorAction SilentlyContinue
                    if (!$tarCommand) { throw 'Windows tar.exe is required to extract downloaded archive.' }
                    New-Item -ItemType Directory -Path $extract -Force | Out-Null
                    & $tarCommand.Source -xf $download -C $extract
                    if ($LASTEXITCODE -ne 0) { throw 'Failed to extract archive.' }
                    $found = Get-ChildItem -LiteralPath $extract -File -Filter ffmpeg -Recurse | Where-Object { Test-LinuxFfmpeg $_.FullName } | Select-Object -First 1
                    if (!$found) { throw 'No valid Linux x64 FFmpeg executable found in archive.' }
                    $candidate = $found.FullName
                }
                if (!(Test-LinuxFfmpeg $candidate)) { throw 'Downloaded file is not a valid ELF64 executable.' }
                New-Item -ItemType Directory -Path $bin -Force | Out-Null
                Copy-Item -LiteralPath $candidate -Destination $target -Force
                if (!(Test-LinuxFfmpeg $target)) { throw 'Installed Linux FFmpeg failed verification.' }
                Write-Host "Linux FFmpeg ready: $target" -ForegroundColor Green
                $success = $true
                break
            } catch {
                Write-Warning "$($source.Name) failed: $($_.Exception.Message)"
            }
        }
        if (!$success) { throw 'All Linux FFmpeg download sources failed. Check internet access, proxy, or firewall.' }
    } finally {
        if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
    }
    exit 0
} catch {
    Write-Host "[ERROR] Linux FFmpeg download failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
