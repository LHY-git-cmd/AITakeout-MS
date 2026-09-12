param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory
)

$ErrorActionPreference = 'Stop'
$resolvedProject = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedBackup = [System.IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Force -Path $resolvedBackup | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$destination = Join-Path $resolvedBackup $stamp
New-Item -ItemType Directory -Path $destination | Out-Null

Push-Location $resolvedProject
try {
    docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' |
        Set-Content -LiteralPath (Join-Path $destination 'mysql.sql') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'MySQL backup failed' }

    $qdrantBinding = @(docker compose port qdrant 6333)[0]
    if ($LASTEXITCODE -ne 0 -or $qdrantBinding -notmatch ':(\d+)$') {
        throw 'Unable to resolve the published Qdrant HTTP port'
    }
    $qdrantPort = $Matches[1]
    $collections = Invoke-RestMethod "http://127.0.0.1:$qdrantPort/collections"
    $snapshotIndex = @{}
    $qdrantDestination = Join-Path $destination 'qdrant'
    New-Item -ItemType Directory -Path $qdrantDestination | Out-Null
    foreach ($collection in $collections.result.collections.name) {
        $snapshot = Invoke-RestMethod -Method Post "http://127.0.0.1:$qdrantPort/collections/$collection/snapshots"
        $snapshotIndex[$collection] = $snapshot.result.name
        $snapshotFile = Join-Path $qdrantDestination ("$collection-$($snapshot.result.name)")
        Invoke-WebRequest -OutFile $snapshotFile `
            "http://127.0.0.1:$qdrantPort/collections/$collection/snapshots/$($snapshot.result.name)"
    }
    $snapshotIndex | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'qdrant-snapshots.json') -Encoding utf8

    $agentVolume = if ($env:AGENT_VOLUME_NAME) { $env:AGENT_VOLUME_NAME } else { 'sky-take-out-agent-data' }
    docker run --rm -v "${agentVolume}:/data:ro" -v "${destination}:/backup" alpine:3.22 `
        tar -czf /backup/agent-data.tar.gz -C /data .
    if ($LASTEXITCODE -ne 0) { throw 'Agent volume backup failed' }
    $checksums = Get-ChildItem -LiteralPath $destination -Recurse -File |
        Where-Object Name -notin @('SHA256SUMS.txt', 'SHA256SUMS.json') |
        ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            [pscustomobject]@{
                relative_path = [System.IO.Path]::GetRelativePath($destination, $_.FullName)
                size = $_.Length
                sha256 = $hash.Hash.ToLowerInvariant()
            }
        }
    $checksums | ConvertTo-Json | Set-Content `
        -LiteralPath (Join-Path $destination 'SHA256SUMS.json') -Encoding utf8
    $checksums | ForEach-Object { "$($_.sha256)  $($_.relative_path)" } |
        Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding utf8
} finally {
    Pop-Location
}

Write-Output $destination
