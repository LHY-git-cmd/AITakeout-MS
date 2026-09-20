<#
.SYNOPSIS
启动用户端移动 E2E 的独立 MySQL、Redis、后端和 Vite 环境，执行 Playwright 后自动清理。

.DESCRIPTION
该脚本只创建本次运行所需的临时容器和进程，不读取或修改开发数据库；无论成功失败都会在 finally 中回收。
#>
param(
    [ValidateSet('all', 'mobile-chrome', 'mobile-webkit')]
    [string]$Project = 'all'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'frontend/project-sky-user-web'
$fixturePath = Join-Path $PSScriptRoot 'e2e-user-fixture.sql'
$logRoot = Join-Path $repoRoot 'target/user-e2e-logs'
$mysqlContainer = "sky-user-e2e-mysql-$PID"
$redisContainer = "sky-user-e2e-redis-$PID"
$mysqlPort = 13316
$redisPort = 16380
$backendPort = 18080
$frontendPort = 15173
$backendProcess = $null
$frontendProcess = $null

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [Parameter(Mandatory = $true)][string]$Description,
        [int]$TimeoutSeconds = 90,
        [System.Diagnostics.Process]$WatchedProcess
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if (& $Condition) { return }
        } catch {
            # 服务启动期间连接失败属于预期，继续等待到统一超时。
        }
        if ($null -ne $WatchedProcess) {
            $WatchedProcess.Refresh()
            if ($WatchedProcess.HasExited) {
                throw "$Description对应的进程已提前退出，退出码 $($WatchedProcess.ExitCode)"
            }
        }
        Start-Sleep -Seconds 1
    }
    throw "等待$Description超时（${TimeoutSeconds}秒）"
}

function Assert-PortAvailable {
    param([int]$Port)
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        throw "端口 $Port 已被占用，请先停止占用进程后重试"
    }
}

function Stop-ProcessTree {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process -or $Process.HasExited) { return }
    & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
}

try {
    Assert-PortAvailable $mysqlPort
    Assert-PortAvailable $redisPort
    Assert-PortAvailable $backendPort
    Assert-PortAvailable $frontendPort
    New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

    & docker run --rm -d --name $mysqlContainer `
        -e MYSQL_DATABASE=sky_e2e `
        -e MYSQL_ROOT_PASSWORD=sky-e2e-root `
        -p "127.0.0.1:${mysqlPort}:3306" mysql:8.4.7 | Out-Null
    & docker run --rm -d --name $redisContainer `
        -p "127.0.0.1:${redisPort}:6379" redis:7.4.7-alpine | Out-Null

    Wait-Until -Description 'MySQL 就绪' -Condition {
        & docker exec $mysqlContainer sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>$null
        return $LASTEXITCODE -eq 0
    }
    Wait-Until -Description 'Redis 就绪' -Condition {
        & docker exec $redisContainer redis-cli ping 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    }

    Get-Content -Raw $fixturePath | & docker exec -i $mysqlContainer sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot sky_e2e'
    if ($LASTEXITCODE -ne 0) { throw '导入用户端 E2E 数据夹具失败' }

    Push-Location $repoRoot
    try {
        & mvn -pl sky-server -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw '后端打包失败' }
    } finally {
        Pop-Location
    }

    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SPRING_FLYWAY_ENABLED = 'false'
    $env:DB_HOST = '127.0.0.1'
    $env:DB_PORT = [string]$mysqlPort
    $env:DB_NAME = 'sky_e2e'
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = 'sky-e2e-root'
    $env:REDIS_HOST = '127.0.0.1'
    $env:REDIS_PORT = [string]$redisPort
    $env:REDIS_DATABASE = '0'
    $env:REDIS_PASSWORD = ''
    $env:JWT_ADMIN_SECRET = 'e2e-admin-secret-key-e2e-admin-secret-key'
    $env:JWT_USER_SECRET = 'e2e-user-secret-key-e2e-user-secret-key'
    $env:LLM_MODEL = 'e2e-disabled'
    $env:AI_ENABLED = 'false'
    $env:RAG_ENABLED = 'false'
    $env:SKY_AGENT_RECOVERY_ENABLED = 'false'
    $env:BAIDU_MAP_AK = ''
    $env:SKY_PAYMENT_MOCK_DELAY_MS = '200'
    $env:SKY_PAYMENT_RECONCILE_DELAY_MS = '100'
    $env:USER_API_TARGET = "http://127.0.0.1:${backendPort}"
    $env:USER_E2E_BASE_URL = "http://127.0.0.1:${frontendPort}"

    $backendProcess = Start-Process -FilePath 'java' -ArgumentList @(
        '-jar', (Join-Path $repoRoot 'sky-server/target/sky-server-1.0-SNAPSHOT.jar'),
        "--server.port=$backendPort"
    ) -WorkingDirectory $repoRoot -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot 'backend.stdout.log') `
        -RedirectStandardError (Join-Path $logRoot 'backend.stderr.log')

    Wait-Until -Description '后端健康检查通过' -TimeoutSeconds 120 -WatchedProcess $backendProcess -Condition {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:${backendPort}/actuator/health"
        return $response.StatusCode -eq 200
    }

    $frontendProcess = Start-Process -FilePath 'npm.cmd' -ArgumentList @(
        'run', 'dev', '--', '--host', '127.0.0.1', '--port', [string]$frontendPort, '--strictPort'
    ) -WorkingDirectory $frontendRoot -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot 'frontend.stdout.log') `
        -RedirectStandardError (Join-Path $logRoot 'frontend.stderr.log')

    Wait-Until -Description 'Vite 页面可访问' -WatchedProcess $frontendProcess -Condition {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:${frontendPort}/"
        return $response.StatusCode -eq 200
    }

    Push-Location $frontendRoot
    try {
        if ($Project -eq 'all') {
            & npx playwright test e2e/auth-payment.spec.ts e2e/refund-notification.spec.ts
        } else {
            & npx playwright test --project=$Project e2e/auth-payment.spec.ts e2e/refund-notification.spec.ts
        }
        if ($LASTEXITCODE -ne 0) { throw '用户端 Playwright E2E 执行失败' }
    } finally {
        Pop-Location
    }
} catch {
    Write-Host "用户端 E2E 失败：$($_.Exception.Message)" -ForegroundColor Red
    if (Test-Path (Join-Path $logRoot 'backend.stderr.log')) {
        Write-Host '后端错误日志末尾：'
        Get-Content (Join-Path $logRoot 'backend.stderr.log') -Tail 80
    }
    throw
} finally {
    Stop-ProcessTree $frontendProcess
    Stop-ProcessTree $backendProcess
    & docker rm -f $redisContainer 2>$null | Out-Null
    & docker rm -f $mysqlContainer 2>$null | Out-Null
}
