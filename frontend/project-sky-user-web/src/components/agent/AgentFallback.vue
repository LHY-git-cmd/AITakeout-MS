<script setup lang="ts">
import { CircleAlert, Headphones } from '@lucide/vue'

defineProps<{ message?: string }>()
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="agent-fallback" role="alert">
    <CircleAlert :size="22" aria-hidden="true" />
    <div><strong>饱饱暂时没能完成</strong><p>{{ message || '请稍后重试，也可以前往订单详情查看最新状态。' }}</p></div>
    <button type="button" @click="emit('retry')">重试</button>
    <span><Headphones :size="15" aria-hidden="true" />人工客服将在第二阶段接入</span>
  </section>
</template>

<style scoped>
.agent-fallback { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 9px; padding: 14px; border: 1px solid #e3b8b0; border-radius: 12px; color: var(--color-brand-dark); background: var(--color-danger-soft); }
.agent-fallback p { margin: 3px 0 0; color: var(--color-muted); font-size: 13px; }
.agent-fallback button { min-height: 44px; padding: 0 12px; border: 1px solid var(--color-brand); border-radius: 8px; color: var(--color-brand-dark); background: var(--color-surface); font-weight: 700; cursor: pointer; }
.agent-fallback span { display: flex; grid-column: 2 / -1; align-items: center; gap: 5px; color: var(--color-muted); font-size: 12px; }
</style>
