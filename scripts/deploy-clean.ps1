# 从源码构建并启动完整交付栈，不删除任何已有数据卷。
param(
    [string]$EnvFile,
    [int]$TimeoutSeconds = 480
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'private-env.ps1')
$resolvedEnv = Resolve-SkyEnvFile -EnvFile $EnvFile
& (Join-Path $PSScriptRoot 'test-delivery-preflight.ps1') -EnvFile $resolvedEnv | Out-Null

Push-Location $projectRoot
try {
    docker compose --env-file $resolvedEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose构建或启动失败' }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $rows = @(docker compose --env-file $resolvedEnv ps --format json |
            ForEach-Object { $_ | ConvertFrom-Json })
        $required = @('mysql','redis','qdrant','sky-embedding','sky-agent','sky-server','web')
        $ready = $true
        foreach ($service in $required) {
            $row = $rows | Where-Object Service -eq $service | Select-Object -First 1
            if (-not $row -or $row.State -ne 'running' -or ($row.Health -and $row.Health -ne 'healthy')) {
                $ready = $false
            }
        }
        if ($ready) { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    if (-not $ready) {
        docker compose --env-file $resolvedEnv ps
        throw "服务未在$TimeoutSeconds秒内全部就绪"
    }
    & (Join-Path $PSScriptRoot 'test-delivery-smoke.ps1') -EnvFile $resolvedEnv
} finally { Pop-Location }
