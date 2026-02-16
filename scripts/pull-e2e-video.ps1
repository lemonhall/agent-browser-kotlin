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

$deviceFramesDir = "/sdcard/Download/agent-browser-kotlin/e2e/frames"
$deviceSnapshotsDir = "/sdcard/Download/agent-browser-kotlin/e2e/snapshots"
$deviceAppExternalFramesDir = "/sdcard/Android/data/com.lsl.agent_browser_kotlin/files/e2e/frames"

$outRoot = Join-Path $PWD "adb_dumps\\e2e"
$latestDir = Join-Path $outRoot "latest"
New-Item -ItemType Directory -Force -Path $latestDir | Out-Null

$latestRunsDir = Join-Path $latestDir "runs"
New-Item -ItemType Directory -Force -Path $latestRunsDir | Out-Null

$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $outRoot $runStamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

Write-Host "Pulling artifacts into $runDir"

$framesLocal = Join-Path $runDir "frames"
New-Item -ItemType Directory -Force -Path $framesLocal | Out-Null
$p1 = Start-Process -FilePath $adb -ArgumentList @("pull", $deviceFramesDir, $framesLocal) -NoNewWindow -PassThru -Wait
if ($p1.ExitCode -ne 0) { throw "adb pull frames failed (exit=$($p1.ExitCode))" }
$pngCount = (Get-ChildItem -Path $framesLocal -Recurse -Filter "*.png" -ErrorAction SilentlyContinue).Count
if ($pngCount -eq 0) {
  $p2 = Start-Process -FilePath $adb -ArgumentList @("pull", $deviceAppExternalFramesDir, $framesLocal) -NoNewWindow -PassThru -Wait
  if ($p2.ExitCode -ne 0) { throw "adb pull frames (app external) failed (exit=$($p2.ExitCode))" }
  $pngCount2 = (Get-ChildItem -Path $framesLocal -Recurse -Filter "*.png" -ErrorAction SilentlyContinue).Count
  if ($pngCount2 -eq 0) { throw "No png frames found after both pulls. Ensure connectedAndroidTest produced screenshots." }
}

$allRunPngs = @()
for ($i = 0; $i -lt 240; $i++) {
  $allRunPngs = @(
    Get-ChildItem -Path $framesLocal -Recurse -Filter "*.png" -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like "run-*-step-*.png" }
  )
  if ($allRunPngs.Length -gt 0) { break }
  Start-Sleep -Milliseconds 500
}
if ($allRunPngs.Length -eq 0) {
  throw "No run frames found in $framesLocal (expected run-<millis>-...-step-XX.png). Ensure you ran :app:connectedAndroidTest."
}

function Get-RunFrameInfo {
  param([System.IO.FileInfo]$File)
  $m = [regex]::Match($File.Name, '^run-(\d+)(?:-(.+))?-step-(\d+)\.png$')
  if (-not $m.Success) { return $null }
  $runId = [int64]$m.Groups[1].Value
  $label = $m.Groups[2].Value
  $step = [int]$m.Groups[3].Value
  [pscustomobject]@{ File = $File; RunId = $runId; Label = $label; Step = $step }
}

$frameInfos = @(
  foreach ($f in $allRunPngs) {
    $info = Get-RunFrameInfo -File $f
    if ($null -ne $info) { $info }
  }
)
if ($frameInfos.Count -eq 0) { throw "Found run-*-step-*.png but could not parse any run ids (unexpected naming)" }

$runIds = $frameInfos | Select-Object -ExpandProperty RunId | Sort-Object -Unique
$latestRun = ($runIds | Measure-Object -Maximum).Maximum
Write-Host "Detected runs: $($runIds.Count) (latest run=$latestRun)"

$snapshotsLocal = Join-Path $runDir "snapshots"
New-Item -ItemType Directory -Force -Path $snapshotsLocal | Out-Null
$p3 = Start-Process -FilePath $adb -ArgumentList @("pull", $deviceSnapshotsDir, $snapshotsLocal) -NoNewWindow -PassThru -Wait
if ($p3.ExitCode -ne 0) {
  Write-Warning "adb pull snapshots failed (exit=$($p3.ExitCode))"
}

function HtmlEncode([string]$s) { [System.Net.WebUtility]::HtmlEncode($s) }

function Write-RunReportHtml {
  param(
    [string]$OutDir,
    [int64]$RunId,
    [string]$Label,
    [object[]]$Steps
  )
  $title = if ($Label) { "agent-browser-kotlin E2E run-$RunId ($Label)" } else { "agent-browser-kotlin E2E run-$RunId" }
  $sb = New-Object System.Collections.Generic.List[string]
  $sb.Add('<!doctype html>')
  $sb.Add('<html><head><meta charset="utf-8" />')
  $sb.Add("<title>$([System.Net.WebUtility]::HtmlEncode($title))</title>")
  $sb.Add('<style>body{font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,Arial;margin:24px;line-height:1.35} .run{color:#334155} img{max-width:420px;border:1px solid #e2e8f0;border-radius:10px;box-shadow:0 10px 30px rgba(2,6,23,.08)} pre{white-space:pre-wrap;background:#0b1020;color:#e2e8f0;padding:12px;border-radius:10px;overflow:auto} a{color:#2563eb;text-decoration:none} a:hover{text-decoration:underline} .grid{display:grid;grid-template-columns:460px 1fr;gap:16px;align-items:start;margin:18px 0} .meta{display:flex;gap:10px;flex-wrap:wrap;font-size:13px;color:#475569}</style>')
  $sb.Add('</head><body>')
  $sb.Add("<h1>$([System.Net.WebUtility]::HtmlEncode($title))</h1>")
  $labelEsc = [System.Net.WebUtility]::HtmlEncode(($Label -as [string]))
  $generated = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
  $sb.Add('<div class="meta"><span class="run">run=' + $RunId + '</span><span>label=' + $labelEsc + '</span><span>generated=' + $generated + '</span></div>')

  foreach ($s in $Steps) {
    $stepNum = $s.Step
    $stepStr = if ($stepNum -lt 10) { "0$stepNum" } else { "$stepNum" }
    $imgRel = "frames/step-$stepStr.png"
    $txtRel = if ($s.SnapshotTxt) { "snapshots/$($s.SnapshotTxt)" } else { $null }
    $jsonRel = if ($s.SnapshotJson) { "snapshots/$($s.SnapshotJson)" } else { $null }
    $sb.Add("<h2>Step $stepStr</h2>")
    $sb.Add('<div class="grid">')
    $sb.Add('<div><img src="' + $imgRel + '" alt="step-' + $stepStr + '" /></div>')
    $sb.Add('<div>')
    if ($txtRel) {
      $jsonHref = if ($jsonRel) { $jsonRel } else { '#' }
      $sb.Add('<div class="meta"><a href="' + $txtRel + '">snapshot.txt</a> · <a href="' + $jsonHref + '">snapshot.json</a></div>')
      $txtPath = Join-Path $OutDir $txtRel
      $txt = ''
      try { $txt = Get-Content -Raw -Path $txtPath -ErrorAction Stop } catch { $txt = "[missing snapshot txt: $($txtPath)]" }
      $sb.Add("<pre>$([System.Net.WebUtility]::HtmlEncode($txt))</pre>")
    } else {
      $sb.Add('<div class="meta">no snapshot for this step</div>')
    }
    $sb.Add('</div></div>')
  }

  $sb.Add('</body></html>')
  $outFile = Join-Path $OutDir "report.html"
  $sb | Set-Content -Path $outFile -Encoding UTF8
}

$runsOutDir = Join-Path $runDir "runs"
New-Item -ItemType Directory -Force -Path $runsOutDir | Out-Null

$durationSec = 3.5
$runSummaries = New-Object System.Collections.Generic.List[object]

foreach ($rid in $runIds) {
  $runLabel = ($frameInfos | Where-Object { $_.RunId -eq $rid -and $_.Label } | Select-Object -First 1 -ExpandProperty Label)
  $runOut = Join-Path $runsOutDir "run-$rid"
  New-Item -ItemType Directory -Force -Path $runOut | Out-Null
  $runFramesOut = Join-Path $runOut "frames"
  New-Item -ItemType Directory -Force -Path $runFramesOut | Out-Null
  $runSnapshotsOut = Join-Path $runOut "snapshots"
  New-Item -ItemType Directory -Force -Path $runSnapshotsOut | Out-Null

  $frames = @(
    $frameInfos |
      Where-Object { $_.RunId -eq $rid } |
      Sort-Object Step, @{ Expression = { $_.File.Name }; Ascending = $true }
  )
  if ($frames.Count -eq 0) { continue }

  $stepFiles = New-Object System.Collections.Generic.List[object]
  foreach ($fi in $frames) {
    $stepNum = [int]$fi.Step
    $stepStr = if ($stepNum -lt 10) { "0$stepNum" } else { "$stepNum" }
    $dstName = "step-$stepStr.png"
    Copy-Item -Force $fi.File.FullName (Join-Path $runFramesOut $dstName)
    $stepFiles.Add([pscustomobject]@{ Step = $stepNum; Frame = $dstName; SnapshotTxt = $null; SnapshotJson = $null })
  }

  $snapFiles = @(
    Get-ChildItem -Path $snapshotsLocal -Recurse -File -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like "run-$rid*-step-*-snapshot.*" } |
      Sort-Object Name
  )
  foreach ($sf in $snapFiles) {
    Copy-Item -Force $sf.FullName (Join-Path $runSnapshotsOut $sf.Name)
    $m2 = [regex]::Match($sf.Name, '^run-(\d+)(?:-.+)?-step-(\d+)-snapshot\.(txt|json)$')
    if ($m2.Success) {
      $stepNum = [int]$m2.Groups[2].Value
      $ext = $m2.Groups[3].Value
      $row = $stepFiles | Where-Object { $_.Step -eq $stepNum } | Select-Object -First 1
      if ($row) {
        if ($ext -eq 'txt') { $row.SnapshotTxt = $sf.Name }
        if ($ext -eq 'json') { $row.SnapshotJson = $sf.Name }
      }
    }
  }

  $concat = Join-Path $runOut "concat.txt"
  $lines = New-Object System.Collections.Generic.List[string]
  foreach ($s in ($stepFiles | Sort-Object Step)) {
    $pngPath = Join-Path $runFramesOut $s.Frame
    $safe = $pngPath -replace "'", "''"
    $lines.Add("file '$safe'")
    $lines.Add("duration $durationSec")
  }
  $lastPngPath = Join-Path $runFramesOut (($stepFiles | Sort-Object Step | Select-Object -Last 1).Frame)
  $lastSafe = $lastPngPath -replace "'", "''"
  $lines.Add("file '$lastSafe'")
  $lines | Set-Content -Path $concat -Encoding UTF8

  $outMp4 = Join-Path $runOut "e2e.mp4"
  & $ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i $concat -vsync vfr -pix_fmt yuv420p $outMp4 | Out-Null

  Write-RunReportHtml -OutDir $runOut -RunId $rid -Label $runLabel -Steps ($stepFiles | Sort-Object Step)

  $runSummaries.Add([pscustomobject]@{
    runId = $rid
    label = $runLabel
    frames = $stepFiles.Count
    snapshots = $snapFiles.Count
    dir = "runs/run-$rid"
  })
  Write-Host "OK: built run=$rid label=$runLabel frames=$($stepFiles.Count) snapshots=$($snapFiles.Count)"
}

if ($runSummaries.Count -eq 0) { throw "No run outputs were built (unexpected)" }

# Copy per-run bundles into latest for human review.
foreach ($r in $runSummaries) {
  $src = Join-Path $runsOutDir ("run-" + $r.runId)
  $dst = Join-Path $latestRunsDir ("run-" + $r.runId)
  New-Item -ItemType Directory -Force -Path $dst | Out-Null
  Copy-Item -Force -Recurse (Join-Path $src "*") $dst
}

$latestBundleSrc = Join-Path $runsOutDir ("run-" + $latestRun)
Copy-Item -Force (Join-Path $latestBundleSrc "e2e.mp4") (Join-Path $latestDir "e2e-latest.mp4")
Copy-Item -Force (Join-Path $latestBundleSrc "report.html") (Join-Path $latestDir "report.html")

$runsJsonPath = Join-Path $latestDir "runs.json"
$runSummaries | Sort-Object runId | ConvertTo-Json -Depth 6 | Set-Content -Path $runsJsonPath -Encoding UTF8

$index = New-Object System.Collections.Generic.List[string]
$index.Add('<!doctype html><html><head><meta charset="utf-8" />')
$index.Add('<title>agent-browser-kotlin E2E evidence (latest)</title>')
$index.Add('<style>body{font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,Arial;margin:24px;line-height:1.35} table{border-collapse:collapse;width:100%} th,td{border-bottom:1px solid #e2e8f0;padding:10px;text-align:left} th{color:#0f172a} .muted{color:#475569;font-size:13px} a{color:#2563eb;text-decoration:none} a:hover{text-decoration:underline}</style></head><body>')
$index.Add('<h1>agent-browser-kotlin E2E evidence (latest)</h1>')
$index.Add('<div class="muted">generated=' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' · latest run=' + $latestRun + ' · files under adb_dumps/e2e/latest/</div>')
$index.Add('<p><a href="e2e-latest.mp4">e2e-latest.mp4</a> · <a href="report.html">latest report.html</a></p>')
$index.Add('<h2>Runs</h2><table><thead><tr><th>runId</th><th>label</th><th>frames</th><th>snapshots</th><th>links</th></tr></thead><tbody>')
foreach ($r in ($runSummaries | Sort-Object runId)) {
  $rid = $r.runId
  $label = [System.Net.WebUtility]::HtmlEncode(($r.label -as [string]))
  $index.Add('<tr><td>' + $rid + '</td><td>' + $label + '</td><td>' + $r.frames + '</td><td>' + $r.snapshots + '</td><td><a href="runs/run-' + $rid + '/e2e.mp4">mp4</a> · <a href="runs/run-' + $rid + '/report.html">report</a></td></tr>')
}
$index.Add('</tbody></table>')
$index.Add('<p class="muted">Tip: open <code>index.html</code> directly in a desktop browser for fast review.</p>')
$index.Add('</body></html>')
$indexPath = Join-Path $latestDir "index.html"
$index | Set-Content -Path $indexPath -Encoding UTF8

Write-Host "OK: latest entrypoints: adb_dumps\\e2e\\latest\\index.html / report.html / e2e-latest.mp4"
