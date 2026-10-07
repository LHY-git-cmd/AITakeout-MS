# 使用临时MySQL和Redis验证：核心基线、全部Flyway迁移和首个管理员能从空环境启动。
param(
    [string]$JarPath = (Join-Path $PSScriptRoot '..\sky-server\target\sky-server-1.0-SNAPSHOT.jar')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$baseline = (Resolve-Path (Join-Path $projectRoot 'deploy\mysql\001-core-schema.sql')).Path
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$mysqlName = "sky-clean-mysql-$suffix"
$redisName = "sky-clean-redis-$suffix"
$temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$runtimeRoot = [System.IO.Path]::GetFullPath((Join-Path $temporaryBase "sky-clean-bootstrap-$suffix"))
$javaProcess = $null

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

try {
    New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
    docker run -d --name $mysqlName -p 127.0.0.1::3306 `
        -e MYSQL_DATABASE=sky_clean -e MYSQL_USER=sky_clean `
        -e MYSQL_PASSWORD=CleanDbPass2026 -e MYSQL_ROOT_PASSWORD=CleanRootPass2026 `
        -v "${baseline}:/docker-entrypoint-initdb.d/001-core-schema.sql:ro" mysql:8.4.7 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '临时MySQL启动失败' }
    docker run -d --name $redisName -p 127.0.0.1::6379 redis:7.4.7-alpine `
        redis-server --requirepass CleanRedisPass2026 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '临时Redis启动失败' }

    $mysqlPort = ((docker port $mysqlName 3306/tcp) -split ':')[-1]
    $redisPort = ((docker port $redisName 6379/tcp) -split ':')[-1]
    $mysqlReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $mysqlName mysql -N -usky_clean -pCleanDbPass2026 sky_clean -e 'select 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $mysqlReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $mysqlReady) { throw '临时MySQL未就绪' }

    $serverPort = Get-FreeTcpPort
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SERVER_PORT = [string]$serverPort
    $env:DB_HOST = '127.0.0.1'
    $env:DB_PORT = [string]$mysqlPort
    $env:DB_NAME = 'sky_clean'
    $env:DB_USERNAME = 'sky_clean'
    $env:DB_PASSWORD = 'CleanDbPass2026'
    $env:SPRING_FLYWAY_USER = 'root'
    $env:SPRING_FLYWAY_PASSWORD = 'CleanRootPass2026'
    $env:REDIS_HOST = '127.0.0.1'
    $env:REDIS_PORT = [string]$redisPort
    $env:REDIS_PASSWORD = 'CleanRedisPass2026'
    $env:REDIS_DATABASE = '0'
    $env:JWT_ADMIN_SECRET = 'clean-admin-jwt-secret-at-least-32-characters'
    $env:JWT_USER_SECRET = 'clean-user-jwt-secret-at-least-32-characters'
    $env:AGENT_INTERNAL_SERVICE_TOKEN = 'clean-agent-internal-token-at-least-32-chars'
    $env:LLM_MODEL = 'clean-disabled'
    $env:LLM_BASE_URL = 'http://127.0.0.1:9'
    $env:LLM_API_KEY = 'clean-placeholder-key'
    $env:AI_ENABLED = 'false'
    $env:BOOTSTRAP_ADMIN_ENABLED = 'true'
    $env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
    $env:BOOTSTRAP_ADMIN_PASSWORD = 'TemporaryStrongAdminPassword!2026'
    $env:BOOTSTRAP_ADMIN_NAME = 'Clean Admin'
    $env:CHECKOUT_PREVIEW_SECRET = 'clean-checkout-preview-secret-at-least-32-chars'
    $env:DELIVERY_MAP_PROVIDER = 'mock'
    $env:PRODUCT_IMAGE_LOCAL_ROOT = Join-Path $runtimeRoot 'uploads\products'

    $stdout = Join-Path $runtimeRoot 'server.out.log'
    $stderr = Join-Path $runtimeRoot 'server.err.log'
    $javaProcess = Start-Process -FilePath 'java' -ArgumentList '-jar', $resolvedJar `
        -WorkingDirectory $projectRoot -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr -WindowStyle Hidden -PassThru

    $healthy = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($javaProcess.HasExited) { break }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$serverPort/actuator/health"
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $healthy) {
        Get-Content -LiteralPath $stderr -Tail 100 -ErrorAction SilentlyContinue
        Get-Content -LiteralPath $stdout -Tail 100 -ErrorAction SilentlyContinue
        throw 'Java服务未能从干净数据库启动'
    }

    $result = docker exec $mysqlName mysql -N -usky_clean -pCleanDbPass2026 sky_clean -e `
        'select count(*) from flyway_schema_history where success=0; select max(version) from flyway_schema_history where success=1; select count(*) from employee; select role from employee limit 1; select left(password,2) from employee limit 1;'
    $values = @($result | Where-Object { $_ -notmatch '^mysql:' })
    if ([int]$values[0] -ne 0) { throw '存在失败的Flyway迁移' }
    if ($values[1] -ne '20261003.01') { throw "最高迁移版本不正确：$($values[1])" }
    if ([int]$values[2] -ne 1 -or $values[3] -ne 'SUPER_ADMIN' -or $values[4] -ne '$2') {
        throw '首个超级管理员创建结果不正确'
    }

    [pscustomobject]@{
        status = 'passed'
        java_health = 'UP'
        latest_migration = $values[1]
        bootstrap_admins = [int]$values[2]
        bootstrap_role = $values[3]
        password_hash = 'BCrypt'
    } | ConvertTo-Json
} finally {
    if ($javaProcess -and -not $javaProcess.HasExited) { Stop-Process -Id $javaProcess.Id -Force }
    docker rm -f $mysqlName $redisName 2>$null | Out-Null
    $safeRoot = $runtimeRoot.StartsWith($temporaryBase)
    $safeLeaf = (Split-Path $runtimeRoot -Leaf) -like 'sky-clean-bootstrap-*'
    if ((Test-Path -LiteralPath $runtimeRoot) -and $safeRoot -and $safeLeaf) {
        Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
    }
}
