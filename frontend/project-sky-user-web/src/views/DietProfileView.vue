<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AlertTriangle, Check, HeartPulse, ShieldCheck, Trash2 } from '@lucide/vue'
import PageScaffold from '@/components/PageScaffold.vue'
import { deleteDietProfile, getDietProfile, saveDietProfile, type DietConstraint, type DietProfile } from '@/api/diet'

const allergens = [
  ['PEANUT', '花生'], ['TREE_NUT', '坚果'], ['MILK', '乳制品'], ['EGG', '蛋类'],
  ['WHEAT', '小麦'], ['SOY', '大豆'], ['FISH', '鱼类'], ['SHELLFISH', '甲壳类'], ['SESAME', '芝麻'],
] as const
const goals = [
  ['WEIGHT_LOSS', '减脂/控制能量'], ['HIGH_PROTEIN', '高蛋白'], ['LOW_SODIUM', '低钠'],
  ['LOW_SUGAR', '低糖'], ['LOW_FAT', '低脂'], ['LIGHT', '清淡'],
] as const

const form = reactive({ regionCode: '', dietaryPattern: '', allergens: [] as string[], goals: [] as string[], exclusions: '' })
const loading = ref(true)
const saving = ref(false)
const consent = ref(false)
const message = ref('')
const error = ref('')
const hasProfile = ref(false)
const canSave = computed(() => consent.value && !saving.value)

function fill(profile: DietProfile | null | undefined) {
  hasProfile.value = Boolean(profile)
  form.regionCode = profile?.regionCode ?? ''
  form.dietaryPattern = profile?.dietaryPattern ?? ''
  form.allergens = profile?.constraints?.filter(item => item.type === 'ALLERGEN').map(item => item.code) ?? []
  form.exclusions = profile?.constraints?.filter(item => item.type === 'EXCLUDED_INGREDIENT').map(item => item.code).join('、') ?? ''
  form.goals = profile?.goals?.map(item => item.code) ?? []
  consent.value = Boolean(profile)
}

async function load() {
  loading.value = true
  try { fill(await getDietProfile()) } finally { loading.value = false }
}

async function save() {
  if (!canSave.value) return
  saving.value = true; message.value = ''; error.value = ''
  const exclusions = form.exclusions.split(/[、,，\s]+/).map(value => value.trim()).filter(Boolean)
  const constraints: DietConstraint[] = [
    ...form.allergens.map(code => ({ type: 'ALLERGEN' as const, code, severity: 'STRICT', sourceType: 'USER' })),
    ...exclusions.map(code => ({ type: 'EXCLUDED_INGREDIENT' as const, code, severity: 'STRICT', sourceType: 'USER' })),
  ]
  try {
    const result = await saveDietProfile({
      regionCode: form.regionCode || undefined,
      dietaryPattern: form.dietaryPattern || undefined,
      consentVersion: 'diet-consent-v1', constraints,
      goals: form.goals.map(code => ({ code, priority: 50 })),
    })
    fill(result); message.value = '饮食档案已保存，饱饱助手会在你授权使用时应用这些条件。'
  } catch { error.value = '保存失败，请检查填写内容后重试。' }
  finally { saving.value = false }
}

async function remove() {
  if (!window.confirm('确定删除长期饮食档案吗？之后仍可在单次对话中临时说明需求。')) return
  await deleteDietProfile(); fill(null); message.value = '长期饮食档案已删除。'
}

onMounted(load)
</script>

<template>
  <PageScaffold title="饮食档案">
    <section class="diet-intro">
      <HeartPulse :size="26" aria-hidden="true" />
      <div><h2>让推荐更适合你</h2><p>你可以保存过敏、忌口和日常目标，也可以不保存并在每次对话中临时说明。</p></div>
    </section>
    <p v-if="message" class="diet-message" role="status"><Check :size="17" />{{ message }}</p>
    <p v-if="error" class="page-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="content-loading">正在读取饮食档案...</div>
    <form v-else class="diet-form" @submit.prevent="save">
      <fieldset>
        <legend>基础偏好</legend>
        <label>常用地区<input v-model.trim="form.regionCode" maxlength="32" placeholder="例如 CN-ZJ-HZ（可不填）" /></label>
        <label>饮食方式
          <select v-model="form.dietaryPattern"><option value="">无特别要求</option><option value="VEGETARIAN">素食</option><option value="HALAL">清真</option></select>
        </label>
      </fieldset>

      <fieldset>
        <legend><ShieldCheck :size="18" /> 食物过敏</legend>
        <p class="diet-help">勾选后会作为硬约束。成分未知、可能含有或有交叉接触风险的菜品不会被标记为安全。</p>
        <div class="diet-options">
          <label v-for="item in allergens" :key="item[0]"><input v-model="form.allergens" type="checkbox" :value="item[0]" />{{ item[1] }}</label>
        </div>
      </fieldset>

      <fieldset>
        <legend>明确忌口</legend>
        <label>不吃的食材<input v-model.trim="form.exclusions" maxlength="300" placeholder="用逗号分隔，例如 香菜、猪肉" /></label>
      </fieldset>

      <fieldset>
        <legend>日常目标</legend>
        <div class="diet-options">
          <label v-for="item in goals" :key="item[0]"><input v-model="form.goals" type="checkbox" :value="item[0]" />{{ item[1] }}</label>
        </div>
      </fieldset>

      <aside class="diet-boundary">
        <AlertTriangle :size="19" aria-hidden="true" />
        <p>本档案用于辅助筛选菜单，不用于诊断或治疗。复杂疾病、急性症状、用药或医生限制请优先咨询专业人员。</p>
      </aside>
      <label class="diet-consent"><input v-model="consent" type="checkbox" />我同意保存以上信息用于个性化点餐，并知道可以随时删除。</label>
      <div class="diet-actions">
        <button class="primary-action" type="submit" :disabled="!canSave">{{ saving ? '保存中...' : '保存档案' }}</button>
        <button v-if="hasProfile" class="danger-action" type="button" @click="remove"><Trash2 :size="17" />删除档案</button>
      </div>
    </form>
  </PageScaffold>
</template>

<style scoped>
.diet-intro { display: flex; align-items: center; gap: 14px; padding: 18px; border: 1px solid var(--color-gold-line); border-radius: 12px; background: #fbf6ec; }
.diet-intro > svg { flex: none; color: var(--color-brand); }
.diet-intro h2, .diet-intro p { margin: 0; }.diet-intro h2 { font-size: 18px; }.diet-intro p, .diet-help { color: var(--color-muted); }
.diet-message { display: flex; align-items: center; gap: 7px; padding: 11px 13px; border-radius: 9px; color: #285d38; background: #edf8ef; }
.diet-form { display: grid; gap: 14px; margin-top: 16px; }
.diet-form fieldset { display: grid; gap: 12px; margin: 0; padding: 18px; border: 1px solid var(--color-line); border-radius: 12px; background: var(--color-surface); }
.diet-form legend { display: inline-flex; align-items: center; gap: 6px; padding: 0 6px; font-weight: 750; }
.diet-form label { display: grid; gap: 7px; font-size: 14px; }
.diet-form input[type="text"], .diet-form input:not([type]), .diet-form select { min-height: 44px; padding: 0 12px; border: 1px solid var(--color-line); border-radius: 9px; background: #fffdf8; }
.diet-help { margin: 0; font-size: 13px; }
.diet-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); gap: 9px; }
.diet-options label, .diet-consent { display: flex; min-height: 44px; align-items: center; gap: 9px; }
.diet-options input, .diet-consent input { width: 18px; height: 18px; accent-color: var(--color-brand); }
.diet-boundary { display: flex; gap: 10px; padding: 13px; border: 1px solid #ead7a8; border-radius: 10px; color: #6f571f; background: #fff9e8; }.diet-boundary svg { flex: none; }.diet-boundary p { margin: 0; }
.diet-actions { display: flex; gap: 10px; flex-wrap: wrap; }.diet-actions button { display: inline-flex; min-height: 46px; align-items: center; justify-content: center; gap: 6px; padding: 0 18px; border-radius: 9px; font-weight: 700; cursor: pointer; }
.primary-action { border: 0; color: #fff; background: var(--color-brand); }.primary-action:disabled { opacity: .45; cursor: not-allowed; }.danger-action { border: 1px solid #e4b8b2; color: var(--color-brand-dark); background: #fff; }
@media (max-width: 600px) { .diet-options { grid-template-columns: 1fr 1fr; }.diet-actions button { flex: 1; } }
</style>
