param(
    [string]$Serial = 'emulator-5580',
    [string]$PageTab = '状态栏',
    [string]$ItemText = '状态栏监测',
    [int]$Iterations = 10,
    [string]$OutputName = 'settings-jank',
    [switch]$AllowPhysicalDevice,
    [double]$MaxJankyPercent = -1,
    [int]$MaxPostShowResizes = 0,
    [int]$MinRenderedFrames = 1,
    [int]$TraceBufferKb = 32768
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw 'Serial contains unsupported characters.' }
if (-not $AllowPhysicalDevice -and $Serial -notmatch '^emulator-\d+$') {
    throw 'This harness requires an emulator serial by default. Pass -AllowPhysicalDevice only for an explicitly authorized target run.'
}
if ($Iterations -lt 3 -or $Iterations -gt 50) { throw 'Iterations must be between 3 and 50.' }
if ($OutputName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') {
    throw 'OutputName must contain only letters, numbers, dot, underscore, or dash.'
}
if ($MaxJankyPercent -ne -1 -and ($MaxJankyPercent -lt 0 -or $MaxJankyPercent -gt 100)) {
    throw 'MaxJankyPercent must be -1 (metrics only) or between 0 and 100.'
}
if ($MaxPostShowResizes -lt 0) { throw 'MaxPostShowResizes must not be negative.' }
if ($MinRenderedFrames -lt 1) { throw 'MinRenderedFrames must be at least 1.' }
if ($TraceBufferKb -lt 32768 -or $TraceBufferKb -gt 524288) {
    throw 'TraceBufferKb must be between 32768 and 524288.'
}
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android platform-tools adb.exe was not found.' }
$escapedSerial = [regex]::Escape($Serial)
$deviceLines = @(& $adb devices | Where-Object { $_ -match "^$escapedSerial\s+(\S+)" })
if ($LASTEXITCODE -ne 0 -or $deviceLines.Count -ne 1 -or $deviceLines[0] -notmatch "^$escapedSerial\s+device\s*$") {
    throw "ADB target '$Serial' is not online with state 'device'."
}
$bootCompleted = (& $adb -s $Serial shell getprop sys.boot_completed | Out-String).Trim()
if ($bootCompleted -ne '1') { throw "ADB target '$Serial' has not completed boot." }
$targetModel = (& $adb -s $Serial shell getprop ro.product.model | Out-String).Trim()
$targetBuild = (& $adb -s $Serial shell getprop ro.build.version.release | Out-String).Trim()

function Invoke-TargetAdb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb command failed: $($Arguments -join ' ')" }
    return $result
}

function Get-NodeCenter($Node) {
    if ($Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Could not parse UI bounds: $($Node.bounds)"
    }
    $left = [int]$Matches[1]; $top = [int]$Matches[2]
    $right = [int]$Matches[3]; $bottom = [int]$Matches[4]
    if ($right -le $left -or $bottom -le $top -or $Node.enabled -ne 'true') {
        throw "UI item is disabled or has empty bounds: $($Node.bounds)"
    }
    return @([int](($left + $right) / 2), [int](($top + $bottom) / 2))
}

function Get-UiNodes {
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $output = Invoke-TargetAdb @('shell', 'uiautomator', 'dump', '/sdcard/settings-jank-window.xml') | Out-String
        if ($output -match 'dumped to:') {
            [xml]$document = ((Invoke-TargetAdb @('shell', 'cat', '/sdcard/settings-jank-window.xml')) | Out-String)
            return @($document.SelectNodes('//node'))
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'UI hierarchy capture failed; refusing to read a stale window dump.'
}

function Find-UiNode($Nodes, [string]$Text, [switch]$Description) {
    foreach ($node in $Nodes) {
        $value = if ($Description) { $node.'content-desc' } else { $node.text }
        if ($value -ceq $Text) { return $node }
    }
    throw "Could not find visible UI item '$Text' on the target device."
}

function Tap-Node($Node) {
    $center = Get-NodeCenter $Node
    [void](Invoke-TargetAdb @('shell', 'input', 'tap', "$($center[0])", "$($center[1])"))
}

function Get-FocusedAppWindow {
    $windows = Invoke-TargetAdb @('shell', 'dumpsys', 'window') | Out-String
    $focus = [regex]::Match($windows, 'mCurrentFocus=Window\{([^}\r\n]+)\}')
    if (-not $focus.Success -or $focus.Groups[1].Value -notmatch '\bcom\.xtjm\.fusionstatusbar/') {
        throw 'The target focus is not on a fusion-statusbar app window.'
    }
    return ($focus.Groups[1].Value -split '\s+')[0]
}

function Open-CloseDialog([int[]]$Center, [string]$ActivityWindow) {
    if ((Get-FocusedAppWindow) -ne $ActivityWindow) {
        throw 'The settings activity is not focused before the dialog tap.'
    }
    [void](Invoke-TargetAdb @('shell', 'input', 'tap', "$($Center[0])", "$($Center[1])"))
    Start-Sleep -Milliseconds 500
    if ((Get-FocusedAppWindow) -eq $ActivityWindow) {
        throw 'The tap did not open a dialog; the measurement is invalid.'
    }
    [void](Invoke-TargetAdb @('shell', 'input', 'keyevent', '4'))
    Start-Sleep -Milliseconds 500
    if ((Get-FocusedAppWindow) -ne $ActivityWindow) {
        throw 'The dialog did not close; the measurement is invalid.'
    }
}

$traceDirectory = Join-Path (Get-Location) 'build\perf'
$outputFiles = @(
    "$OutputName.trace",
    "$OutputName.json",
    "$OutputName.gfxinfo.txt",
    "$OutputName.framestats.txt",
    "$OutputName.cpuinfo.txt",
    "$OutputName.meminfo.txt",
    "$OutputName.metadata.json",
    "$OutputName.getprop.txt",
    "$OutputName.display.txt"
)
$existingOutputFiles = @($outputFiles | Where-Object { Test-Path -LiteralPath (Join-Path $traceDirectory $_) })
if ($existingOutputFiles.Count -gt 0) {
    throw "Refusing to overwrite existing measurement output: $($existingOutputFiles -join ', '). Choose a new OutputName."
}
$remoteTrace = "/sdcard/$OutputName.trace"
[void](& $adb -s $Serial shell test -e $remoteTrace)
if ($LASTEXITCODE -eq 0) {
    throw "Refusing to overwrite existing device trace '$remoteTrace'. Choose a new OutputName."
}
if ($LASTEXITCODE -ne 1) { throw "Could not check whether device trace '$remoteTrace' already exists." }
New-Item -ItemType Directory -Path $traceDirectory -Force | Out-Null

function Get-PackageMetadata([string]$PackageName) {
    $dump = (Invoke-TargetAdb @('shell', 'dumpsys', 'package', $PackageName) | Out-String)
    $versionNameMatch = [regex]::Match($dump, '(?m)^\s*versionName=([^\s]+)')
    $versionCodeMatch = [regex]::Match($dump, '(?m)^\s*versionCode=(\d+)')
    $paths = @((Invoke-TargetAdb @('shell', 'pm', 'path', $PackageName) | ForEach-Object {
        if ($_ -match '^package:(.+)$') { $Matches[1] }
    }))
    $hashes = [ordered]@{}
    foreach ($path in $paths) {
        $hash = (Invoke-TargetAdb @('shell', 'sha256sum', $path) | Out-String).Trim()
        $hashes[$path] = if ($hash -match '^([0-9a-fA-F]{64})') { $Matches[1].ToLowerInvariant() } else { $hash }
    }
    [ordered]@{
        package = $PackageName
        versionName = if ($versionNameMatch.Success) { $versionNameMatch.Groups[1].Value } else { '' }
        versionCode = if ($versionCodeMatch.Success) { [long]$versionCodeMatch.Groups[1].Value } else { $null }
        apkPaths = $paths
        sha256 = $hashes
        pid = ((Invoke-TargetAdb @('shell', 'pidof', $PackageName) | Out-String).Trim())
    }
}

function Get-SettingValue([string]$Namespace, [string]$Name) {
    return ((Invoke-TargetAdb @('shell', 'settings', 'get', $Namespace, $Name) | Out-String).Trim())
}

function Write-MeasurementText([string]$FileName, [string]$Content) {
    [System.IO.File]::WriteAllText((Join-Path $traceDirectory $FileName), $Content,
        (New-Object System.Text.UTF8Encoding($false)))
}

[void](Invoke-TargetAdb @('shell', 'am', 'start', '-n', 'io.github.yudigaga.fusionstatusbar/com.xtjm.fusionstatusbar.MainActivity'))
Start-Sleep -Seconds 3
$nodes = Get-UiNodes
Tap-Node (Find-UiNode $nodes $PageTab -Description)
Start-Sleep -Milliseconds 700
$nodes = Get-UiNodes
$itemNode = Find-UiNode $nodes $ItemText
$itemCenter = Get-NodeCenter $itemNode
$activityWindow = Get-FocusedAppWindow
$appMetadata = Get-PackageMetadata 'io.github.yudigaga.fusionstatusbar'
$systemUiMetadata = Get-PackageMetadata 'com.android.systemui'
$getpropText = (Invoke-TargetAdb @('shell', 'getprop') | Out-String)
$displayText = (Invoke-TargetAdb @('shell', 'dumpsys', 'display') | Out-String)
$refreshSettings = [ordered]@{
    peakRefreshRate = Get-SettingValue 'system' 'peak_refresh_rate'
    minRefreshRate = Get-SettingValue 'system' 'min_refresh_rate'
    userRefreshRate = Get-SettingValue 'system' 'user_refresh_rate'
    displayRefreshRateSetting = Get-SettingValue 'system' 'refresh_rate_mode'
}
$metadata = [ordered]@{
    capturedUtc = [DateTime]::UtcNow.ToString('o')
    serial = $Serial
    targetModel = $targetModel
    targetAndroid = $targetBuild
    app = $appMetadata
    systemUi = $systemUiMetadata
    refreshSettings = $refreshSettings
    getprop = "$OutputName.getprop.txt"
    display = "$OutputName.display.txt"
    settingsChanged = $false
    moduleLoadedVersion = 'not_verified_by_package_metadata'
}
Write-MeasurementText "$OutputName.getprop.txt" $getpropText
Write-MeasurementText "$OutputName.display.txt" $displayText
Write-MeasurementText "$OutputName.metadata.json" ($metadata | ConvertTo-Json -Depth 8)

# Warm the activity and dialog before resetting renderer counters.
Open-CloseDialog $itemCenter $activityWindow
Open-CloseDialog $itemCenter $activityWindow
Start-Sleep -Seconds 1
[void](Invoke-TargetAdb @('shell', 'dumpsys', 'gfxinfo', 'io.github.yudigaga.fusionstatusbar', 'reset'))
# Stop tracing even when a tap, focus check, or adb command fails.
$measurementStartedUtc = [DateTime]::UtcNow.ToString('o')
try {
    [void](Invoke-TargetAdb @('shell', 'atrace', '--async_start', '-b', "$TraceBufferKb", '-a',
        'io.github.yudigaga.fusionstatusbar', 'gfx', 'view', 'wm'))
    for ($i = 0; $i -lt $Iterations; $i++) { Open-CloseDialog $itemCenter $activityWindow }
} finally {
    [void](Invoke-TargetAdb @('shell', 'atrace', '--async_stop', '-o', $remoteTrace))
}
$measurementEndedUtc = [DateTime]::UtcNow.ToString('o')
$traceFile = Join-Path $traceDirectory "$OutputName.trace"
[void](Invoke-TargetAdb @('pull', $remoteTrace, $traceFile))
$stats = Invoke-TargetAdb @('shell', 'dumpsys', 'gfxinfo', 'io.github.yudigaga.fusionstatusbar')
Write-MeasurementText "$OutputName.gfxinfo.txt" ($stats | Out-String)
Write-MeasurementText "$OutputName.framestats.txt" (Invoke-TargetAdb @('shell', 'dumpsys', 'gfxinfo',
    'io.github.yudigaga.fusionstatusbar', 'framestats') | Out-String)
# Post-measurement snapshots cannot explain CPU/PSS at a particular slow frame.
# Keep these commands outside the traced open/close loop.
$cpuCapturedUtc = [DateTime]::UtcNow.ToString('o')
Write-MeasurementText "$OutputName.cpuinfo.txt" (Invoke-TargetAdb @('shell', 'dumpsys', 'cpuinfo') | Out-String)
$memoryCapturedUtc = [DateTime]::UtcNow.ToString('o')
Write-MeasurementText "$OutputName.meminfo.txt" (Invoke-TargetAdb @('shell', 'dumpsys', 'meminfo',
    'io.github.yudigaga.fusionstatusbar') | Out-String)
$summary = @($stats | Select-String 'Total frames rendered:|Janky frames:|50th percentile:|90th percentile:|95th percentile:|99th percentile:|Number Slow UI thread:|Number Frame deadline missed:')
$frameLine = $summary | Where-Object { $_.Line -match '^Total frames rendered:' } | Select-Object -First 1
$jankLine = $summary | Where-Object { $_.Line -match '^Janky frames:' } | Select-Object -First 1
if ($null -eq $frameLine -or $null -eq $jankLine) { throw 'gfxinfo did not return frame statistics.' }
if ($frameLine.Line -notmatch 'Total frames rendered:\s+(\d+)') { throw "Could not parse frame result: $($frameLine.Line)" }
$renderedFrames = [long]$Matches[1]
if ($jankLine.Line -notmatch 'Janky frames:\s+(\d+)\s+\(([\d.]+)%\)') { throw "Could not parse jank result: $($jankLine.Line)" }
$janky = [double]::Parse($Matches[2], [System.Globalization.CultureInfo]::InvariantCulture)
$relayoutEvents = @(Select-String -LiteralPath $traceFile -Pattern 'VRI\[MainActivity\]-relayoutWindow#first=(true|false)/resize=(true|false)')
$postShowResizes = @($relayoutEvents | Where-Object { $_.Line -match 'VRI\[MainActivity\]-relayoutWindow#first=false/resize=true' }).Count
$firstShowCount = @($relayoutEvents | Where-Object { $_.Line -match 'VRI\[MainActivity\]-relayoutWindow#first=true/resize=(true|false)' }).Count
$truncationReasons = @()
if ((Get-Item -LiteralPath $traceFile).Length -eq 0) {
    $truncationReasons += 'atrace returned an empty trace.'
} elseif ($relayoutEvents.Count -eq 0) {
    $truncationReasons += 'No supported settings-window relayout events were retained.'
}
$bufferHeaders = @(Select-String -LiteralPath $traceFile -Pattern 'entries-in-buffer/entries-written:\s*(\d+)\s*/\s*(\d+)')
foreach ($header in $bufferHeaders) {
    if ($header.Line -match 'entries-in-buffer/entries-written:\s*(\d+)\s*/\s*(\d+)' -and
        [long]$Matches[2] -gt [long]$Matches[1]) {
        $truncationReasons += 'Trace header reports more events written than retained in a buffer.'
    }
}
if (Select-String -LiteralPath $traceFile -Pattern 'LOST\s+\d+\s+EVENTS|lost_events=([1-9]\d*)' -Quiet) {
    $truncationReasons += 'Trace reports lost events.'
}
if ($firstShowCount -lt $Iterations) {
    $truncationReasons += 'The trace does not retain a first-show event for every completed cycle.'
}
$traceTruncationSuspected = $truncationReasons.Count -gt 0
"Target: $Serial; page: $PageTab; item: $ItemText; open/close cycles: $Iterations"
$summary | ForEach-Object { $_.Line }
"Window relayouts after the first show: $postShowResizes"
"Retained first-show events: $firstShowCount/$Iterations; trace truncation suspected: $traceTruncationSuspected"
$result = [ordered]@{
    capturedUtc = [DateTime]::UtcNow.ToString('o')
    measurementStartedUtc = $measurementStartedUtc
    measurementEndedUtc = $measurementEndedUtc
    serial = $Serial
    page = $PageTab
    item = $ItemText
    iterations = $Iterations
    renderedFrames = $renderedFrames
    jankyPercent = $janky
    postShowResizes = $postShowResizes
    traceFirstShowCount = $firstShowCount
    traceFirstShowCountInterpretation = 'count_in_retained_trace; not expected_per_iteration'
    traceTruncationSuspected = $traceTruncationSuspected
    traceTruncationReasons = $truncationReasons
    windowStructureEvidenceComplete = -not $traceTruncationSuspected
    traceBufferKb = $TraceBufferKb
    maxJankyPercent = $MaxJankyPercent
    maxPostShowResizes = $MaxPostShowResizes
    minRenderedFrames = $MinRenderedFrames
    gfxSummary = @($summary | ForEach-Object { $_.Line })
    trace = (Split-Path -Leaf $traceFile)
    gfxinfo = "$OutputName.gfxinfo.txt"
    framestats = "$OutputName.framestats.txt"
    framestatsCoverage = 'renderer_retained_history_may_cover_only_recent_frames'
    metadata = "$OutputName.metadata.json"
    app = $appMetadata
    systemUi = $systemUiMetadata
    refreshSettings = $refreshSettings
    cpuinfo = "$OutputName.cpuinfo.txt"
    cpuCapturedUtc = $cpuCapturedUtc
    meminfo = "$OutputName.meminfo.txt"
    memoryCapturedUtc = $memoryCapturedUtc
    resourceSampling = 'post_measurement_snapshots_only'
    emulatorOnly = -not $AllowPhysicalDevice
    deviceValidation = if ($AllowPhysicalDevice) { 'performed' } else { 'not_performed' }
    targetModel = $targetModel
    targetAndroid = $targetBuild
}
$summaryFile = Join-Path $traceDirectory "$OutputName.json"
[System.IO.File]::WriteAllText($summaryFile, ($result | ConvertTo-Json -Depth 4), (New-Object System.Text.UTF8Encoding($false)))
'Machine-readable summary written before threshold evaluation.'
if ($renderedFrames -lt $MinRenderedFrames) {
    throw "Insufficient rendered frames: $renderedFrames is below the $MinRenderedFrames minimum."
}
if ($traceTruncationSuspected) {
    throw "Trace completeness check failed: $($truncationReasons -join ' ') Use fewer iterations or a larger TraceBufferKb with a new OutputName."
}
if ($postShowResizes -gt $MaxPostShowResizes) {
    throw "Window relayout regression: $postShowResizes exceeds the $MaxPostShowResizes limit."
}
if ($MaxJankyPercent -ge 0 -and $janky -gt $MaxJankyPercent) {
    throw "Frame-jank regression: $janky% exceeds the $MaxJankyPercent% limit."
}
'Settings-window structure check passed. Frame statistics describe this target only and do not establish cross-version improvement.'
if ($MaxJankyPercent -lt 0) { 'Frame-jank percentage is reported only; no performance threshold is enabled.' }
"Machine-readable summary: $summaryFile"
