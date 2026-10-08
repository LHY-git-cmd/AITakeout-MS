# 统一解析项目外私有配置；只定义函数，不输出配置内容或执行外部命令。
function Resolve-SkyEnvFile {
    param([string]$EnvFile)
    $projectRoot = Split-Path -Parent $PSScriptRoot
    if ($EnvFile) { $candidate = $EnvFile }
    elseif ($env:SKY_ENV_FILE) { $candidate = $env:SKY_ENV_FILE }
    elseif (Test-Path -LiteralPath (Join-Path $projectRoot '.env') -PathType Leaf) {
        $candidate = Join-Path $projectRoot '.env'
    } else {
        $configRoot = [Environment]::GetFolderPath('LocalApplicationData')
        $candidate = Join-Path $configRoot 'metrics-backend/private-config/sky-take-out.env'
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw '找不到私有配置。请设置 SKY_ENV_FILE，或使用 -EnvFile 指定由 .env.example 填写的配置文件。'
    }
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Read-SkyEnvironment {
    param([Parameter(Mandatory)][string]$EnvFile)
    $values = @{}
    # 本地启动支持普通 KEY=VALUE 及单/双引号值；不会执行文件中的表达式。
    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
        if ($line -match '^\s*(?:#|$)') { continue }
        if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw '私有配置包含无法解析的行，请使用 KEY=VALUE 格式。'
        }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        } else { $value = ($value -replace '\s+#.*$', '').TrimEnd() }
        $values[$name] = $value
    }
    return $values
}
