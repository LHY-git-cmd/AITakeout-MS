<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Grip, ShoppingBag, ChevronDown, ChevronUp } from '@lucide/vue'
import CartPanel from '@/components/CartPanel.vue'
import { useCartStore } from '@/stores/cart'

const POSITION_KEY = 'sky-cart-floating-position'

const cartStore = useCartStore()
const widget = ref<HTMLElement | null>(null)
const expanded = ref(true)
const dragging = ref(false)
const position = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })

const widgetStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`,
}))

function clampPosition(x: number, y: number) {
  const element = widget.value
  const width = element?.offsetWidth ?? Math.min(352, window.innerWidth - 24)
  const height = element?.offsetHeight ?? 420
  return {
    x: Math.min(Math.max(12, x), Math.max(12, window.innerWidth - width - 12)),
    y: Math.min(Math.max(12, y), Math.max(12, window.innerHeight - height - 12)),
  }
}

function savePosition() {
  window.localStorage.setItem(POSITION_KEY, JSON.stringify(position.value))
}

function setInitialPosition() {
  const stored = window.localStorage.getItem(POSITION_KEY)
  if (stored) {
    try {
      const saved = JSON.parse(stored)
      position.value = clampPosition(Number(saved.x), Number(saved.y))
      return
    } catch {
      window.localStorage.removeItem(POSITION_KEY)
    }
  }
  position.value = clampPosition(window.innerWidth - 364, window.innerHeight - 452)
}

function handlePointerMove(event: PointerEvent) {
  if (!dragging.value) return
  position.value = clampPosition(event.clientX - dragOffset.value.x, event.clientY - dragOffset.value.y)
}

function stopDragging() {
  if (!dragging.value) return
  dragging.value = false
  document.body.classList.remove('is-dragging-cart')
  savePosition()
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', stopDragging)
  window.removeEventListener('pointercancel', stopDragging)
}

function startDragging(event: PointerEvent) {
  if (event.button !== 0) return
  const rect = widget.value?.getBoundingClientRect()
  if (!rect) return
  dragging.value = true
  dragOffset.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  document.body.classList.add('is-dragging-cart')
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', stopDragging)
  window.addEventListener('pointercancel', stopDragging)
}

function handleResize() {
  position.value = clampPosition(position.value.x, position.value.y)
  savePosition()
}

function toggleExpanded() {
  expanded.value = !expanded.value
  void nextTick(handleResize)
}

onMounted(() => {
  setInitialPosition()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  stopDragging()
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <aside
    ref="widget"
    class="cart-floating-widget"
    :class="{ 'is-expanded': expanded, 'is-dragging': dragging }"
    :style="widgetStyle"
    aria-label="悬浮购物车"
  >
    <header class="cart-floating-widget__header" @pointerdown="startDragging">
      <div class="cart-floating-widget__title">
        <Grip :size="17" aria-hidden="true" />
        <ShoppingBag :size="19" aria-hidden="true" />
        <strong>购物车</strong>
        <span v-if="cartStore.totalCount" class="cart-floating-widget__count">{{ cartStore.totalCount }}</span>
      </div>
      <button
        type="button"
        class="cart-floating-widget__toggle"
        :aria-label="expanded ? '收起购物车' : '展开购物车'"
        :title="expanded ? '收起购物车' : '展开购物车'"
        @pointerdown.stop
        @click="toggleExpanded"
      >
        <ChevronDown v-if="expanded" :size="18" aria-hidden="true" />
        <ChevronUp v-else :size="18" aria-hidden="true" />
      </button>
    </header>

    <div v-if="expanded" class="cart-floating-widget__body">
      <CartPanel compact />
    </div>
    <footer v-else class="cart-floating-widget__collapsed">
      <span>合计</span>
      <strong>¥{{ cartStore.totalAmount.toFixed(2) }}</strong>
    </footer>
  </aside>
</template>

<style scoped>
.cart-floating-widget {
  position: fixed;
  z-index: 55;
  width: min(352px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 15px;
  color: var(--color-ink);
  background: var(--color-surface);
  box-shadow: 0 18px 48px rgb(56 39 24 / 22%), 0 3px 10px rgb(56 39 24 / 10%);
  transition: box-shadow 160ms ease;
}

.cart-floating-widget.is-dragging {
  box-shadow: 0 24px 58px rgb(56 39 24 / 28%), 0 5px 14px rgb(56 39 24 / 14%);
  user-select: none;
}

.cart-floating-widget__header {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 12px 0 10px;
  color: var(--color-ink);
  background: #fbf6ec;
  cursor: grab;
  touch-action: none;
}

.cart-floating-widget.is-dragging .cart-floating-widget__header { cursor: grabbing; }

.cart-floating-widget__title { display: flex; min-width: 0; align-items: center; gap: 7px; }
.cart-floating-widget__title > svg:first-child { color: var(--color-muted); }
.cart-floating-widget__count {
  display: inline-grid;
  min-width: 22px;
  height: 22px;
  padding: 0 5px;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  background: var(--color-brand);
  font-size: 12px;
  font-weight: 750;
}

.cart-floating-widget__toggle {
  display: grid;
  width: 36px;
  height: 36px;
  padding: 0;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 9px;
  color: var(--color-muted);
  background: var(--color-surface);
  cursor: pointer;
}

.cart-floating-widget__toggle:hover { color: var(--color-brand-dark); border-color: var(--color-brand); }
.cart-floating-widget__body { max-height: min(480px, calc(100vh - 120px)); overflow: auto; padding: 0 12px 12px; }
.cart-floating-widget__body :deep(.cart-panel) { padding: 0; }
.cart-floating-widget__body :deep(.cart-panel__toolbar) { padding-top: 10px; }
.cart-floating-widget__collapsed { display: flex; justify-content: space-between; padding: 0 14px 12px; color: var(--color-muted); font-size: 13px; }
.cart-floating-widget__collapsed strong { color: var(--color-brand-dark); font-size: 16px; }

@media (max-width: 767px) {
  .cart-floating-widget { width: calc(100vw - 24px); }
  .cart-floating-widget__body { max-height: min(390px, calc(100dvh - 150px)); }
}
</style>
