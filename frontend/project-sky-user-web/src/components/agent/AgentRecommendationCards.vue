<script setup lang="ts">
import { Plus, Utensils } from '@lucide/vue'
import ProductImage from '@/components/ProductImage.vue'

defineProps<{ items: Record<string, unknown>[] }>()
const emit = defineEmits<{ add: [item: Record<string, unknown>] }>()

function price(item: Record<string, unknown>) {
  return Number(item.price ?? 0).toFixed(2)
}
</script>

<template>
  <section class="agent-card-group" aria-label="推荐菜品">
    <header><Utensils :size="18" aria-hidden="true" /><strong>为你推荐</strong></header>
    <p v-if="!items.length" class="agent-card-group__empty">暂时没有找到符合条件的可售商品</p>
    <div v-else class="agent-recommendations">
      <article v-for="item in items" :key="`${item.productType}-${item.id}`" class="agent-product-card">
        <ProductImage :src="String(item.image ?? '')" :alt="String(item.name ?? '推荐菜品')" />
        <div>
          <strong>{{ item.name }}</strong>
          <p>{{ item.description || '实时可售，价格以当前页面为准' }}</p>
          <span>¥{{ price(item) }}</span>
        </div>
        <button type="button" :aria-label="`添加${item.name}`" @click="emit('add', item)">
          <Plus :size="17" aria-hidden="true" />添加
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.agent-card-group { padding: 14px; border: 1px solid var(--color-gold-line); border-radius: 12px; background: var(--color-surface); }
.agent-card-group > header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; color: var(--color-brand-dark); }
.agent-recommendations { display: grid; gap: 10px; }
.agent-product-card { display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 9px; border: 1px solid var(--color-line); border-radius: 10px; background: #fffdf8; }
.agent-product-card :deep(.product-image) { width: 64px; height: 64px; border-radius: 8px; object-fit: cover; }
.agent-product-card > div { min-width: 0; }
.agent-product-card strong, .agent-product-card p { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.agent-product-card p { margin: 3px 0; color: var(--color-muted); font-size: 12px; }
.agent-product-card span { color: var(--color-brand-dark); font-weight: 750; }
.agent-product-card button { display: inline-flex; min-height: 44px; align-items: center; gap: 4px; padding: 0 10px; border: 0; border-radius: 8px; color: #fff; background: var(--color-brand); font-weight: 700; cursor: pointer; }
.agent-card-group__empty { margin: 0; color: var(--color-muted); font-size: 13px; }
@media (max-width: 520px) { .agent-product-card { grid-template-columns: 54px minmax(0, 1fr); } .agent-product-card button { grid-column: 1 / -1; justify-content: center; } }
</style>
