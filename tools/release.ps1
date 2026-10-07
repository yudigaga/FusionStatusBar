param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GitPath = 'git',
    [string]$PythonPath = 'python',
    [string]$ArtifactRoot = 'artifacts/releases',
    [string]$PackageName = 'io.github.yudigaga.fusionstatusbar',
    [string]$Namespace = 'com.xtjm.fusionstatusbar',
    [string]$SourceRef = 'HEAD',
    [string]$CredentialFile = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/fusion-statusbar-release-credentials.txt'),
    [switch]$VerifyOnly,
    [switch]$AllowDirty,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$expectedCertificate = 'ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2'
$windowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    $output = & $Command @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Command" }
    return $output
}
function Write-Utf8([string]$Path, [string]$Content) { [IO.File]::WriteAllText($Path, $Content, $utf8) }

Push-Location $projectRoot
try {
    $sourceCommit = (Invoke-Checked $GitPath @('rev-parse', 'HEAD')).Trim()
    $selectedCommit = (Invoke-Checked $GitPath @('rev-parse', "$SourceRef^{commit}")).Trim()
    if ($sourceCommit -ne $selectedCommit) { throw 'The selected -SourceRef must match the checked-out commit.' }
    $dirty = -not [string]::IsNullOrWhiteSpace((Invoke-Checked $GitPath @('status', '--porcelain') | Out-String))
    if ($dirty -and -not $AllowDirty) { throw 'Dirty source tree: use -AllowDirty for a local archive with sourceDirty=true.' }
    $identityJson = Invoke-Checked $PythonPath @((Join-Path $PSScriptRoot 'validate-build.py'),
        '--project-root', $projectRoot, '--identity-only')
    $identity = (($identityJson -join "`n") | ConvertFrom-Json)
    if ($identity.applicationId -ne $PackageName -or $identity.namespace -ne $Namespace) {
        throw 'Source package or namespace differs from the selected release identity.'
    }
    $directory = [IO.Path]::GetFullPath((Join-Path $projectRoot "$ArtifactRoot/v$($identity.versionName)"))
    if (Test-Path -LiteralPath $directory) { throw "Artifact directory already exists: $directory" }
    if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME or supply -JavaHome pointing to JDK 21.' }
    $java = Join-Path $JavaHome $(if ($windowsHost) { 'bin/java.exe' } else { 'bin/java' })
    if (-not (Test-Path -LiteralPath $java)) { throw 'JDK 21 java executable was not found.' }
    $jdk = (Invoke-Checked $java @('--version') | Out-String).Trim()
    if ($jdk -notmatch '(?m)^(?:openjdk|java) 21[. ]') { throw 'JDK 21 is required for Miuix and Compose tests.' }
    if ([string]::IsNullOrWhiteSpace($AndroidSdk)) {
        $localFile = Join-Path $projectRoot 'local.properties'
        if (Test-Path -LiteralPath $localFile) {
            foreach ($line in [IO.File]::ReadAllLines($localFile)) {
                if ($line -match '^sdk\.dir=(.+)$') { $AndroidSdk = $Matches[1].Replace('\:', ':').Replace('\\', '\') }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($AndroidSdk)) { throw 'Set ANDROID_HOME or supply -AndroidSdk.' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
        Where-Object { $_.Name -eq '36.0.0' } | Select-Object -First 1
    if ($null -eq $buildTools) { throw 'Android SDK build-tools 36.0.0 are required.' }
    $aapt = Join-Path $buildTools.FullName $(if ($windowsHost) { 'aapt.exe' } else { 'aapt' })
    $apksigner = Join-Path $buildTools.FullName $(if ($windowsHost) { 'apksigner.bat' } else { 'apksigner' })
    $zipalign = Join-Path $buildTools.FullName $(if ($windowsHost) { 'zipalign.exe' } else { 'zipalign' })
    foreach ($tool in @($aapt, $apksigner, $zipalign)) { if (-not (Test-Path -LiteralPath $tool)) { throw "Missing Android tool: $tool" } }
    if (-not $VerifyOnly -and -not (Test-Path -LiteralPath $CredentialFile -PathType Leaf)) {
        throw 'A local signing credential file is required, or use -VerifyOnly.'
    }
    $env:JAVA_HOME = $JavaHome; $env:ANDROID_HOME = $AndroidSdk
    $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    & (Join-Path $PSScriptRoot 'save-source-snapshot.ps1') -ProjectRoot $projectRoot -GitPath $GitPath -Destination $directory
    if (-not $SkipBuild) {
        $gradle = Join-Path $projectRoot $(if ($windowsHost) { 'gradlew.bat' } else { 'gradlew' })
        Invoke-Checked $gradle @(':app:testDebugUnitTest', ':app:testReleaseUnitTest', ':app:lintDebug', ':app:lintRelease',
            ':app:assembleDebug', ':app:assembleRelease', '--console=plain', '--no-daemon')
    }
    $validationPath = Join-Path $directory 'verified-build.json'
    Invoke-Checked $PythonPath @((Join-Path $PSScriptRoot 'validate-build.py'), '--project-root', $projectRoot,
        '--package', $PackageName, '--namespace', $Namespace, '--output', $validationPath)
    $validation = Get-Content -LiteralPath $validationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $variants = [ordered]@{}
    foreach ($variantName in @('debug', 'release')) {
        $variant = $validation.variants.$variantName
        $apk = [string]$variant.apk
        $archivedName = "fusion-statusbar-v$($identity.versionName)-$variantName.apk"
        $target = Join-Path $directory $archivedName
        $certificate = $null; $signing = 'unsigned'; $alignment = 'not_performed'
        if ($variantName -eq 'release' -and -not $VerifyOnly) {
            $aligned = Join-Path $directory 'aligned-unsigned.apk'
            [void](Invoke-Checked $zipalign @('-P', '16', '4', $apk, $aligned))
            $credentials = @{}
            foreach ($line in [IO.File]::ReadAllLines($CredentialFile)) {
                if ($line -match '^\s*(keystore|alias|password)\s*[:=]\s*(.+)$') { $credentials[$Matches[1]] = $Matches[2].Trim() }
            }
            foreach ($field in @('keystore', 'alias', 'password')) { if (-not $credentials.ContainsKey($field)) { throw "Missing credential field: $field" } }
            $oldPassword = $env:FUSION_RELEASE_SIGN_PASSWORD
            try {
                $env:FUSION_RELEASE_SIGN_PASSWORD = $credentials.password
                [void](Invoke-Checked $apksigner @('sign', '--ks', $credentials.keystore, '--ks-key-alias', $credentials.alias,
                    '--ks-pass', 'env:FUSION_RELEASE_SIGN_PASSWORD', '--key-pass', 'env:FUSION_RELEASE_SIGN_PASSWORD',
                    '--v4-signing-enabled', 'false', '--out', $target, $aligned))
            } finally { $env:FUSION_RELEASE_SIGN_PASSWORD = $oldPassword; $credentials.Clear() }
            Remove-Item -LiteralPath $aligned
            $alignment = 'passed'; $signing = 'official'
            [void](Invoke-Checked $zipalign @('-c', '-P', '16', '-v', '4', $target))
        } else { Copy-Item -LiteralPath $apk -Destination $target }
        $badging = @(Invoke-Checked $aapt @('dump', 'badging', $target))
        $packageLine = @($badging | Where-Object { $_ -match '^package:' })
        if ($packageLine.Count -ne 1 -or $packageLine[0] -notmatch ("name='" + [regex]::Escape($PackageName) + "'") -or
            $packageLine[0] -notmatch ("versionCode='" + $identity.versionCode + "'") -or $packageLine[0] -notmatch ("versionName='" + [regex]::Escape($identity.versionName) + "'")) { throw 'APK manifest mismatch.' }
        $actualDebuggable = @($badging | Where-Object { $_ -match '^application-debuggable' }).Count -gt 0
        if ($actualDebuggable -ne ($variantName -eq 'debug')) { throw 'APK debuggable flag mismatch.' }
        Write-Utf8 (Join-Path $directory "manifest-badging-$variantName.txt") ($badging -join "`n")
        if ($variantName -eq 'debug' -or -not $VerifyOnly) {
            $signature = @(Invoke-Checked $apksigner @('verify', '--verbose', '--print-certs', $target))
            $signatureText = $signature -join "`n"
            if ($signatureText -notmatch 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})') { throw 'Signing certificate digest missing.' }
            $certificate = $Matches[1].ToLowerInvariant()
            if ($variantName -eq 'release' -and $certificate -ne $expectedCertificate) { throw 'Official signing certificate mismatch.' }
            Write-Utf8 (Join-Path $directory "signature-$variantName.txt") $signatureText
            if ($variantName -eq 'debug') { $signing = 'debug' }
        }
        $testDir = "app/build/test-results/test$($variantName.Substring(0,1).ToUpperInvariant())$($variantName.Substring(1))UnitTest"
        Copy-Item -LiteralPath $testDir -Destination (Join-Path $directory "tests-$variantName") -Recurse
        Copy-Item -LiteralPath "app/build/reports/lint-results-$variantName.xml" -Destination $directory
        $variants[$variantName] = [ordered]@{ apk = $archivedName; apkSha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant(); apkSize = (Get-Item -LiteralPath $target).Length; debuggable = $variantName -eq 'debug'; signing = $signing; signingCertificateSha256 = $certificate; zipAlignment = $alignment; tests = $variant.tests; lint = $variant.lint }
    }
    $source = Get-Content -LiteralPath (Join-Path $directory 'snapshot.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $patchHash = (Get-FileHash -LiteralPath (Join-Path $directory 'tracked.patch') -Algorithm SHA256).Hash.ToLowerInvariant()
    $wrapper = [IO.File]::ReadAllText((Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.properties'))
    $rootBuild = [IO.File]::ReadAllText((Join-Path $projectRoot 'build.gradle'))
    if ($wrapper -notmatch 'gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip') { throw 'Gradle version missing.' }; $gradleVersion = $Matches[1]
    if ($rootBuild -notmatch 'com.android.tools.build:gradle:([^"\s]+)') { throw 'AGP version missing.' }; $agpVersion = $Matches[1]
    $lockFiles = @(Get-ChildItem -LiteralPath $projectRoot -Recurse -Filter '*.lockfile' | Where-Object { $_.FullName -notmatch '[\\/](?:artifacts|build|\.gradle)[\\/]' } | ForEach-Object { [ordered]@{ path = $_.FullName.Substring($projectRoot.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } })
    if ($lockFiles.Count -eq 0) { throw 'Dependency lockfiles are required.' }
    $manifest = [ordered]@{ schemaVersion = 2; versionName = $identity.versionName; versionCode = $identity.versionCode; packageName = $identity.applicationId; namespace = $identity.namespace; buildMode = $(if ($VerifyOnly) { 'verification_only' } elseif ($source.sourceDirty) { 'local_dirty_signed' } else { 'official_signed' }); sourceCommit = $source.sourceCommit; sourceRef = $SourceRef; sourceTags = @(Invoke-Checked $GitPath @('tag', '--points-at', 'HEAD')); sourceDirty = [bool]$source.sourceDirty; sourceArchiveSha256 = $source.archiveSha256; sourceIndexSha256 = $source.sourceIndexSha256; sourcePatchSha256 = $patchHash; sourceSnapshot = 'source.zip'; variants = $variants; createdUtc = [DateTime]::UtcNow.ToString('o'); toolchain = [ordered]@{ jdk = $jdk; gradle = $gradleVersion; agp = $agpVersion; compileSdk = $identity.compileSdk; targetSdk = $identity.targetSdk; buildTools = $buildTools.Name; dependencyLocks = $lockFiles }; validationInput = $(if ($SkipBuild) { 'existing_build_outputs' } else { 'built_in_this_run' }); installation = 'not_performed'; systemUiInjection = 'not_performed'; romValidation = 'not_performed' }
    Write-Utf8 (Join-Path $directory 'release.json') ($manifest | ConvertTo-Json -Depth 10)
    Write-Utf8 (Join-Path $directory 'validation.md') ("# Local validation v$($identity.versionName)`n`nMode: $($manifest.buildMode). Source: $($source.sourceCommit), dirty=$($source.sourceDirty).`n`n" + (($variants.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value.tests.total) tests, $($_.Value.lint.errors) lint errors, $($_.Value.lint.warnings) warnings; signing=$($_.Value.signing)." }) -join "`n") + "`n`nInstallation, SystemUI injection, ROM compatibility and runtime performance were not validated.`n")
    Write-Output "Verified archive: $directory"
} finally { Pop-Location }
