param(
    [string]$ModelPath,
    [string]$ManifestPath = (Join-Path $PSScriptRoot '..\sky-embedding\model-manifest.example.json')
)

$ErrorActionPreference = 'Stop'

if (-not $ModelPath) {
    $ModelPath = if ($env:BGE_MODEL_HOST_PATH) {
        $env:BGE_MODEL_HOST_PATH
    }
    else {
        Join-Path $PSScriptRoot '..\sky-embedding\models\bge-m3'
    }
}

$resolvedModel = (Resolve-Path -LiteralPath $ModelPath).Path
$resolvedManifest = (Resolve-Path -LiteralPath $ManifestPath).Path
$manifest = Get-Content -LiteralPath $resolvedManifest -Raw -Encoding UTF8 | ConvertFrom-Json
$modelPrefix = $resolvedModel.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
$results = @()

foreach ($entry in $manifest.files.PSObject.Properties) {
    $filePath = [System.IO.Path]::GetFullPath((Join-Path $resolvedModel $entry.Name))
    if (-not $filePath.StartsWith($modelPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest contains an unsafe model path: $($entry.Name)"
    }
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        throw "Required model file is missing: $($entry.Name)"
    }
    $file = Get-Item -LiteralPath $filePath
    if ($file.Length -ne [long]$entry.Value.size) {
        throw "Model file size mismatch: $($entry.Name)"
    }
    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $expectedHash = ([string]$entry.Value.sha256).ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        throw "Model file checksum mismatch: $($entry.Name)"
    }
    $results += [pscustomobject]@{
        file = $entry.Name
        size = $file.Length
        sha256 = $actualHash
        verified = $true
    }
}

[pscustomobject]@{
    model = $manifest.model
    revision = $manifest.revision
    model_path = $resolvedModel
    verified_files = $results.Count
    status = 'verified'
    files = $results
} | ConvertTo-Json -Depth 4
