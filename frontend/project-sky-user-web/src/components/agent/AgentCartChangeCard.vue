<script setup lang="ts">
import { CheckCircle2, ShoppingBag } from '@lucide/vue'

const props = defineProps<{ data: unknown }>()

function value() {
  return props.data && typeof props.data === 'object' ? props.data as Record<string, unknown> : {}
}
function items() {
  const raw = value().items
  return Array.isArray(raw) ? raw as Record<string, unknown>[] : []
}
</script>

<template>
  <section class="agent-state-card" aria-live="polite">
    <span class="agent-state-card__icon"><CheckCircle2 :size="20" aria-hidden="true" /></span>
    <div>
      <strong>已更新购物车</strong>
      <p><ShoppingBag :size="15" aria-hidden="true" /> 当前共 {{ items().reduce((sum, item) => sum + Number(item.quantity ?? 0), 0) }} 件商品</p>
      <small v-if="value().replayed">已识别为重复请求，没有重复加购</small>
    </div>
  </section>
</template>

<style scoped>
.agent-state-card { display: flex; align-items: flex-start; gap: 11px; padding: 14px; border: 1px solid #b9d5c4; border-radius: 12px; background: #f0f8f3; }
.agent-state-card__icon { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; border-radius: 50%; color: #fff; background: var(--color-success); }
.agent-state-card p { display: flex; align-items: center; gap: 6px; margin: 4px 0 0; color: var(--color-muted); font-size: 13px; }
.agent-state-card small { color: var(--color-success); }
</style>
