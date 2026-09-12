param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $BackupPath).Path
$manifestPath = Join-Path $root 'SHA256SUMS.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'SHA256SUMS.json is missing; this verifier requires the structured backup manifest'
}

$entries = @(Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($entries.Count -eq 0) { throw 'Backup checksum manifest is empty' }
$rootPrefix = $root.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar

foreach ($entry in $entries) {
    $path = [System.IO.Path]::GetFullPath((Join-Path $root $entry.relative_path))
    if (-not $path.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe path in checksum manifest: $($entry.relative_path)"
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Backup file is missing: $($entry.relative_path)"
    }
    $file = Get-Item -LiteralPath $path
    if ($file.Length -ne [long]$entry.size) {
        throw "Backup file size mismatch: $($entry.relative_path)"
    }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne ([string]$entry.sha256).ToLowerInvariant()) {
        throw "Backup checksum mismatch: $($entry.relative_path)"
    }
}

[pscustomobject]@{
    backup_path = $root
    verified_files = $entries.Count
    status = 'verified'
} | ConvertTo-Json
