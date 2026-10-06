param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$GitPath = 'git',
    [Parameter(Mandatory = $true)][string]$Destination,
    [switch]$Extract
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
$outputDirectory = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $outputDirectory) { throw 'Snapshot destination already exists.' }
$utf8 = New-Object Text.UTF8Encoding($false)
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Push-Location $projectDirectory
try {
    $commit = (& $GitPath rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read source commit.' }
    $paths = @(& $GitPath -c core.quotepath=false ls-files --cached --others --exclude-standard) | Sort-Object -Unique
    if ($LASTEXITCODE -ne 0 -or $paths.Count -eq 0) { throw 'Cannot enumerate source files.' }
    $dirty = -not [string]::IsNullOrWhiteSpace((& $GitPath status --porcelain | Out-String))
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect source status.' }
    $prefix = $projectDirectory + [IO.Path]::DirectorySeparatorChar
    [void](New-Item -ItemType Directory -Path $outputDirectory)
    $archivePath = Join-Path $outputDirectory 'source.zip'
    $archive = [IO.Compression.ZipFile]::Open($archivePath, [IO.Compression.ZipArchiveMode]::Create)
    $index = @()
    try {
        foreach ($relativePath in $paths) {
            $sourcePath = [IO.Path]::GetFullPath((Join-Path $projectDirectory $relativePath))
            if (-not $sourcePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'Source entry is outside the project.'
            }
            if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { continue }
            if ($relativePath -match '(?i)\.(jks|keystore|p12|pfx)$|(^|/)(\.env|[^/]*credentials[^/]*)$') {
                throw 'Credential material must not be included in a source snapshot.'
            }
            $entryName = $relativePath.Replace('\', '/')
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive, $sourcePath, $entryName, [IO.Compression.CompressionLevel]::Optimal)
            $index += [ordered]@{
                path = $entryName
                sha256 = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
            }
        }
    } finally { $archive.Dispose() }
    $patchPath = Join-Path $outputDirectory 'tracked.patch'
    & $GitPath diff --binary HEAD "--output=$patchPath"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot preserve tracked diff.' }
    [IO.File]::WriteAllText((Join-Path $outputDirectory 'source-files.json'),
        (ConvertTo-Json -InputObject $index -Depth 4), $utf8)
    $manifest = [ordered]@{
        sourceCommit = $commit; sourceDirty = $dirty; files = $index.Count
        archive = 'source.zip'
        archiveSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceIndexSha256 = (Get-FileHash -LiteralPath (Join-Path $outputDirectory 'source-files.json') -Algorithm SHA256).Hash.ToLowerInvariant()
        createdUtc = [DateTime]::UtcNow.ToString('o')
    }
    [IO.File]::WriteAllText((Join-Path $outputDirectory 'snapshot.json'),
        ($manifest | ConvertTo-Json -Depth 4), $utf8)
    if ($Extract) {
        [IO.Compression.ZipFile]::ExtractToDirectory($archivePath, (Join-Path $outputDirectory 'source'))
    }
    Write-Output "Source snapshot: $outputDirectory ($($index.Count) files)"
} finally { Pop-Location }
