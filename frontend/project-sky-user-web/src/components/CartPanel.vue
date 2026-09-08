<script setup lang="ts">
import { ref } from 'vue'
import { ShoppingBag, Trash2 } from '@lucide/vue'
import { useRouter } from 'vue-router'
import ProductImage from './ProductImage.vue'
import QuantityControl from './QuantityControl.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore, type CartItem } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'

defineProps<{ compact?: boolean }>()

const cartStore = useCartStore()
const authStore = useAuthStore()
const uiStore = useUiStore()
const router = useRouter()
const busyKey = ref('')

async function change(item: CartItem, direction: 'up' | 'down') {
  busyKey.value = item.key
  try {
    if (direction === 'up') await cartStore.increment(item)
    else await cartStore.decrement(item)
  } finally {
    busyKey.value = ''
  }
}

async function clearCart() {
  if (!window.confirm('确定清空购物车吗？')) return
  await cartStore.clear()
}

async function checkout() {
  if (!authStore.isAuthenticated) {
    uiStore.closeCart()
    uiStore.openLogin()
    return
  }
  uiStore.closeCart()
  await router.push('/checkout')
}
</script>

<template>
  <div :class="['cart-panel', { 'cart-panel--compact': compact }]">
    <div v-if="cartStore.items.length" class="cart-panel__toolbar">
      <span>{{ cartStore.totalCount }} 件商品</span>
      <button type="button" @click="clearCart"><Trash2 :size="15" /> 清空</button>
    </div>
    <div v-if="cartStore.syncing" class="cart-panel__notice">正在合并本地购物车...</div>
    <div v-if="cartStore.error" class="cart-panel__error">{{ cartStore.error }}</div>
    <div v-if="!cartStore.items.length && !cartStore.loading" class="cart-panel__empty">
      <div><ShoppingBag :size="30" /></div>
      <p>购物车还是空的</p>
    </div>
    <div v-else class="cart-panel__items">
      <article v-for="item in cartStore.items" :key="item.key" class="cart-line">
        <ProductImage :src="item.image" :alt="item.name" />
        <div class="cart-line__main">
          <strong>{{ item.name }}</strong>
          <span v-if="item.dishFlavor">{{ item.dishFlavor }}</span>
          <b>¥{{ Number(item.amount).toFixed(2) }}</b>
        </div>
        <QuantityControl
          :count="item.number"
          :busy="busyKey === item.key"
          @increment="change(item, 'up')"
          @decrement="change(item, 'down')"
        />
      </article>
    </div>
    <div class="cart-panel__footer">
      <div><span>合计</span><strong>¥{{ cartStore.totalAmount.toFixed(2) }}</strong></div>
      <button type="button" :disabled="!cartStore.items.length" @click="checkout">去结算</button>
    </div>
  </div>
</template>
