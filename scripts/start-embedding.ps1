$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $projectRoot 'sky-embedding'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) {
    throw 'Embedding virtual environment is missing. Create sky-embedding/.venv first.'
}
if (-not (Test-Path (Join-Path $serviceRoot 'models\bge-m3\config.json'))) {
    throw 'BAAI/bge-m3 model files are missing.'
}

Push-Location $serviceRoot
try {
    & $python run.py
}
finally {
    Pop-Location
}
