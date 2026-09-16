<#
.SYNOPSIS
执行管理端 Agent 第一版的单机 Docker Compose 验收预检和可选应用场景入口。
.DESCRIPTION
用途：审计 Compose 配置、启动服务、检查容器健康状态、依赖 readiness 和内部服务令牌一致性，
      并为 12 类端到端场景保留可追踪的 PASS/BLOCKED/NOT_RUN 记录。
输入：Compose 文件、项目名、服务地址、可选场景执行器和清理执行器参数；不接收或打印密钥正文。
输出：JSON 证据报告（默认 build/reports/agent-v1-acceptance-<批次>.json）及控制台摘要。
外部调用：docker compose config/up/ps/inspect；可选 Invoke-WebRequest 健康检查；
          场景执行器和清理执行器仅在显式传入时调用。
安全约束：脚本只允许测试数据使用固定前缀；清理执行器必须返回与本批次 created_ids 完全相等的精确 ID 集合。
          禁止 docker compose down -v、删除数据库、删除 Qdrant 集合或删除命名卷。
退出码：0=配置、服务健康且所有场景通过；1=预检/健康/安全校验失败；2=存在 BLOCKED 或 NOT_RUN，验收未完成。
#>
[CmdletBinding()]
param(
    [string[]]$ComposeFile = @('compose.yml'),
    [string]$ProjectName = 'sky-take-out',
    [string]$BatchId,
    [string]$Output,
    [string]$ServerBaseUrl = 'http://127.0.0.1:8080',
    [string]$AgentBaseUrl = 'http://127.0.0.1:8000',
    [string]$EmbeddingBaseUrl = 'http://127.0.0.1:8001',
    [string]$QdrantBaseUrl = 'http://127.0.0.1:6333',
    [string]$ScenarioRunner,
    [string]$CleanupRunner,
    [switch]$NoStart,
    [switch]$StopAfter
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $BatchId) { $BatchId = 'agent-v1-acceptance-' + (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') }
if ($BatchId -notmatch '^agent-v1-acceptance-[A-Za-z0-9-]+$') { throw 'BatchId must use the fixed agent-v1-acceptance- prefix' }
if (-not $Output) { $Output = Join-Path $projectRoot ("build/reports/{0}.json" -f $BatchId) }
if (-not [IO.Path]::IsPathRooted($Output)) { $Output = Join-Path $projectRoot $Output }
$reportDirectory = Split-Path -Parent $Output
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null

function Invoke-Compose {
    param([string[]]$Arguments)
    $args = @('--project-name', $ProjectName)
    foreach ($file in $ComposeFile) { $args += @('--file', (Join-Path $projectRoot $file)) }
    $args += $Arguments
    $script:lastComposeOutput = @(& docker compose @args 2>&1)
    return $LASTEXITCODE
}

function Add-Check {
    param([string]$Name, [string]$Status, [string]$Detail)
    $script:checks += [ordered]@{ name = $Name; status = $Status; detail = $Detail }
    if ($Status -eq 'FAIL') { $script:failed = $true }
}

function Test-HttpReady {
    param([string]$Name, [string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 10 -UseBasicParsing
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
            Add-Check $Name 'PASS' ("HTTP {0}" -f $response.StatusCode)
        } else { Add-Check $Name 'FAIL' ("HTTP {0}" -f $response.StatusCode) }
    } catch { Add-Check $Name 'FAIL' 'endpoint unavailable or returned a non-success status' }
}

function Test-NonEmptyEvidence([object]$Value, [switch]$RequireSequence, [switch]$RequireFinalState) {
    if ($null -eq $Value) { return $false }
    if ($Value -is [string]) { return (-not $RequireSequence -and -not $RequireFinalState -and -not [string]::IsNullOrWhiteSpace($Value)) }
    if ($Value -is [System.Collections.IDictionary]) {
        if ($Value.Count -eq 0) { return $false }
        $properties = @($Value.Keys)
    } elseif ($Value -is [pscustomobject]) {
        $properties = @($Value.PSObject.Properties.Name)
        if ($properties.Count -eq 0) { return $false }
    } elseif ($Value -is [System.Collections.IEnumerable]) {
        return @($Value).Count -gt 0
    } else { return $false }
    if ($RequireFinalState) {
        $stateKeys = @($properties | Where-Object { $_ -match '(?i)java|mysql|status|state' })
        if ($stateKeys.Count -eq 0) { return $false }
        $nonEmpty = $false
        foreach ($key in $stateKeys) {
            $stateValue = if ($Value -is [System.Collections.IDictionary]) { $Value[$key] } else { $Value.PSObject.Properties[$key].Value }
            if ($null -ne $stateValue -and (-not ($stateValue -is [string]) -or -not [string]::IsNullOrWhiteSpace($stateValue))) { $nonEmpty = $true; break }
        }
        if (-not $nonEmpty) { return $false }
    }
    return $true
}

function Get-EvidenceField([object]$Evidence, [string[]]$Names) {
    foreach ($name in $Names) {
        if ($Evidence -is [System.Collections.IDictionary] -and $Evidence.Contains($name)) { return $Evidence[$name] }
        if ($Evidence -is [pscustomobject] -and $Evidence.PSObject.Properties.Name -contains $name) { return $Evidence.PSObject.Properties[$name].Value }
    }
    return $null
}

function Get-ContainerTokenHash([string]$Service) {
    $composePrefix = @('--project-name', $ProjectName)
    foreach ($file in $ComposeFile) { $composePrefix += @('--file', (Join-Path $projectRoot $file)) }
    try { $lines = & docker compose @composePrefix ps -q $Service 2>$null } catch { return $null }
    $containerId = (($lines | Select-Object -Last 1) | Out-String).Trim()
    if (-not $containerId) { return $null }
    try { $envLines = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $containerId 2>$null } catch { return $null }
    $entry = $envLines | Where-Object { $_ -like 'AGENT_INTERNAL_SERVICE_TOKEN=*' } | Select-Object -First 1
    if (-not $entry) { return $null }
    $token = $entry.Substring('AGENT_INTERNAL_SERVICE_TOKEN='.Length)
    $bytes = [Text.Encoding]::UTF8.GetBytes($token)
    return ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
}

$checks = @(); $failed = $false; $createdIds = @(); $scenarioStatuses = @(); $tokenHashExpected = $null
Push-Location $projectRoot
try {
    $configCode = Invoke-Compose @('config', '--quiet')
    Add-Check 'compose-config' ($(if ($configCode -eq 0) { 'PASS' } else { 'FAIL' })) 'Compose configuration parsed without rendering secrets'

    $envPath = Join-Path $projectRoot '.env'
    $tokenEntry = if (Test-Path $envPath) { Get-Content $envPath | Where-Object { $_ -match '^AGENT_INTERNAL_SERVICE_TOKEN=' } | Select-Object -First 1 } else { $null }
    $token = if ($tokenEntry) { $tokenEntry.Substring('AGENT_INTERNAL_SERVICE_TOKEN='.Length).Trim() } else { '' }
    if ($token.Length -ge 32 -and $token -notmatch 'replace-with|change-me') {
        $localHash = ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash([Text.Encoding]::UTF8.GetBytes($token)))).Replace('-', '').ToLowerInvariant()
        Add-Check 'internal-token-configured' 'PASS' 'token length and placeholder checks passed; value not recorded'
        if (-not $NoStart) { $tokenHashExpected = $localHash }
        else { Add-Check 'internal-token-consistency' 'NOT_RUN' 'NoStart was specified' }
    } else { Add-Check 'internal-token-configured' 'FAIL' 'AGENT_INTERNAL_SERVICE_TOKEN is missing, too short, or still a placeholder' }

    if (-not $NoStart) {
        $upCode = Invoke-Compose @('up', '-d', '--build')
        Add-Check 'compose-up' ($(if ($upCode -eq 0) { 'PASS' } else { 'FAIL' })) 'Compose services requested; no volumes were removed'
        if ($upCode -eq 0 -and $tokenHashExpected) {
            $agentHash = Get-ContainerTokenHash 'sky-agent'; $serverHash = Get-ContainerTokenHash 'sky-server'
            if ($agentHash -and $serverHash -and $agentHash -eq $tokenHashExpected -and $serverHash -eq $tokenHashExpected) {
                Add-Check 'internal-token-consistency' 'PASS' 'SHA-256 hashes match across .env and running containers'
            } else { Add-Check 'internal-token-consistency' 'FAIL' 'token hash missing or inconsistent; secret value was not printed' }
        }
    } else { Add-Check 'compose-up' 'NOT_RUN' 'NoStart was specified' }

    $psCode = Invoke-Compose @('ps')
    Add-Check 'compose-ps' ($(if ($psCode -eq 0) { 'PASS' } else { 'FAIL' })) 'Container status command completed'
    if (-not $NoStart -and $psCode -eq 0) {
        [void](Invoke-Compose @('ps', '--format', 'json'))
        try { $rows = @($lastComposeOutput | ConvertFrom-Json) }
        catch { Add-Check 'compose-ps-json' 'FAIL' 'docker compose ps JSON output could not be parsed'; $rows = @() }
        $required = @('mysql', 'redis', 'qdrant', 'sky-embedding', 'sky-agent', 'sky-server')
        foreach ($service in $required) {
            $row = $rows | Where-Object { $_.Service -eq $service -or $_.Name -like "*$service*" } | Select-Object -First 1
            $health = if ($row) { [string]$row.Health } else { '' }
            if ($health -eq 'healthy') { Add-Check ("health-$service") 'PASS' 'container reports healthy' }
            else { Add-Check ("health-$service") 'FAIL' ("expected healthy, observed '{0}'" -f $health) }
        }
        Test-HttpReady 'server-readiness' "$ServerBaseUrl/actuator/health/readiness"
        Test-HttpReady 'agent-readiness' "$AgentBaseUrl/health/ready"
        Test-HttpReady 'embedding-readiness' "$EmbeddingBaseUrl/health/ready"
        Test-HttpReady 'qdrant-health' "$QdrantBaseUrl/healthz"
    }

    $scenarioIds = 1..12 | ForEach-Object { 'E2E-{0:D2}' -f $_ }
    if ($ScenarioRunner) {
        $runnerPath = (Resolve-Path $ScenarioRunner).Path
        $runnerReport = Join-Path $reportDirectory ("{0}-scenarios.json" -f $BatchId)
        & pwsh -NoProfile -File $runnerPath -BatchId $BatchId -Prefix $BatchId -OutputPath $runnerReport
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $runnerReport)) { Add-Check 'scenario-runner' 'FAIL' 'scenario runner failed or did not produce a report' }
        else {
            $scenarioParsed = $true
            try {
                $scenarioData = Get-Content -Raw $runnerReport | ConvertFrom-Json
                $scenarioStatuses = @($scenarioData.scenarios)
                $createdIds = @($scenarioData.created_ids)
            } catch {
                Add-Check 'scenario-schema' 'FAIL' 'scenario runner report is not valid JSON'; $scenarioStatuses = @(); $createdIds = @(); $scenarioParsed = $false
            }
            if ($scenarioParsed) {
                $invalid = @($createdIds | Where-Object { $_ -notlike "$BatchId*" })
                if ($invalid.Count -gt 0) { Add-Check 'created-data-prefix' 'FAIL' 'runner returned an ID outside the fixed batch prefix' }
                else { Add-Check 'created-data-prefix' 'PASS' 'all created IDs use the fixed batch prefix' }
                $expectedSet = @($scenarioIds)
                $actualIds = @($scenarioStatuses | ForEach-Object { $_.id })
                $missing = @($expectedSet | Where-Object { $_ -notin $actualIds })
                $unexpected = @($actualIds | Where-Object { $_ -notin $expectedSet })
                $duplicates = @($actualIds | Group-Object | Where-Object { $_.Count -gt 1 })
                if ($missing.Count -gt 0 -or $unexpected.Count -gt 0 -or $duplicates.Count -gt 0) {
                    Add-Check 'scenario-completeness' 'FAIL' 'runner must return exactly one result for each E2E-01..E2E-12'
                } else { Add-Check 'scenario-completeness' 'PASS' 'exactly 12 scenario results were returned' }
                $requiredFields = @('status', 'evidence', 'task_id', 'session_id', 'admin_role', 'event_sequence', 'final_state')
                foreach ($scenario in $scenarioStatuses) {
                    if ([string]$scenario.status -ne 'PASS') { continue }
                    $missingFields = @($requiredFields | Where-Object {
                        $property = $scenario.PSObject.Properties[$_]
                        if ($null -eq $property -or $null -eq $property.Value) { return $true }
                        if ($_ -eq 'event_sequence') { return -not (Test-NonEmptyEvidence $property.Value -RequireSequence) }
                        if ($_ -eq 'final_state') { return -not (Test-NonEmptyEvidence $property.Value -RequireFinalState) }
                        return -not (Test-NonEmptyEvidence $property.Value)
                    })
                    $evidenceProperty = $scenario.PSObject.Properties['evidence']
                    $evidence = if ($evidenceProperty) { $evidenceProperty.Value } else { $null }
                    $extraEvidenceMissing = @()
                    if ($scenario.id -in @('E2E-05','E2E-06','E2E-07','E2E-08')) {
                        if (-not (Test-NonEmptyEvidence (Get-EvidenceField $evidence @('confirmation_id','confirmationId')))) { $extraEvidenceMissing += 'confirmation_id' }
                        if (-not (Test-NonEmptyEvidence (Get-EvidenceField $evidence @('audit_id','auditId','audit_record','auditRecord')))) { $extraEvidenceMissing += 'audit_id/audit_record' }
                    }
                    if ($scenario.id -in @('E2E-08','E2E-10','E2E-11')) {
                        if (-not (Test-NonEmptyEvidence (Get-EvidenceField $evidence @('recovery_evidence','recoveryEvidence','health_evidence','healthEvidence')))) { $extraEvidenceMissing += 'recovery_evidence/health_evidence' }
                    }
                    $missingFields += $extraEvidenceMissing
                    if ($missingFields.Count -gt 0) {
                        Add-Check ("scenario-evidence-$($scenario.id)") 'FAIL' ("PASS scenario is missing evidence fields: {0}" -f ($missingFields -join ', '))
                    }
                }
            }
        }
    } else {
        foreach ($id in $scenarioIds) { $scenarioStatuses += [ordered]@{ id = $id; status = 'NOT_RUN'; evidence = $null; note = 'No ScenarioRunner was supplied; user execution required' } }
        Add-Check 'application-scenarios' 'NOT_RUN' '12 E2E scenarios were not executed; no pass is inferred'
    }

    if ($CleanupRunner -and $createdIds.Count -gt 0) {
        $manifest = Join-Path $reportDirectory ("{0}-cleanup-manifest.json" -f $BatchId)
        $cleanupReport = Join-Path $reportDirectory ("{0}-cleanup.json" -f $BatchId)
        @{ batch_id = $BatchId; prefix = $BatchId; created_ids = $createdIds } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $manifest
        & pwsh -NoProfile -File (Resolve-Path $CleanupRunner).Path -ManifestPath $manifest -Prefix $BatchId -OutputPath $cleanupReport
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cleanupReport)) { Add-Check 'cleanup' 'FAIL' 'cleanup runner failed or did not produce evidence' }
        else {
            try {
                $cleanupData = Get-Content -Raw $cleanupReport | ConvertFrom-Json
                $deletedIds = @($cleanupData.deleted_ids)
                $createdUnique = @($createdIds | Sort-Object -Unique)
                $deletedUnique = @($deletedIds | Sort-Object -Unique)
                $sameSet = ($createdUnique.Count -gt 0 -and $createdUnique.Count -eq $deletedUnique.Count -and (@(Compare-Object $createdUnique $deletedUnique).Count -eq 0) -and $deletedIds.Count -eq $deletedUnique.Count)
                if (-not $sameSet) { Add-Check 'cleanup' 'FAIL' 'deleted_ids must exactly equal the unique created_ids set' }
                else { Add-Check 'cleanup' 'PASS' 'cleanup runner completed against exactly the created IDs' }
            } catch { Add-Check 'cleanup' 'FAIL' 'cleanup report is not valid JSON or lacks deleted_ids' }
        }
    } elseif ($createdIds.Count -eq 0) { Add-Check 'cleanup' 'NOT_RUN' 'no created IDs were reported; nothing was deleted' }
    else { Add-Check 'cleanup' 'BLOCKED' 'created IDs exist but no CleanupRunner was supplied' }
}
finally {
    if ($StopAfter -and -not $NoStart) { [void](Invoke-Compose @('stop')) }
    Pop-Location
}

$report = [ordered]@{
    batch_id = $BatchId; generated_at = (Get-Date).ToUniversalTime().ToString('o'); project = $ProjectName
    checks = $checks; scenarios = $scenarioStatuses; created_ids = $createdIds
    safety = [ordered]@{ destructive_compose_commands = $false; fixed_prefix = $BatchId; volumes_removed = $false }
}
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $Output
Write-Host ("Acceptance evidence written to {0}" -f $Output)
if ($failed) { exit 1 }
if (@($checks | Where-Object { $_.status -in @('NOT_RUN', 'BLOCKED') }).Count -gt 0 -or @($scenarioStatuses | Where-Object { $_.status -ne 'PASS' }).Count -gt 0) { exit 2 }
exit 0
