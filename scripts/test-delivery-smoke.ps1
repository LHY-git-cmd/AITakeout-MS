# 对已启动的交付栈执行只读冒烟检查：服务、前端、Flyway和核心表。
param(
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'private-env.ps1')
$resolvedEnv = Resolve-SkyEnvFile -EnvFile $EnvFile
$values = @{}
foreach ($line in Get-Content -LiteralPath $resolvedEnv -Encoding UTF8) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $name, $value = $line -split '=', 2
    $values[$name.Trim()] = $value.Trim()
}

$serverPort = if ($values['SKY_SERVER_PORT']) { $values['SKY_SERVER_PORT'] } else { '18080' }
$agentPort = if ($values['SKY_AGENT_PORT']) { $values['SKY_AGENT_PORT'] } else { '18000' }
$embeddingPort = if ($values['SKY_EMBEDDING_PORT']) { $values['SKY_EMBEDDING_PORT'] } else { '18001' }
$qdrantPort = if ($values['QDRANT_HTTP_PORT']) { $values['QDRANT_HTTP_PORT'] } else { '16333' }
$adminPort = if ($values['ADMIN_WEB_PORT']) { $values['ADMIN_WEB_PORT'] } else { '80' }
$userPort = if ($values['USER_WEB_PORT']) { $values['USER_WEB_PORT'] } else { '8081' }

$checks = [ordered]@{
    java = "http://127.0.0.1:$serverPort/actuator/health/readiness"
    agent = "http://127.0.0.1:$agentPort/health/ready"
    embedding = "http://127.0.0.1:$embeddingPort/health/ready"
    qdrant = "http://127.0.0.1:$qdrantPort/healthz"
    admin_web = "http://127.0.0.1:$adminPort/"
    user_web = "http://127.0.0.1:$userPort/"
}
$results = @()
foreach ($entry in $checks.GetEnumerator()) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $entry.Value -TimeoutSec 15
    if ($response.StatusCode -ne 200) { throw "$($entry.Key) 健康检查失败" }
    $results += [pscustomobject]@{ name=$entry.Key; url=$entry.Value; status=$response.StatusCode }
}

Push-Location $projectRoot
try {
    # SQL通过标准输入传递，避免多层命令行引号导致mysql的-e参数被截断。
    $sql = @'
select count(*) from flyway_schema_history where success=0;
select count(*) from employee;
select count(*) from dish;
select count(*) from orders;
select count(*) from user;
'@
    # 密码在容器内通过环境变量传递；为旧版PowerShell保留原生命令参数中的双引号。
    $mysqlCommand = 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N --user="$MYSQL_USER" --database="$MYSQL_DATABASE"'
    if ($PSVersionTable.PSVersion.Major -lt 7 -or
        -not (Get-Variable PSNativeCommandArgumentPassing -ErrorAction SilentlyContinue) -or
        $PSNativeCommandArgumentPassing -eq 'Legacy') {
        $mysqlCommand = $mysqlCommand.Replace('"', '\"')
    }
    $sqlResult = $sql | docker compose --env-file $resolvedEnv exec -T mysql sh -c $mysqlCommand
    if ($LASTEXITCODE -ne 0) { throw '数据库冒烟查询失败' }
    $numbers = @($sqlResult | Where-Object { $_ -match '^\d+$' })
    if ($numbers.Count -lt 5 -or [int]$numbers[0] -ne 0) {
        throw "Flyway或核心表检查失败：$($numbers -join ',')"
    }
} finally { Pop-Location }

[pscustomobject]@{
    status = 'passed'
    checked_at = (Get-Date).ToString('s')
    endpoints = $results
    failed_migrations = 0
    core_tables = 4
} | ConvertTo-Json -Depth 4
