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


$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $outRoot $runStamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

Write-Host "Pulling artifacts into $runDir"

$framesLocal = Join-Path $runDir "frames"
New-Item -ItemType Directory -Force -Path $framesLocal | Out-Null
$p1 = Start-Process -FilePath $adb -ArgumentList @("pull", $deviceFramesDir, $framesLocal) -NoNewWindow -PassThru -Wait
if ($p1.ExitCode -ne 0) { throw "adb pull frames failed (exit=$($p1.ExitCode))" }
if ((Get-ChildItem -Path $framesLocal -Recurse -Filter "step-*.png" -ErrorAction SilentlyContinue).Count -eq 0) {
  $p2 = Start-Process -FilePath $adb -ArgumentList @("pull", $deviceAppExternalFramesDir, $framesLocal) -NoNewWindow -PassThru -Wait
  if ($p2.ExitCode -ne 0) { throw "adb pull frames (app external) failed (exit=$($p2.ExitCode))" }
}

$runPngs = @()
for ($i = 0; $i -lt 240; $i++) {
  $runPngs = @(
    Get-ChildItem -Path $framesLocal -Recurse -Filter "*.png" -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like "run-*-step-*.png" }
  )
  if ($runPngs.Length -gt 0) { break }
  Start-Sleep -Milliseconds 500
}
if ($runPngs.Length -eq 0) {
  throw "No run frames found in $framesLocal (expected run-<millis>-step-XX.png). Ensure you ran :app:connectedAndroidTest."
}

$runs = $runPngs | ForEach-Object {
  $parts = $_.BaseName.Split('-')
  if ($parts.Length -ge 4 -and $parts[0] -eq "run" -and $parts[2] -eq "step") { [int64]$parts[1] } else { 0 }
}
$latestRun = ($runs | Measure-Object -Maximum).Maximum
$pngs = $runPngs | Where-Object { $_.Name -like "run-$latestRun-step-*.png" } | Sort-Object Name
if ($pngs.Count -eq 0) { throw "No frames found for latest run=$latestRun in $framesLocal" }
Write-Host "Selected latest run=$latestRun ($($pngs.Count) frames)"

$concat = Join-Path $runDir "concat.txt"
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

$outMp4 = Join-Path $runDir "e2e.mp4"
& $ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i $concat -vsync vfr -pix_fmt yuv420p $outMp4 | Out-Null

Copy-Item -Force $outMp4 (Join-Path $latestDir "e2e-latest.mp4")
Write-Host "OK: built mp4 from frames: adb_dumps\\e2e\\latest\\e2e-latest.mp4"
