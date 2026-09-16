<#
.SYNOPSIS
运行 RAG 与 Agent 工具质量门禁，任一失败整体失败。
.DESCRIPTION
输入：Mode、RealSampleSize、Python 解释器、两个报告路径及可选数据集/服务地址。
输出：RAG/工具独立 JSON 报告；默认文件名区分 mock 与 real。
外部调用：RAG 使用 Embedding/Qdrant；real 另调用模型。工具业务始终为内存 Mock。
退出码：0 两项均通过；1 任一门禁、依赖或配置失败。不会跳过失败后的另一项。
#>
param(
    [ValidateSet('mock', 'real')]
    [string]$Mode = 'mock',
    [ValidateRange(12, 10000)]
    [int]$RealSampleSize = 12,
    [string]$Python = 'python',
    [string]$Output,
    [string]$ToolOutput,
    [string]$RagDataset,
    [string]$ToolDataset,
    [string]$QdrantUrl,
    [string]$EmbeddingUrl
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$agentRoot = Join-Path $projectRoot 'agent-service'
if (-not $Output) { $Output = "build/reports/rag-eval-$Mode.json" }
if (-not $ToolOutput) { $ToolOutput = "build/reports/tool-eval-$Mode.json" }

function Resolve-ProjectPath([string]$Value) {
    if ([IO.Path]::IsPathRooted($Value)) { return [IO.Path]::GetFullPath($Value) }
    return [IO.Path]::GetFullPath((Join-Path $projectRoot $Value))
}

$ragPath = Resolve-ProjectPath $Output
$toolPath = Resolve-ProjectPath $ToolOutput
if ($ragPath -eq $toolPath) { throw 'RAG and tool reports must use different output paths' }
$ragArgs = @('-m', 'evals.run', '--mode', $Mode, '--real-sample-size', $RealSampleSize, '--output', $ragPath)
$toolArgs = @('-m', 'evals.tool_run', '--mode', $Mode, '--real-sample-size', $RealSampleSize, '--output', $toolPath)
if ($RagDataset) { $ragArgs += @('--dataset', (Resolve-ProjectPath $RagDataset)) }
if ($ToolDataset) { $toolArgs += @('--dataset', (Resolve-ProjectPath $ToolDataset)) }
if ($QdrantUrl) { $ragArgs += @('--qdrant-url', $QdrantUrl) }
if ($EmbeddingUrl) { $ragArgs += @('--embedding-url', $EmbeddingUrl) }

$failed = $false
Push-Location $agentRoot
try {
    foreach ($invocation in @(@{Name = 'RAG'; Arguments = $ragArgs}, @{Name = 'Tool'; Arguments = $toolArgs})) {
        try {
            $arguments = $invocation.Arguments
            & $Python @arguments
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "$($invocation.Name) evaluation failed with exit code $LASTEXITCODE"
                $failed = $true
            }
        }
        catch {
            Write-Warning "$($invocation.Name) evaluation could not finish"
            $failed = $true
        }
    }
}
finally {
    Pop-Location
}
if ($failed) { exit 1 }
exit 0
