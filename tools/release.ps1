param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GitPath = 'git',
    [string]$ArtifactRoot = 'artifacts/releases'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Command" }
}

function Read-ZipText($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) { throw "Missing APK entry: $Name" }
    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

Push-Location $projectRoot
try {
    if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
        throw 'Set JAVA_HOME or supply -JavaHome pointing to JDK 17.'
    }
    if ([string]::IsNullOrWhiteSpace($AndroidSdk)) {
        $localFile = Join-Path $projectRoot 'local.properties'
        if (Test-Path -LiteralPath $localFile) {
            foreach ($line in [System.IO.File]::ReadAllLines($localFile)) {
                if ($line -match '^sdk\.dir=(.+)$') { $AndroidSdk = $Matches[1].Replace('\:', ':').Replace('\\', '\') }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($AndroidSdk)) { throw 'Set ANDROID_HOME or supply -AndroidSdk.' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if ($null -eq $buildTools) { throw 'Android SDK build-tools are required.' }
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"
    $env:DEBUG = ''
    Invoke-Checked (Join-Path $projectRoot 'gradlew.bat') @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', '--console=plain')

    $testFiles = @(Get-ChildItem -LiteralPath 'app/build/test-results/testDebugUnitTest' -Filter 'TEST-*.xml')
    if ($testFiles.Count -eq 0) { throw 'No unit-test results found.' }
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($file in $testFiles) {
        $suite = ([xml][System.IO.File]::ReadAllText($file.FullName)).testsuite
        $tests += [int]$suite.tests; $failures += [int]$suite.failures
        $errors += [int]$suite.errors; $skipped += [int]$suite.skipped
    }
    if ($tests -eq 0 -or $failures -gt 0 -or $errors -gt 0 -or $skipped -gt 0) { throw 'Unit-test gate failed.' }
    $lint = [xml][System.IO.File]::ReadAllText((Join-Path $projectRoot 'app/build/reports/lint-results-debug.xml'))
    $issues = @($lint.SelectNodes('/issues/issue'))
    $lintErrors = @($issues | Where-Object { $_.severity -in @('Error', 'Fatal') })
    if ($lintErrors.Count -gt 0 -or @($issues | Where-Object { $_.id -eq 'ObsoleteLintCustomCheck' }).Count -gt 0) {
        throw 'Lint gate failed, including custom-check compatibility.'
    }
    $policy = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'lint-policy.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $counts = @{}
    foreach ($issue in $issues) {
        if ($issue.id -notin $policy.checkedIds) { continue }
        $location = $issue.SelectSingleNode('location')
        $path = if ($null -eq $location) { '' } else { [string]$location.file }
        $prefix = $projectRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
        if ($path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { $path = $path.Substring($prefix.Length) }
        $key = "$($issue.id):$($path.Replace('\', '/'))"
        $counts[$key] = 1 + [int]$counts[$key]
        $limit = $policy.allowed.PSObject.Properties[$key]
        if ($null -eq $limit -or $counts[$key] -gt [int]$limit.Value) {
            throw "New high-risk lint finding requires review: $key"
        }
    }
    $metadata = Get-Content -LiteralPath 'app/build/outputs/apk/debug/output-metadata.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1) { throw 'Expected one debug APK.' }
    $variant = $metadata.elements[0]
    $apk = Join-Path $projectRoot "app/build/outputs/apk/debug/$($variant.outputFile)"
    $signature = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $badging = & (Join-Path $buildTools.FullName 'aapt.exe') dump badging $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed.' }
    $packageLine = @($badging | Where-Object { $_ -match '^package:' })[0]
    if ($packageLine -notmatch "versionCode='$($variant.versionCode)'" -or
        $packageLine -notmatch ("versionName='" + [regex]::Escape($variant.versionName) + "'")) { throw 'APK manifest version mismatch.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try { $module = Read-ZipText $archive 'META-INF/xposed/module.prop' } finally { $archive.Dispose() }
    if ($module -notmatch ("(?m)^version=" + [regex]::Escape($variant.versionName) + '\r?$') -or
        $module -notmatch ("(?m)^versionCode=" + $variant.versionCode + '\r?$')) { throw 'Xposed metadata version mismatch.' }
    $buildConfig = [System.IO.File]::ReadAllText((Join-Path $projectRoot 'app/build/generated/source/buildConfig/debug/com/xtjm/fusionstatusbar/BuildConfig.java'))
    if ($buildConfig -notmatch ('VERSION_NAME = "' + [regex]::Escape($variant.versionName) + '"') -or
        $buildConfig -notmatch ('VERSION_CODE = ' + $variant.versionCode + ';')) { throw 'Hook BuildConfig version mismatch.' }

    $commit = (& $GitPath rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'A source Git commit is required.' }
    $sourcePaths = @(& $GitPath -c core.quotepath=false ls-files --cached --others --exclude-standard) | Sort-Object -Unique
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate source files.' }
    $sourceIndex = @($sourcePaths | ForEach-Object {
        if (Test-Path -LiteralPath $_ -PathType Leaf) {
            [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
    })
    $sourceJson = ConvertTo-Json -InputObject $sourceIndex -Depth 4
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $sourceHash = [BitConverter]::ToString($sha.ComputeHash($utf8.GetBytes($sourceJson))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
    $dirty = -not [string]::IsNullOrWhiteSpace((& $GitPath status --porcelain | Out-String))
    $directory = Join-Path $projectRoot "$ArtifactRoot/v$($variant.versionName)"
    if (Test-Path -LiteralPath $directory) { throw "Release directory already exists: $directory. Preserve it or select another -ArtifactRoot." }
    [void](New-Item -ItemType Directory -Path $directory -Force)
    Copy-Item -LiteralPath $apk -Destination $directory
    Copy-Item -LiteralPath 'app/build/test-results/testDebugUnitTest' -Destination (Join-Path $directory 'tests') -Recurse
    Copy-Item -LiteralPath 'app/build/reports/lint-results-debug.xml' -Destination $directory
    [System.IO.File]::WriteAllText((Join-Path $directory 'signature.txt'), ($signature -join "`n"), $utf8)
    [System.IO.File]::WriteAllText((Join-Path $directory 'source-files.json'), $sourceJson, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $directory 'module.prop'), $module, $utf8)
    $manifest = [ordered]@{
        versionName = $variant.versionName; versionCode = $variant.versionCode
        packageName = $metadata.applicationId; apk = $variant.outputFile
        apkSha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceCommit = $commit; sourceDirty = $dirty; sourceIndexSha256 = $sourceHash
        createdUtc = [DateTime]::UtcNow.ToString('o')
        tests = @{ total = $tests; failures = $failures; errors = $errors; skipped = $skipped }
        lint = @{ errors = $lintErrors.Count; warnings = @($issues | Where-Object { $_.severity -eq 'Warning' }).Count; customChecksCompatible = $true }
        deviceValidation = 'not_performed'; installation = 'not_performed'; romCompatibility = 'unverified'
        validationPolicy = 'local_only_as_requested'
        jdk = (& (Join-Path $JavaHome 'bin/java.exe') --version | Out-String).Trim()
        buildTools = $buildTools.Name
    }
    [System.IO.File]::WriteAllText((Join-Path $directory 'release.json'), ($manifest | ConvertTo-Json -Depth 6), $utf8)
    Write-Output "Verified release: $directory"
    Write-Output "Tests: $tests; lint warnings: $($manifest.lint.warnings); APK SHA-256: $($manifest.apkSha256)"
} finally { Pop-Location }
