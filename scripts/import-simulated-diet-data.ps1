# 将一人份模拟营养、菜单食材和模拟过敏原声明导入本地开发数据库。
# 数据会标记为SIMULATED，生产环境默认不会使用；必须显式传入 -ConfirmSimulation。
param(
    [switch]$ConfirmSimulation
)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmSimulation) {
    throw '必须显式传入 -ConfirmSimulation，确认这些数据只用于开发演示。'
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $repoRoot 'data/diet-seed'
$database = if ($env:DB_NAME) { $env:DB_NAME } else { 'sky_take_out' }
$hostName = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }
$port = if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }
$user = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
if (-not $env:DB_PASSWORD) { throw 'DB_PASSWORD未配置' }

function SqlText([object]$value) {
    if ($null -eq $value) { return 'NULL' }
    $text = [string]$value
    if ([string]::IsNullOrWhiteSpace($text)) { return 'NULL' }
    return "'" + $text.Replace("'", "''") + "'"
}

function SqlNumber([object]$value) {
    $text = [string]$value
    if ([string]::IsNullOrWhiteSpace($text)) { return 'NULL' }
    return [decimal]::Parse($text, [Globalization.CultureInfo]::InvariantCulture).ToString([Globalization.CultureInfo]::InvariantCulture)
}

$sql = [Collections.Generic.List[string]]::new()
$sql.Add('START TRANSACTION;')

foreach ($ingredient in Import-Csv (Join-Path $dataRoot 'ingredient_master.csv')) {
    $sql.Add("INSERT INTO food_ingredient(ingredient_code,name,category,status) VALUES($(SqlText $ingredient.ingredient_code),$(SqlText $ingredient.name),$(SqlText $ingredient.category),1) ON DUPLICATE KEY UPDATE name=VALUES(name),category=VALUES(category),status=1;")
}

$dishIds = @(Import-Csv (Join-Path $dataRoot 'dish_nutrition_collection.csv') | ForEach-Object { [long]$_.dish_id })
foreach ($dishId in $dishIds) {
    $sql.Add("INSERT INTO dish_recipe_version(dish_id,recipe_version,status,created_by,effective_from) VALUES($dishId,1,'SIMULATED',1,NOW()) ON DUPLICATE KEY UPDATE status='SIMULATED',effective_from=NOW();")
    $sql.Add("DELETE FROM dish_ingredient WHERE dish_id=$dishId AND recipe_version=1;")
    $sql.Add("DELETE FROM dish_allergen_declaration WHERE dish_id=$dishId AND recipe_version=1;")
}

foreach ($item in Import-Csv (Join-Path $dataRoot 'dish_ingredient_draft.csv')) {
    $sql.Add("INSERT INTO dish_ingredient(dish_id,recipe_version,ingredient_id,amount_g,role_type,replaceable) SELECT $($item.dish_id),1,id,$(SqlNumber $item.amount_g),$(SqlText $item.role_type),0 FROM food_ingredient WHERE ingredient_code=$(SqlText $item.ingredient_code);")
}

foreach ($item in Import-Csv (Join-Path $dataRoot 'dish_nutrition_collection.csv')) {
    $sql.Add("INSERT INTO dish_nutrition_profile(dish_id,profile_version,recipe_version,serving_size_g,energy_kcal,protein_g,fat_g,carbohydrate_g,dietary_fiber_g,sugar_g,sodium_mg,purine_mg,source_type,source_reference,calculation_method,verification_status,data_completeness,uncertainty_note,effective_from) VALUES($($item.dish_id),1,1,$(SqlNumber $item.serving_size_g),$(SqlNumber $item.energy_kcal),$(SqlNumber $item.protein_g),$(SqlNumber $item.fat_g),$(SqlNumber $item.carbohydrate_g),$(SqlNumber $item.dietary_fiber_g),$(SqlNumber $item.sugar_g),$(SqlNumber $item.sodium_mg),$(SqlNumber $item.purine_mg),$(SqlText $item.source_type),$(SqlText $item.source_reference),$(SqlText $item.calculation_method),'SIMULATED',100,$(SqlText $item.uncertainty_note),NOW()) ON DUPLICATE KEY UPDATE serving_size_g=VALUES(serving_size_g),energy_kcal=VALUES(energy_kcal),protein_g=VALUES(protein_g),fat_g=VALUES(fat_g),carbohydrate_g=VALUES(carbohydrate_g),dietary_fiber_g=VALUES(dietary_fiber_g),sugar_g=VALUES(sugar_g),sodium_mg=VALUES(sodium_mg),purine_mg=VALUES(purine_mg),source_type=VALUES(source_type),source_reference=VALUES(source_reference),calculation_method=VALUES(calculation_method),verification_status='SIMULATED',data_completeness=100,uncertainty_note=VALUES(uncertainty_note),effective_from=NOW(),version=version+1;")
}

$allergenCodes = @('PEANUT','TREE_NUT','MILK','EGG','WHEAT','SOY','FISH','SHELLFISH','SESAME')
foreach ($dish in Import-Csv (Join-Path $dataRoot 'dish_allergen_simulated.csv')) {
    $contains = @($dish.contains_allergens -split ';' | Where-Object { $_ })
    $unknownAll = $dish.unknown_allergens -eq 'ALL'
    foreach ($code in $allergenCodes) {
        $status = if ($unknownAll) { 'UNKNOWN' } elseif ($code -in $contains) { 'CONTAINS' } else { 'FREE' }
        $source = "SIM-MENU-V1: $($dish.assumption_scope)"
        $sql.Add("INSERT INTO dish_allergen_declaration(dish_id,recipe_version,allergen_code,declaration_status,source_reference,verified_by,verified_at) VALUES($($dish.dish_id),1,$(SqlText $code),$(SqlText $status),$(SqlText $source),1,NOW());")
    }
}

$sql.Add('DELETE FROM setmeal_nutrition_snapshot WHERE verification_status=''SIMULATED'';')
$sql.Add('COMMIT;')

$tempFile = New-TemporaryFile
try {
    Set-Content -LiteralPath $tempFile.FullName -Value $sql -Encoding utf8
    Get-Content -Raw -LiteralPath $tempFile.FullName | mysql.exe --host=$hostName --port=$port --user=$user --password=$env:DB_PASSWORD --database=$database
    if ($LASTEXITCODE -ne 0) { throw '模拟饮食数据导入失败' }
} finally {
    Remove-Item -LiteralPath $tempFile.FullName -Force -ErrorAction SilentlyContinue
}

Write-Output "Imported simulated diet data for $($dishIds.Count) dishes into $database."
