param([string]$Source = '..', [string]$Output = 'artifacts/evidence-index.json')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $Source))
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $Output))
$items = @(Get-ChildItem -LiteralPath $sourcePath -File | Where-Object { $_.Extension -in @('.apk', '.zip', '.png', '.txt') } | Sort-Object Name | ForEach-Object {
    [ordered]@{ name=$_.Name; bytes=$_.Length; modifiedUtc=$_.LastWriteTimeUtc.ToString('o'); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
[void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath))
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputPath, (ConvertTo-Json -InputObject $items -Depth 4), $utf8)
Write-Output "Indexed $($items.Count) files: $outputPath"
