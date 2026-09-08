<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronRight, ClipboardList, PackageOpen, RefreshCw } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import OrderActions from '@/components/OrderActions.vue'
import ProductImage from '@/components/ProductImage.vue'
import { cancelOrder, getOrderPage, payOrder, remindOrder, repeatOrder, type OrderRecord } from '@/api/order'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import type { OrderStatusEvent } from '@/services/orderSocket'
import { statusInfo } from '@/utils/order'

const PAGE_SIZE = 6
const authStore = useAuthStore()
const cartStore = useCartStore()
const uiStore = useUiStore()
const router = useRouter()
const orders = ref<OrderRecord[]>([])
const total = ref(0)
const page = ref(1)
const activeStatus = ref<number | undefined>()
const loading = ref(false)
const loadingMore = ref(false)
const busyId = ref<number | null>(null)
const busyAction = ref('')
const error = ref('')
const notice = ref('')

function handleOrderStatus(event: Event) {
  const detail = (event as CustomEvent<OrderStatusEvent>).detail
  if (!detail?.orderId) return
  notice.value = detail.content || '订单状态已更新'
  void load(true)
}

const tabs = [
  { label: '全部', status: undefined },
  { label: '待付款', status: 1 },
  { label: '待接单', status: 2 },
  { label: '派送中', status: 4 },
  { label: '已完成', status: 5 },
  { label: '已取消', status: 6 },
]

async function load(reset = true) {
  if (!authStore.isAuthenticated) return
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  error.value = ''
  try {
    const result = await getOrderPage(page.value, PAGE_SIZE, activeStatus.value)
    total.value = Number(result.total)
    orders.value = reset ? result.records ?? [] : [...orders.value, ...(result.records ?? [])]
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '订单加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function selectStatus(status?: number) {
  activeStatus.value = status
  await load(true)
}

async function loadMore() {
  page.value += 1
  await load(false)
}

async function run(order: OrderRecord, action: 'cancel' | 'remind' | 'repeat' | 'pay') {
  if (action === 'cancel' && !window.confirm('确定取消该订单吗？')) return
  busyId.value = order.id
  busyAction.value = action
  error.value = ''
  notice.value = ''
  try {
    if (action === 'cancel') {
      await cancelOrder(order.id)
      notice.value = '订单已取消'
      await load(true)
    } else if (action === 'remind') {
      await remindOrder(order.id)
      notice.value = '已提醒商家处理订单'
    } else if (action === 'repeat') {
      await repeatOrder(order.id)
      await cartStore.loadRemote()
      await router.push('/')
      uiStore.openCart()
    } else {
      const payment = await payOrder(order.number)
      if (!payment.mockPay) throw new Error('未返回模拟支付结果')
      sessionStorage.setItem('sky-last-order', JSON.stringify({
        id: order.id, orderNumber: order.number, orderAmount: order.amount, orderTime: order.orderTime,
      }))
      await router.push({ name: 'payment-result', query: { success: '1', id: String(order.id) } })
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '操作失败，请稍后重试'
  } finally {
    busyId.value = null
    busyAction.value = ''
  }
}

onMounted(() => {
  window.addEventListener('sky:order-status', handleOrderStatus)
  if (authStore.isAuthenticated) void load()
  else uiStore.openLogin()
})

onBeforeUnmount(() => window.removeEventListener('sky:order-status', handleOrderStatus))

watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void load()
})
</script>

<template>
  <PageScaffold title="我的订单" description="查看订单状态与历史记录">
    <div class="order-tabs" role="tablist" aria-label="订单状态">
      <button
        v-for="tab in tabs"
        :key="tab.label"
        type="button"
        role="tab"
        :aria-selected="activeStatus === tab.status"
        :class="{ 'is-active': activeStatus === tab.status }"
        @click="selectStatus(tab.status)"
      >{{ tab.label }}</button>
    </div>

    <p v-if="notice" class="operation-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="page-error order-page-error" role="alert">{{ error }}</p>
    <div v-if="!authStore.isAuthenticated" class="content-empty">
      <ClipboardList :size="34" /><strong>登录后查看订单</strong><button type="button" @click="uiStore.openLogin">登录</button>
    </div>
    <div v-else-if="loading" class="content-empty"><RefreshCw class="spin" :size="28" />正在加载订单...</div>
    <div v-else-if="!orders.length" class="content-empty">
      <PackageOpen :size="36" /><strong>当前没有订单</strong><RouterLink class="empty-link" to="/">去点餐</RouterLink>
    </div>
    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <header>
          <div><span>订单号 {{ order.number }}</span><small>{{ order.orderTime }}</small></div>
          <b :class="`status-${statusInfo(order.status).tone}`">{{ statusInfo(order.status).label }}</b>
        </header>
        <RouterLink class="order-card__content" :to="`/orders/${order.id}`">
          <div class="order-card__products">
            <ProductImage
              v-for="item in order.orderDetailList.slice(0, 3)"
              :key="item.id"
              :src="item.image"
              :alt="item.name"
            />
            <span v-if="order.orderDetailList.length > 3">+{{ order.orderDetailList.length - 3 }}</span>
          </div>
          <div class="order-card__summary">
            <strong>{{ order.orderDetailList[0]?.name || '订单商品' }}<template v-if="order.orderDetailList.length > 1"> 等 {{ order.orderDetailList.length }} 种</template></strong>
            <span>共 {{ order.orderDetailList.reduce((sum, item) => sum + item.number, 0) }} 件</span>
          </div>
          <strong class="order-card__amount">¥{{ Number(order.amount).toFixed(2) }}</strong>
          <ChevronRight :size="18" />
        </RouterLink>
        <footer>
          <OrderActions
            :order="order"
            :busy="busyId === order.id ? busyAction : ''"
            @cancel="run(order, 'cancel')"
            @remind="run(order, 'remind')"
            @repeat="run(order, 'repeat')"
            @pay="run(order, 'pay')"
          />
        </footer>
      </article>
      <button v-if="orders.length < total" class="load-more" type="button" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? '加载中...' : `加载更多（${orders.length}/${total}）` }}
      </button>
    </div>
  </PageScaffold>
</template>
