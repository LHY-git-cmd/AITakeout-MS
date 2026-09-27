<script setup lang="ts">
import { ShieldAlert, X } from '@lucide/vue'

defineProps<{ data?: unknown; busy?: boolean }>()
const emit = defineEmits<{ approve: []; reject: [] }>()
</script>

<template>
  <div class="agent-confirmation-layer" role="presentation">
    <section role="alertdialog" aria-modal="true" aria-labelledby="agent-confirmation-title" class="agent-confirmation">
      <button class="agent-confirmation__close" type="button" aria-label="拒绝并关闭" @click="emit('reject')"><X :size="20" /></button>
      <span class="agent-confirmation__icon"><ShieldAlert :size="24" aria-hidden="true" /></span>
      <h2 id="agent-confirmation-title">确认执行此操作？</h2>
      <p>请核对操作对象和影响范围。确认后系统才会继续执行，聊天文字不会被当作确认。</p>
      <pre v-if="data">{{ JSON.stringify(data, null, 2) }}</pre>
      <div>
        <button type="button" :disabled="busy" @click="emit('reject')">取消操作</button>
        <button type="button" :disabled="busy" @click="emit('approve')">确认执行</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.agent-confirmation-layer { display: grid; position: fixed; z-index: 90; inset: 0; padding: 20px; place-items: center; background: rgb(31 27 22 / 52%); }
.agent-confirmation { position: relative; width: min(100%, 440px); padding: 26px; border-radius: 14px; background: var(--color-surface); box-shadow: 0 22px 60px rgb(31 27 22 / 24%); }
.agent-confirmation__close { display: grid; position: absolute; top: 8px; right: 8px; width: 44px; height: 44px; padding: 0; place-items: center; border: 0; background: transparent; cursor: pointer; }
.agent-confirmation__icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 12px; color: var(--color-brand-dark); background: var(--color-brand-soft); }
.agent-confirmation h2 { margin: 14px 0 6px; font-size: 21px; }
.agent-confirmation p { margin: 0; color: var(--color-muted); }
.agent-confirmation pre { max-height: 150px; overflow: auto; padding: 10px; border-radius: 8px; background: var(--color-canvas); font-size: 12px; white-space: pre-wrap; }
.agent-confirmation > div { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 20px; }
.agent-confirmation > div button { min-height: 44px; border: 1px solid var(--color-brand); border-radius: 8px; color: var(--color-brand-dark); background: var(--color-surface); font-weight: 750; cursor: pointer; }
.agent-confirmation > div button:last-child { color: #fff; background: var(--color-brand); }
</style>
