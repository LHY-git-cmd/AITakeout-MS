<script setup lang="ts">
import { Clock3, PackageCheck } from '@lucide/vue'

const props = defineProps<{ toolName?: string; data: unknown }>()

function objectValue(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
function rows() {
  if (Array.isArray(props.data)) return props.data as Record<string, unknown>[]
  const value = objectValue(props.data)
  for (const key of ['records', 'items', 'timeline', 'events']) {
    if (Array.isArray(value[key])) return value[key] as Record<string, unknown>[]
  }
  return Object.keys(value).length ? [value] : []
}
function label(item: Record<string, unknown>) {
  return String(item.statusText ?? item.title ?? item.status ?? item.description ?? '订单状态已更新')
}
function orderNumber(item: Record<string, unknown>) {
  return item.number ?? item.orderNumber ?? item.order_id ?? item.id
}
</script>

<template>
  <section class="agent-order-card" aria-label="订单状态">
    <header><PackageCheck :size="19" aria-hidden="true" /><strong>{{ toolName === 'get_after_sale_status' ? '售后状态' : '订单信息' }}</strong></header>
    <p v-if="!rows().length">没有查询到可展示的订单记录</p>
    <ul v-else>
      <li v-for="(item, index) in rows().slice(0, 5)" :key="String(orderNumber(item) ?? index)">
        <Clock3 :size="16" aria-hidden="true" />
        <span><b v-if="orderNumber(item)">订单 {{ orderNumber(item) }}</b>{{ label(item) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.agent-order-card { padding: 14px; border: 1px solid var(--color-line); border-left: 4px solid var(--color-accent); border-radius: 12px; background: var(--color-surface); }
.agent-order-card header { display: flex; align-items: center; gap: 8px; color: var(--color-brand-dark); }
.agent-order-card > p { margin: 10px 0 0; color: var(--color-muted); font-size: 13px; }
.agent-order-card ul { display: grid; gap: 8px; margin: 10px 0 0; padding: 0; list-style: none; }
.agent-order-card li { display: flex; align-items: flex-start; gap: 8px; color: var(--color-muted); font-size: 13px; }
.agent-order-card li svg { flex: 0 0 auto; margin-top: 2px; color: var(--color-accent); }
.agent-order-card li span, .agent-order-card li b { display: block; }
.agent-order-card li b { margin-bottom: 2px; color: var(--color-ink); }
</style>
