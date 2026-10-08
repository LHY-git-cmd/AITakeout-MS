<template>
  <section class="diet-admin-page">
    <header class="diet-header"><div><h1>饮食推荐管理</h1><p>维护已审核营养事实、过敏原声明和版本化食养规则</p></div></header>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="菜品营养" name="nutrition">
        <div class="diet-toolbar"><el-input v-model.trim="keyword" size="small" placeholder="搜索菜品" clearable @keyup.enter.native="loadDishes" /><el-button type="primary" size="small" @click="loadDishes">查询</el-button></div>
        <el-table v-loading="loading" :data="dishes" class="diet-table">
          <el-table-column prop="name" label="菜品" min-width="180" />
          <el-table-column prop="categoryName" label="分类" width="130" />
          <el-table-column prop="price" label="价格" width="100" />
          <el-table-column label="营养状态" width="120"><template slot-scope="s"><el-tag :type="nutritionState[s.row.id] === 'VERIFIED' ? 'success' : 'warning'">{{ nutritionState[s.row.id] || '未维护' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="180"><template slot-scope="s"><el-button type="text" @click="openNutrition(s.row)">维护营养</el-button></template></el-table-column>
        </el-table>
        <el-pagination :current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
      </el-tab-pane>
      <el-tab-pane label="食养规则" name="rules">
        <div class="diet-toolbar diet-toolbar--rules"><span class="diet-hint">规则必须绑定有效权威来源，经校验后才能发布。</span><el-button type="primary" size="small" @click="ruleDialog=true">新建规则集</el-button></div>
        <el-table :data="rules" class="diet-table">
          <el-table-column prop="name" label="规则集" min-width="180" />
          <el-table-column prop="rule_code" label="编码" min-width="160" />
          <el-table-column prop="rule_version" label="版本" width="80" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" min-width="220"><template slot-scope="s">
            <el-button v-if="s.row.status === 'DRAFT'" type="text" @click="validateRule(s.row)">校验</el-button>
            <el-button v-if="s.row.status === 'VALIDATED'" type="text" @click="publishRule(s.row)">发布</el-button>
            <el-button v-if="s.row.status === 'PUBLISHED'" type="text" class="danger" @click="offlineRule(s.row)">下线</el-button>
            <el-button v-if="s.row.status === 'OFFLINE'" type="text" @click="rollbackRule(s.row)">回滚上线</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog class="nutrition-dialog" :title="`维护营养 · ${selectedDish?.name || ''}`" :visible.sync="dialog" width="720px">
      <el-alert type="warning" :closable="false" title="成分或过敏原未知时请明确选择 UNKNOWN，不能用 FREE 代替未知。" />
      <el-form label-position="top" class="nutrition-form">
        <el-form-item label="标准份量（g）"><el-input-number v-model="form.servingSizeG" :min="1" /></el-form-item>
        <el-form-item label="热量（kcal）"><el-input-number v-model="form.energyKcal" :min="0" /></el-form-item>
        <el-form-item label="蛋白质（g）"><el-input-number v-model="form.proteinG" :min="0" /></el-form-item>
        <el-form-item label="脂肪（g）"><el-input-number v-model="form.fatG" :min="0" /></el-form-item>
        <el-form-item label="碳水（g）"><el-input-number v-model="form.carbohydrateG" :min="0" /></el-form-item>
        <el-form-item label="膳食纤维（g）"><el-input-number v-model="form.dietaryFiberG" :min="0" /></el-form-item>
        <el-form-item label="糖（g）"><el-input-number v-model="form.sugarG" :min="0" /></el-form-item>
        <el-form-item label="钠（mg）"><el-input-number v-model="form.sodiumMg" :min="0" /></el-form-item>
        <el-form-item label="数据来源" class="full"><el-input v-model.trim="form.sourceReference" placeholder="供应商标签、配方计算或权威数据来源" /></el-form-item>
        <el-form-item label="食材（每行：编码|名称|克数）" class="full"><el-input v-model="ingredientsText" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="过敏原声明" class="full">
          <div class="allergen-grid"><label v-for="item in allergenOptions" :key="item.code"><span>{{ item.name }}</span><el-select v-model="allergenState[item.code]" size="mini"><el-option v-for="state in allergenStates" :key="state" :label="state" :value="state" /></el-select></label></div>
        </el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveNutrition">保存草稿</el-button><el-button type="success" :loading="saving" @click="verifyNutrition">审核启用</el-button></span>
    </el-dialog>
    <el-dialog title="新建食养规则集" :visible.sync="ruleDialog" width="680px">
      <el-alert type="warning" :closable="false" title="这里只接受受控操作符和动作；规则发布前必须绑定权威来源并通过校验。" />
      <el-form label-position="top" class="rule-form">
        <el-form-item label="规则编码"><el-input v-model.trim="ruleForm.rule_code" placeholder="例如 HYPERTENSION" /></el-form-item>
        <el-form-item label="名称"><el-input v-model.trim="ruleForm.name" /></el-form-item>
        <el-form-item label="适用人群"><el-input v-model.trim="ruleForm.applicable_population" /></el-form-item>
        <el-form-item label="规则 JSON 数组"><el-input v-model="ruleForm.rulesJson" type="textarea" :rows="7" /></el-form-item>
        <el-form-item label="来源 JSON 数组"><el-input v-model="ruleForm.sourcesJson" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="ruleDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createRule">创建草稿</el-button></span>
    </el-dialog>
  </section>
</template>

<script lang="ts">
import Vue from 'vue'
import { getDishPage } from '@/api/dish'
import * as api from '@/api/diet'

const emptyForm = () => ({ profileVersion: 1, recipeVersion: 1, servingSizeG: 100, energyKcal: 0, proteinG: 0, fatG: 0, carbohydrateG: 0, dietaryFiberG: 0, sugarG: 0, sodiumMg: 0, sourceType: 'RECIPE_CALCULATION', sourceReference: '', calculationMethod: 'PER_SERVING', uncertaintyNote: '' })

export default Vue.extend({
  name: 'DietManagementPage',
  data() { return { activeTab: 'nutrition', loading: false, saving: false, keyword: '', page: 1, total: 0, dishes: [] as any[], rules: [] as any[], nutritionState: {} as Record<string, string>, dialog: false, ruleDialog: false, selectedDish: null as any, form: emptyForm(), ingredientsText: '', ruleForm: { rule_code: '', name: '', applicable_population: '', rulesJson: '[\n  {"target_field":"SODIUM_MG","operator":"UNKNOWN","action":"REQUIRE_CLARIFICATION","priority":100,"reason_code":"SODIUM_DATA_MISSING","message":"缺少钠数据，不能生成确定性结论"}\n]', sourcesJson: '[\n  {"source_title":"权威指南名称","source_url":"https://...","source_section":"章节","review_due_date":"2027-10-01"}\n]' }, allergenState: {} as Record<string, string>, allergenStates: ['FREE', 'CONTAINS', 'MAY_CONTAIN', 'CROSS_CONTACT_RISK', 'UNKNOWN'], allergenOptions: [{ code: 'PEANUT', name: '花生' }, { code: 'TREE_NUT', name: '坚果' }, { code: 'MILK', name: '乳制品' }, { code: 'EGG', name: '蛋类' }, { code: 'WHEAT', name: '小麦' }, { code: 'SOY', name: '大豆' }, { code: 'FISH', name: '鱼类' }, { code: 'SHELLFISH', name: '甲壳类' }, { code: 'SESAME', name: '芝麻' }] } },
  mounted() { this.loadDishes(); this.loadRules() },
  methods: {
    async loadDishes() { this.loading = true; try { const res:any = await getDishPage({ page: this.page, pageSize: 10, name: this.keyword || undefined }); this.dishes = res.data?.data?.records || []; this.total = Number(res.data?.data?.total || 0); await Promise.all(this.dishes.map(async dish => { const n:any = await api.getDishNutrition(dish.id); this.$set(this.nutritionState, dish.id, n.data?.data?.verification_status || '') })) } finally { this.loading = false } },
    async loadRules() { const res:any = await api.listDietRuleSets(); this.rules = res.data?.data || [] },
    changePage(value:number) { this.page=value; this.loadDishes() },
    async openNutrition(dish:any) { this.selectedDish=dish; this.form=emptyForm(); this.ingredientsText=''; this.allergenState={}; this.allergenOptions.forEach(item => this.$set(this.allergenState,item.code,'UNKNOWN')); const res:any=await api.getDishNutrition(dish.id); const data=res.data?.data; if(data && Object.keys(data).length) this.form={ ...this.form, ...data, profileVersion:data.profile_version, recipeVersion:data.recipe_version, servingSizeG:data.serving_size_g, energyKcal:data.energy_kcal, proteinG:data.protein_g, fatG:data.fat_g, carbohydrateG:data.carbohydrate_g, dietaryFiberG:data.dietary_fiber_g, sugarG:data.sugar_g, sodiumMg:data.sodium_mg, sourceType:data.source_type, sourceReference:data.source_reference }; this.dialog=true },
    nutritionPayload() { const ingredients=this.ingredientsText.split(/\r?\n/).filter(Boolean).map(line => { const [code,name,amount]=line.split('|'); return { code:code?.trim(), name:name?.trim(), amountG:Number(amount||0), roleType:'PRIMARY', replaceable:false } }); const allergens=this.allergenOptions.map(item => ({ code:item.code,status:this.allergenState[item.code] || 'UNKNOWN',sourceReference:this.form.sourceReference || '待补充来源' })); return { ...this.form, ingredients, allergens } },
    async saveNutrition() { if(!this.form.sourceReference) return this.$message.warning('请填写数据来源'); this.saving=true; try { await api.saveDishNutrition(this.selectedDish.id,this.nutritionPayload()); this.$message.success('营养草稿已保存'); await this.loadDishes() } finally { this.saving=false } },
    async verifyNutrition() { await this.saveNutrition(); this.saving=true; try { await api.verifyDishNutrition(this.selectedDish.id,this.form.profileVersion); this.$message.success('营养档案已审核启用'); this.dialog=false; await this.loadDishes() } finally { this.saving=false } },
    async createRule() { let rules:any[],sources:any[]; try { rules=JSON.parse(this.ruleForm.rulesJson); sources=JSON.parse(this.ruleForm.sourcesJson) } catch { return this.$message.error('规则或来源 JSON 格式不正确') } this.saving=true; try { await api.createDietRuleSet({ rule_code:this.ruleForm.rule_code,name:this.ruleForm.name,applicable_population:this.ruleForm.applicable_population,rules,sources }); this.ruleDialog=false; await this.loadRules(); this.$message.success('规则草稿已创建') } finally { this.saving=false } },
    async validateRule(row:any) { await api.validateDietRuleSet(row.rule_set_id); await this.loadRules() }, async publishRule(row:any) { await api.publishDietRuleSet(row.rule_set_id); await this.loadRules() }, async offlineRule(row:any) { await api.offlineDietRuleSet(row.rule_set_id); await this.loadRules() }, async rollbackRule(row:any) { await api.rollbackDietRuleSet(row.rule_set_id); await this.loadRules() },
  }
})
</script>

<style scoped lang="scss">
.diet-admin-page { padding: 0 24px 30px; color: var(--text-2); }.diet-header { margin: 0 -24px 18px; padding: 20px 28px; border-bottom: 1px solid var(--field-border); background: var(--surface-card); }.diet-header h1,.diet-header p { margin:0 }.diet-header p { margin-top:5px; color:var(--text-3) }.diet-toolbar { display:flex; max-width:520px; gap:8px; margin-bottom:14px }.diet-toolbar--rules { max-width:none; align-items:center; justify-content:space-between }.diet-table { width:100% }.diet-hint { color:var(--text-3); font-size:13px }.danger { color:#dc2626 }.nutrition-form { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:0 12px; margin-top:14px }.nutrition-form .full { grid-column:1/-1 }.allergen-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px }.allergen-grid label { display:flex; align-items:center; justify-content:space-between; gap:6px }.allergen-grid .el-select { width:145px }.rule-form { margin-top:14px } @media(max-width:900px){.nutrition-form{grid-template-columns:repeat(2,minmax(0,1fr))}.allergen-grid{grid-template-columns:1fr}}
/* Element数字输入默认固定宽度；网格项与控件必须一起允许收缩。 */
.nutrition-form > .el-form-item { min-width: 0; }
.nutrition-form ::v-deep .el-form-item__content { min-width: 0; }
.nutrition-form ::v-deep .el-input-number,
.nutrition-form ::v-deep .el-input,
.nutrition-form ::v-deep .el-textarea { width: 100%; max-width: 100%; }
.nutrition-dialog ::v-deep .el-dialog { max-width: calc(100vw - 32px); }
.allergen-grid label { min-width: 0; }
.allergen-grid .el-select { min-width: 0; max-width: 100%; flex: 1; }
@media (max-width: 420px) {
  .nutrition-form { grid-template-columns: minmax(0, 1fr); }
}
</style>
