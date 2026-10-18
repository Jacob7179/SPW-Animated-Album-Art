param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference = 'Stop'
# Normalize paths passed from Windows CMD, including accidental trailing quotes.
$ProjectRoot = $ProjectRoot.Trim().Trim('"').TrimEnd('\')
if (-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) {
    throw "Project directory not found: $ProjectRoot"
}
$jdkCache = Join-Path $ProjectRoot '.jdk'
$pathFile = Join-Path $jdkCache 'current-jdk.txt'
New-Item -ItemType Directory -Path $jdkCache -Force | Out-Null

function Test-Jdk25([string]$dir) {
    if ([string]::IsNullOrWhiteSpace($dir)) { return $false }
    $exe = Join-Path $dir 'bin\java.exe'
    if (!(Test-Path -LiteralPath $exe -PathType Leaf)) { return $false }
    # Java writes its version to stderr even when successful. Windows PowerShell 5.1
    # can turn native stderr into a terminating NativeCommandError when Stop is set.
    # Use ProcessStartInfo so normal version output never becomes a PS error.
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $exe
        $psi.Arguments = '-version'
        $psi.UseShellExecute = $false
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.CreateNoWindow = $true
        $process = [System.Diagnostics.Process]::Start($psi)
        $stderr = $process.StandardError.ReadToEnd()
        $stdout = $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        $result = $stderr + "`n" + $stdout
        return ($process.ExitCode -eq 0 -and $result -match 'version "25(?:\.|")')
    } catch {
        return $false
    } finally {
        if ($null -ne $process) { $process.Dispose() }
    }
}
function Use-Jdk([string]$dir) {
    $resolved = (Resolve-Path -LiteralPath $dir).Path
    [System.IO.File]::WriteAllText($pathFile, $resolved, [System.Text.Encoding]::ASCII)
    Write-Host "JDK 25 ready: $resolved"
}

if (Test-Path -LiteralPath $pathFile) {
    $cached = ([System.IO.File]::ReadAllText($pathFile)).Trim()
    if (Test-Jdk25 $cached) { Use-Jdk $cached; exit 0 }
}
if (Test-Jdk25 $env:JAVA_HOME) { Use-Jdk $env:JAVA_HOME; exit 0 }
foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Amazon Corretto")) {
    if (!(Test-Path -LiteralPath $base)) { continue }
    foreach ($candidate in (Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue)) {
        if (Test-Jdk25 $candidate.FullName) { Use-Jdk $candidate.FullName; exit 0 }
    }
}
foreach ($candidate in (Get-ChildItem -LiteralPath $jdkCache -Directory -ErrorAction SilentlyContinue)) {
    if (Test-Jdk25 $candidate.FullName) { Use-Jdk $candidate.FullName; exit 0 }
}

$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
$url = "https://api.adoptium.net/v3/binary/latest/25/ga/windows/$arch/jdk/hotspot/normal/eclipse"
$archive = Join-Path $jdkCache 'temurin-25.zip'
$extract = Join-Path $jdkCache 'extracted'
Write-Host "JDK 25 not found. Downloading Eclipse Temurin for Windows $arch..."
Write-Host "Official download: $url"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $archive -MaximumRedirection 10 -UseBasicParsing
    if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
    Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
    $java = Get-ChildItem -LiteralPath $extract -Filter java.exe -Recurse -File | Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } | Select-Object -First 1
    if ($null -eq $java) { throw 'Downloaded archive does not contain bin\java.exe' }
    $jdk = Split-Path -Parent (Split-Path -Parent $java.FullName)
    if (!(Test-Jdk25 $jdk)) { throw 'Downloaded Java is not JDK 25' }
    Use-Jdk $jdk
    Remove-Item -LiteralPath $archive -Force
    exit 0
} catch {
    Write-Error "Failed to download or unpack JDK 25: $($_.Exception.Message)"
    exit 1
}
