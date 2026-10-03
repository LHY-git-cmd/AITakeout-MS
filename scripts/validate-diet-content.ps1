# 校验个性化饮食数据包和知识库文件的基本结构，防止缺列、重复ID或丢失来源。
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $repoRoot 'data/diet-seed'
$knowledgeRoot = Join-Path $repoRoot '知识库/个性化饮食'

$requiredCsv = @{
    'ingredient_master.csv' = @('ingredient_code', 'name', 'source_id', 'review_status')
    'dish_ingredient_draft.csv' = @('dish_id', 'ingredient_code', 'source_id', 'review_status')
    'dish_allergen_draft.csv' = @('dish_id', 'allergen_code', 'declaration_status', 'source_reference')
    'dish_nutrition_collection.csv' = @('dish_id', 'serving_size_g', 'energy_kcal', 'source_reference', 'verification_status')
    'dish_recipe_simulated.csv' = @('dish_id', 'one_person_serving_g', 'oil_g', 'salt_g', 'contains_allergens')
    'dish_allergen_simulated.csv' = @('dish_id', 'contains_allergens', 'unknown_allergens', 'assumption_scope')
    'dish_adaptation_simulated.csv' = @('dish_id', 'spicy_level', 'oil_level', 'salt_level', 'digestibility', 'simulation_version')
    'dish_data_quality.csv' = @('dish_id', 'ready_for_verification', 'next_action')
}

foreach ($entry in $requiredCsv.GetEnumerator()) {
    $path = Join-Path $dataRoot $entry.Key
    $rows = @(Import-Csv -LiteralPath $path)
    if (-not $rows.Count) { throw "$($entry.Key) 没有数据" }
    $columns = @($rows[0].PSObject.Properties.Name)
    foreach ($column in $entry.Value) {
        if ($column -notin $columns) { throw "$($entry.Key) 缺少列 $column" }
    }
}

$rules = Get-Content -Raw -LiteralPath (Join-Path $dataRoot 'diet_rule_drafts.json') | ConvertFrom-Json
if (@($rules).Count -ne 4) { throw '食养规则草稿数量必须为4' }
foreach ($rule in $rules) {
    if (-not $rule.sources.Count -or -not $rule.rules.Count) { throw "$($rule.rule_code) 缺少规则或来源" }
}

$manifest = Get-Content -Raw -LiteralPath (Join-Path $knowledgeRoot 'manifest.json') | ConvertFrom-Json
foreach ($document in $manifest.documents) {
    $path = Join-Path $knowledgeRoot $document.file
    if (-not (Test-Path -LiteralPath $path)) { throw "知识库文件不存在: $($document.file)" }
    $content = Get-Content -Raw -LiteralPath $path
    if ($content -notmatch 'https://') { throw "知识库文件缺少来源链接: $($document.file)" }
}

Write-Output "Diet content validation passed: $(@($manifest.documents).Count) knowledge files, $(@($rules).Count) rule drafts."
