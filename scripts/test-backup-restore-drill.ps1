$ErrorActionPreference = 'Stop'
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$mysqlName = "sky-drill-mysql-$suffix"
$qdrantName = "sky-drill-qdrant-$suffix"
$agentVolume = "sky-drill-agent-$suffix"
$temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$drillRoot = [System.IO.Path]::GetFullPath((Join-Path $temporaryBase "sky-runtime-drill-$suffix"))
if (-not $drillRoot.StartsWith($temporaryBase) -or (Split-Path $drillRoot -Leaf) -notlike 'sky-runtime-drill-*') {
    throw 'Unsafe drill directory'
}
New-Item -ItemType Directory -Path $drillRoot | Out-Null

try {
    docker run -d --name $mysqlName -e MYSQL_ROOT_PASSWORD=drillpass `
        -e MYSQL_DATABASE=drill mysql:8.4.7 | Out-Null
    $mysqlReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $mysqlName mysql -N -uroot -pdrillpass -e 'SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $mysqlReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $mysqlReady) { throw 'MySQL drill container did not become ready' }
    docker exec $mysqlName mysql -uroot -pdrillpass drill `
        -e 'CREATE TABLE proof(value VARCHAR(32)); INSERT INTO proof VALUES ("mysql-ok");'
    $mysqlDump = Join-Path $drillRoot 'mysql.sql'
    docker exec $mysqlName mysqldump -uroot -pdrillpass drill |
        Set-Content -LiteralPath $mysqlDump -Encoding utf8
    docker exec $mysqlName mysql -uroot -pdrillpass `
        -e 'DROP DATABASE drill; CREATE DATABASE drill;'
    Get-Content -LiteralPath $mysqlDump -Raw |
        docker exec -i $mysqlName mysql -uroot -pdrillpass drill
    $mysqlProof = docker exec $mysqlName mysql -N -uroot -pdrillpass drill `
        -e 'SELECT value FROM proof LIMIT 1;'
    if ($mysqlProof.Trim() -ne 'mysql-ok') { throw 'MySQL restore verification failed' }

    docker volume create $agentVolume | Out-Null
    docker run --rm -v "${agentVolume}:/data" alpine:3.22 `
        sh -c 'printf agent-ok > /data/state.txt'
    docker run --rm -v "${agentVolume}:/data:ro" -v "${drillRoot}:/backup" alpine:3.22 `
        tar -czf /backup/agent-data.tar.gz -C /data .
    docker volume rm $agentVolume | Out-Null
    docker volume create $agentVolume | Out-Null
    docker run --rm -v "${agentVolume}:/data" -v "${drillRoot}:/backup:ro" alpine:3.22 `
        tar -xzf /backup/agent-data.tar.gz -C /data
    $agentProof = docker run --rm -v "${agentVolume}:/data:ro" alpine:3.22 cat /data/state.txt
    if ($agentProof.Trim() -ne 'agent-ok') { throw 'Agent volume restore verification failed' }

    docker run -d --name $qdrantName -p 127.0.0.1::6333 qdrant/qdrant:v1.15.5 | Out-Null
    $binding = docker port $qdrantName 6333/tcp
    $qdrantPort = ($binding -split ':')[-1]
    $qdrantReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            Invoke-RestMethod "http://127.0.0.1:$qdrantPort/healthz" | Out-Null
            $qdrantReady = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $qdrantReady) { throw 'Qdrant drill container did not become ready' }
    Invoke-RestMethod -Method Put -ContentType 'application/json' `
        -Body '{"vectors":{"size":4,"distance":"Cosine"}}' `
        "http://127.0.0.1:$qdrantPort/collections/drill" | Out-Null
    Invoke-RestMethod -Method Put -ContentType 'application/json' `
        -Body '{"points":[{"id":1,"vector":[1,0,0,0],"payload":{"proof":"qdrant-ok"}}]}' `
        "http://127.0.0.1:$qdrantPort/collections/drill/points?wait=true" | Out-Null
    $snapshot = Invoke-RestMethod -Method Post `
        "http://127.0.0.1:$qdrantPort/collections/drill/snapshots"
    $snapshotFile = Join-Path $drillRoot $snapshot.result.name
    Invoke-WebRequest -OutFile $snapshotFile `
        "http://127.0.0.1:$qdrantPort/collections/drill/snapshots/$($snapshot.result.name)"
    Invoke-RestMethod -Method Delete "http://127.0.0.1:$qdrantPort/collections/drill" | Out-Null
    curl.exe --silent --show-error --fail -X POST `
        -F "snapshot=@$snapshotFile" `
        "http://127.0.0.1:$qdrantPort/collections/drill/snapshots/upload?priority=snapshot" | Out-Null
    $qdrantProof = Invoke-RestMethod `
        "http://127.0.0.1:$qdrantPort/collections/drill/points/1"
    if ($qdrantProof.result.payload.proof -ne 'qdrant-ok') {
        throw 'Qdrant restore verification failed'
    }

    Write-Output 'DRILL_SUCCESS: mysql, qdrant, agent-volume'
} finally {
    docker rm -f $mysqlName $qdrantName 2>$null | Out-Null
    docker volume rm $agentVolume 2>$null | Out-Null
    $safeBase = $drillRoot.StartsWith($temporaryBase)
    $safeLeaf = (Split-Path $drillRoot -Leaf) -like 'sky-runtime-drill-*'
    if ((Test-Path -LiteralPath $drillRoot) -and $safeBase -and $safeLeaf) {
        Remove-Item -LiteralPath $drillRoot -Recurse -Force
    }
}
