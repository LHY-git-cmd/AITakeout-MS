# 为本地 Java/Python 等开发命令临时加载私有环境变量，结束后恢复调用进程原值。
# 在 PowerShell 中以 -CommandArguments @('-jar', 'app.jar') 明确传递参数数组。
[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$EnvFile,
    [Parameter(Mandatory, Position = 0)][string]$Command,
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$CommandArguments
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'private-env.ps1')
$values = Read-SkyEnvironment -EnvFile (Resolve-SkyEnvFile -EnvFile $EnvFile)
$previous = @{}
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    & $Command @CommandArguments
    $commandExitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
exit $commandExitCode
