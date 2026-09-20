<script setup lang="ts">
import { LoaderCircle, PackageOpen, Plus, RefreshCw } from '@lucide/vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import ProductImage from './ProductImage.vue'
import type { ProductSearchItem } from '@/api/search'

defineProps<{
  items: ProductSearchItem[]
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  error: string
  addingId: number | null
  disabled?: boolean
}>()
const emit = defineEmits<{ loadMore: []; retry: []; select: [item: ProductSearchItem]; add: [item: ProductSearchItem] }>()
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (!('IntersectionObserver' in window)) return
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) emit('loadMore')
  }, { rootMargin: '240px 0px' })
  if (sentinel.value) observer.observe(sentinel.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section class="search-results" aria-live="polite" :aria-busy="loading || loadingMore">
    <div v-if="loading" class="product-loading"><LoaderCircle class="spin" :size="24" />正在搜索全部商品</div>
    <div v-else-if="!items.length" class="menu-state">
      <PackageOpen :size="34" aria-hidden="true" />
      <strong>{{ error || '没有找到相关商品，可以缩短关键词后重试' }}</strong>
      <button v-if="error" type="button" @click="$emit('retry')"><RefreshCw :size="17" />重新搜索</button>
    </div>
    <div v-else class="product-grid">
      <article v-for="item in items" :key="item.stableKey" class="product-card">
        <button class="product-card__image" type="button" :aria-label="`查看${item.name}`" @click="$emit('select', item)">
          <ProductImage :src="item.image" :alt="item.name" />
        </button>
        <div class="product-card__body">
          <div class="search-result__meta"><span>{{ item.categoryName }}</span><span>{{ item.productType === 'dish' ? '菜品' : '套餐' }}</span></div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.description || '新鲜制作，欢迎品尝' }}</p>
          <div class="product-card__footer">
            <strong><small>¥</small>{{ Number(item.price).toFixed(2) }}</strong>
            <button type="button" :disabled="disabled || addingId === item.id" :aria-label="item.hasFlavor ? `选择${item.name}规格` : `添加${item.name}`" @click="$emit('add', item)">
              <LoaderCircle v-if="addingId === item.id" class="spin" :size="17" />
              <Plus v-else :size="18" />
              <span v-if="item.hasFlavor">选规格</span>
            </button>
          </div>
        </div>
      </article>
    </div>
    <div ref="sentinel" class="search-results__sentinel" aria-hidden="true" />
    <div v-if="loadingMore" class="search-results__more"><LoaderCircle class="spin" :size="18" />继续加载</div>
    <div v-else-if="error && items.length" class="search-results__retry" role="alert">
      <span>{{ error }}</span><button type="button" @click="$emit('loadMore')">重试加载</button>
    </div>
    <p v-else-if="items.length && !hasMore" class="search-results__end">已显示全部相关商品</p>
  </section>
</template>
