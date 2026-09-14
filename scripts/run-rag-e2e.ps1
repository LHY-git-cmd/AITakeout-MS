$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$agentRoot = Join-Path $projectRoot 'agent-service'

$embedding = Invoke-RestMethod 'http://127.0.0.1:8001/health' -TimeoutSec 10
if ($embedding.status -ne 'healthy' -or $embedding.dimension -ne 1024) {
    throw 'BGE-M3 Embedding service is not ready on port 8001.'
}

$qdrant = Invoke-WebRequest 'http://127.0.0.1:6333/healthz' -UseBasicParsing -TimeoutSec 10
if ($qdrant.StatusCode -ne 200) {
    throw 'Qdrant is not ready on port 6333.'
}

$env:RUN_RAG_E2E = '1'
$env:QDRANT_URL = 'http://127.0.0.1:6333'
$env:QDRANT_COLLECTION = 'sky_knowledge_v2'
$env:EMBEDDING_BASE_URL = 'http://127.0.0.1:8001'

Push-Location $agentRoot
try {
    python -m unittest tests.test_rag_e2e -v
}
finally {
    Pop-Location
}
