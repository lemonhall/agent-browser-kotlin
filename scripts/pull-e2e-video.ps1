$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
  $cmd = Get-Command adb -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $sdk = $env:ANDROID_SDK_ROOT
  if (-not $sdk) { $sdk = $env:ANDROID_HOME }
  if (-not $sdk) { throw "ANDROID_HOME / ANDROID_SDK_ROOT not set and adb not found in PATH" }
  $adb = Join-Path $sdk "platform-tools\\adb.exe"
  if (-not (Test-Path $adb)) { throw "adb.exe not found at $adb" }
  return $adb
}

function Resolve-FfmpegPath {
  $cmd = Get-Command ffmpeg -ErrorAction SilentlyContinue
  if (-not $cmd) { throw "ffmpeg not found in PATH" }
  return $cmd.Source
}

$adb = Resolve-AdbPath
$ffmpeg = Resolve-FfmpegPath

$deviceMp4 = "/sdcard/Download/agent-browser-kotlin/e2e-latest.mp4"
$deviceFramesDir = "/sdcard/Download/agent-browser-kotlin/e2e/frames"
$deviceAppExternalFramesDir = "/sdcard/Android/data/com.lsl.agent_browser_kotlin/files/e2e/frames"

$outRoot = Join-Path $PWD "adb_dumps\\e2e"
$latestDir = Join-Path $outRoot "latest"
New-Item -ItemType Directory -Force -Path $latestDir | Out-Null

Write-Host "Pulling artifacts into $latestDir"

& $adb pull $deviceMp4 (Join-Path $latestDir "e2e-latest.mp4") 2>$null | Out-Null
if (Test-Path (Join-Path $latestDir "e2e-latest.mp4")) {
  Write-Host "OK: pulled mp4: adb_dumps\\e2e\\latest\\e2e-latest.mp4"
  exit 0
}

$framesLocal = Join-Path $latestDir "frames"
New-Item -ItemType Directory -Force -Path $framesLocal | Out-Null
& $adb pull $deviceFramesDir $framesLocal | Out-Null
if ((Get-ChildItem -Path $framesLocal -Recurse -Filter "step-*.png" -ErrorAction SilentlyContinue).Count -eq 0) {
  & $adb pull $deviceAppExternalFramesDir $framesLocal 2>$null | Out-Null
}

$pngs = Get-ChildItem -Path $framesLocal -Recurse -Filter "step-*.png" | Sort-Object Name
if ($pngs.Count -eq 0) { throw "No frames found in $framesLocal (expected step-*.png)" }

$concat = Join-Path $latestDir "concat.txt"
$durationSec = 3.5
$lines = New-Object System.Collections.Generic.List[string]
foreach ($png in $pngs) {
  $safe = $png.FullName -replace "'", "''"
  $lines.Add("file '$safe'")
  $lines.Add("duration $durationSec")
}
$lastSafe = $pngs[-1].FullName -replace "'", "''"
$lines.Add("file '$lastSafe'")
$lines | Set-Content -Path $concat -Encoding UTF8

$outMp4 = Join-Path $latestDir "e2e-latest.mp4"
& $ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i $concat -vsync vfr -pix_fmt yuv420p $outMp4 | Out-Null

Write-Host "OK: built mp4 from frames: adb_dumps\\e2e\\latest\\e2e-latest.mp4"
