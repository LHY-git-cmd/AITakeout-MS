# 个性化饮食推荐数据包

本目录同时保存“可证明事实草稿”和“一人份开发模拟数据”。模拟营养状态统一为 `SIMULATED`，不能当作实验室检测值或生产审核数据。

## 文件说明

- `source_registry.csv`：数据和知识来源登记；
- `ingredient_master.csv`：当前菜单涉及的标准食材字典；
- `dish_ingredient_draft.csv`：从菜名和菜品描述提取的配料事实；
- `dish_allergen_draft.csv`：只标记能够由明确食材证明的 `CONTAINS`，其余均为 `UNKNOWN`；
- `dish_nutrition_collection.csv`：一人份模拟营养值及未来真实数据替换字段；
- `dish_recipe_simulated.csv`：一人份模拟配方、油盐糖和过敏原假设；
- `dish_allergen_simulated.csv`：模拟配方范围内的过敏原声明；
- `dish_data_quality.csv`：每道菜距离审核启用还缺少的数据；
- `seasonal_rule_draft.csv`：地域时令数据采集模板，等待本地供应链或农业部门数据；
- `setmeal_composition_snapshot.csv`：当前数据库套餐组成快照。

## 数据依据

1. 菜名、描述和套餐组成来自当前 `sky_take_out` 数据库；
2. `小麦面粉 → WHEAT`、`鸡蛋 → EGG`、`豆腐/大豆 → SOY`、明确鱼类 → `FISH`；
3. 未提供配方、标签或加工环境证据时，不推断为 `FREE`；
4. 营养值必须来自真实称量配方、供应商营养标签、检测报告或受控计算；
5. 包装饮料和酒类必须以实际采购规格标签为准，不能根据商品名猜测。

## 审核流程

```text
确认实际商品与配方
→ 称量标准份量和全部原料
→ 录入油、盐、糖、酱料和调味品
→ 核对供应商过敏原及交叉接触声明
→ 使用受控食物成分数据计算营养
→ 厨师/数据维护人复核
→ 营养专业人员或授权审核人审核
→ 管理端标记 VERIFIED
```

`UNKNOWN` 不能在没有新证据时批量改为 `FREE`。

## 模拟模式

用户已明确允许当前阶段使用一人份模拟用量，因此 `dish_nutrition_collection.csv` 已填入 `SIM-MENU-V1` 估算值：

- 单人菜按一份成品估算；
- “2斤鱼”按整菜由3～4人分食折算为一人份；
- 包装饮料按常见单瓶规格估算；
- 炒菜计入模拟用油、盐、糖和酱料；
- 数值用于开发排序和界面演示，不用于医疗决策；
- 生产环境 `DIET_ALLOW_SIMULATED_DATA` 默认关闭。

本地导入命令：

```powershell
.\scripts\import-simulated-diet-data.ps1 -ConfirmSimulation
```
