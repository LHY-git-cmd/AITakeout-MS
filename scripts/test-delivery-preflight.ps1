# 在构建或启动前检查交付环境、配置占位符、模型文件和Compose语法。
param(
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'private-env.ps1')
$resolvedEnv = Resolve-SkyEnvFile -EnvFile $EnvFile

foreach ($command in @('docker', 'git')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "缺少命令：$command"
    }
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop尚未启动' }

$values = @{}
foreach ($line in Get-Content -LiteralPath $resolvedEnv -Encoding UTF8) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $name, $value = $line -split '=', 2
    $values[$name.Trim()] = $value.Trim()
}

$required = @(
    'MYSQL_USER','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','DB_USERNAME','DB_PASSWORD',
    'REDIS_PASSWORD','JWT_ADMIN_SECRET','JWT_USER_SECRET','AGENT_INTERNAL_SERVICE_TOKEN',
    'LLM_PROVIDER','LLM_BASE_URL','LLM_MODEL','LLM_API_KEY','BOOTSTRAP_ADMIN_PASSWORD',
    'CHECKOUT_PREVIEW_SECRET'
)
foreach ($name in $required) {
    $value = [string]$values[$name]
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match 'replace-with|change-me') {
        throw "$name 仍为空或使用占位值"
    }
}
if ($values['MYSQL_USER'] -ne $values['DB_USERNAME']) {
    throw 'MYSQL_USER与DB_USERNAME必须一致'
}
if ([string]$values['MYSQL_USER'] -eq 'root') {
    throw 'MYSQL_USER/DB_USERNAME必须使用非root业务账号；Flyway会单独使用root迁移账号'
}
if ($values['MYSQL_PASSWORD'] -ne $values['DB_PASSWORD']) {
    throw 'MYSQL_PASSWORD与DB_PASSWORD必须一致'
}
foreach ($name in @('JWT_ADMIN_SECRET','JWT_USER_SECRET','AGENT_INTERNAL_SERVICE_TOKEN','CHECKOUT_PREVIEW_SECRET')) {
    $secret = [string]$values[$name]
    if ($secret.Length -lt 32) { throw "$name 必须至少32个字符" }
}

$modelPath = if ($values['BGE_MODEL_HOST_PATH']) {
    $configuredPath = [string]$values['BGE_MODEL_HOST_PATH']
    if ([System.IO.Path]::IsPathRooted($configuredPath)) { $configuredPath }
    else { Join-Path $projectRoot $configuredPath }
} else {
    Join-Path $projectRoot 'sky-embedding\models\bge-m3'
}
& (Join-Path $PSScriptRoot 'verify-bge-model.ps1') -ModelPath $modelPath | Out-Null

Push-Location $projectRoot
try {
    docker compose --env-file $resolvedEnv config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose配置无效' }
} finally { Pop-Location }

[pscustomobject]@{
    status = 'ready'
    project_root = $projectRoot
    env_file = $resolvedEnv
    model_verified = $true
} | ConvertTo-Json
