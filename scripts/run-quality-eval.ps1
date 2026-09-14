param(
    [ValidateSet('mock', 'real')]
    [string]$Mode = 'mock',
    [int]$RealSampleSize = 12,
    [string]$Output = 'build/reports/rag-eval.json'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$agentRoot = Join-Path $projectRoot 'agent-service'

Push-Location $agentRoot
try {
    python -m evals.run `
        --mode $Mode `
        --real-sample-size $RealSampleSize `
        --output (Join-Path $projectRoot $Output)
    if ($LASTEXITCODE -ne 0) {
        throw "quality evaluation failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
