param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$bin = Join-Path $ProjectRoot 'bin'
$exe = Join-Path $bin 'ffmpeg.exe'
if (Test-Path -LiteralPath $exe -PathType Leaf) {
    Write-Host "FFmpeg already exists: $exe"
    exit 0
}
$work = Join-Path ([System.IO.Path]::GetTempPath()) ('spw-ffmpeg-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Force -Path $bin, $work | Out-Null
    $archive = Join-Path $work 'ffmpeg.zip'
    $url = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip'
    Write-Host "Downloading FFmpeg from $url"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $work 'unpacked') -Force
    $found = Get-ChildItem -LiteralPath (Join-Path $work 'unpacked') -Filter ffmpeg.exe -Recurse -File | Select-Object -First 1
    if (-not $found) { throw 'ffmpeg.exe was not found in the downloaded archive.' }
    Copy-Item -LiteralPath $found.FullName -Destination $exe -Force
    & $exe -version | Select-Object -First 1
    if ($LASTEXITCODE -ne 0) { throw 'Downloaded FFmpeg failed verification.' }
    Write-Host "FFmpeg ready: $exe"
    exit 0
} catch {
    Write-Error $_
    if (Test-Path -LiteralPath $exe) { Remove-Item -LiteralPath $exe -Force }
    exit 1
} finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}
