# 使用项目外私有配置调用 Docker Compose；参数原样传递，不修改配置或数据卷。
# 可在最前面传入 -EnvFile 路径；其余参数全部属于 Docker，避免 -d 被 PowerShell 当作 Debug。
param()
$ErrorActionPreference = 'Stop'
$ComposeArguments = @($args)
$EnvFile = $null
if ($ComposeArguments.Count -gt 0 -and $ComposeArguments[0] -eq '-EnvFile') {
    if ($ComposeArguments.Count -lt 2) { throw '-EnvFile 后必须提供配置路径。' }
    $EnvFile = $ComposeArguments[1]
    $ComposeArguments = @($ComposeArguments | Select-Object -Skip 2)
}
. (Join-Path $PSScriptRoot 'private-env.ps1')
$resolvedEnv = Resolve-SkyEnvFile -EnvFile $EnvFile
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & docker compose --env-file $resolvedEnv @ComposeArguments
    $composeExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $composeExitCode
