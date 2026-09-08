<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, Clock3, MapPin, PackageOpen, ReceiptText, RefreshCw, UserRound } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import OrderActions from '@/components/OrderActions.vue'
import ProductImage from '@/components/ProductImage.vue'
import { cancelOrder, getOrderDetail, payOrder, remindOrder, repeatOrder, type OrderRecord } from '@/api/order'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import type { OrderStatusEvent } from '@/services/orderSocket'
import { statusInfo } from '@/utils/order'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const uiStore = useUiStore()
const order = ref<OrderRecord | null>(null)
const loading = ref(true)
const busyAction = ref('')
const error = ref('')
const notice = ref('')

function handleOrderStatus(event: Event) {
  const detail = (event as CustomEvent<OrderStatusEvent>).detail
  if (detail?.orderId === orderId.value) {
    notice.value = detail.content || '订单状态已更新'
    void load()
  }
}

const orderId = computed(() => Number(route.params.id))
const goodsAmount = computed(() => order.value?.orderDetailList.reduce((sum, item) => sum + Number(item.amount) * item.number, 0) ?? 0)

async function load() {
  if (!authStore.isAuthenticated || !Number.isFinite(orderId.value)) return
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrderDetail(orderId.value)
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

async function run(action: 'cancel' | 'remind' | 'repeat' | 'pay') {
  if (!order.value) return
  if (action === 'cancel' && !window.confirm('确定取消该订单吗？')) return
  busyAction.value = action
  error.value = ''
  notice.value = ''
  try {
    if (action === 'cancel') {
      await cancelOrder(order.value.id)
      notice.value = '订单已取消'
      await load()
    } else if (action === 'remind') {
      await remindOrder(order.value.id)
      notice.value = '已提醒商家处理订单'
    } else if (action === 'repeat') {
      await repeatOrder(order.value.id)
      await cartStore.loadRemote()
      await router.push('/')
      uiStore.openCart()
    } else {
      const payment = await payOrder(order.value.number)
      if (!payment.mockPay) throw new Error('未返回模拟支付结果')
      sessionStorage.setItem('sky-last-order', JSON.stringify({
        id: order.value.id, orderNumber: order.value.number, orderAmount: order.value.amount, orderTime: order.value.orderTime,
      }))
      await router.push({ name: 'payment-result', query: { success: '1', id: String(order.value.id) } })
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '操作失败，请稍后重试'
  } finally {
    busyAction.value = ''
  }
}

onMounted(() => {
  window.addEventListener('sky:order-status', handleOrderStatus)
  if (authStore.isAuthenticated) void load()
  else uiStore.openLogin()
})
onBeforeUnmount(() => window.removeEventListener('sky:order-status', handleOrderStatus))
watch(() => authStore.isAuthenticated, (authenticated) => { if (authenticated) void load() })
</script>

<template>
  <PageScaffold title="订单详情">
    <template #action><RouterLink class="back-link" to="/orders"><ArrowLeft :size="17" />返回订单</RouterLink></template>
    <p v-if="notice" class="operation-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="page-error order-page-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="content-empty"><RefreshCw class="spin" :size="28" />正在加载订单...</div>
    <div v-else-if="!order" class="content-empty"><PackageOpen :size="34" /><strong>没有找到订单</strong></div>
    <div v-else class="order-detail-layout">
      <div class="order-detail-main">
        <section class="order-status-band">
          <div><small>当前状态</small><h2>{{ statusInfo(order.status).label }}</h2><p>订单号 {{ order.number }}</p></div>
          <OrderActions :order="order" :busy="busyAction" @cancel="run('cancel')" @remind="run('remind')" @repeat="run('repeat')" @pay="run('pay')" />
        </section>

        <section class="detail-section">
          <header><ReceiptText :size="19" /><h2>商品明细</h2></header>
          <div class="detail-products">
            <article v-for="item in order.orderDetailList" :key="item.id">
              <ProductImage :src="item.image" :alt="item.name" />
              <div><strong>{{ item.name }}</strong><span v-if="item.dishFlavor">{{ item.dishFlavor }}</span></div>
              <span>x{{ item.number }}</span><b>¥{{ (Number(item.amount) * item.number).toFixed(2) }}</b>
            </article>
          </div>
          <div class="detail-cost"><span>商品小计</span><b>¥{{ goodsAmount.toFixed(2) }}</b></div>
          <div class="detail-cost"><span>打包费</span><b>¥{{ Number(order.packAmount).toFixed(2) }}</b></div>
          <div class="detail-cost detail-cost--total"><span>订单合计</span><strong>¥{{ Number(order.amount).toFixed(2) }}</strong></div>
        </section>
      </div>

      <aside class="order-meta">
        <section><h2><MapPin :size="18" />配送信息</h2><p><UserRound :size="16" />{{ order.consignee }} {{ order.phone }}</p><p>{{ order.address }}</p></section>
        <section><h2><Clock3 :size="18" />时间信息</h2><dl><dt>下单时间</dt><dd>{{ order.orderTime }}</dd><dt>预计送达</dt><dd>{{ order.estimatedDeliveryTime || '尽快送达' }}</dd><template v-if="order.deliveryTime"><dt>送达时间</dt><dd>{{ order.deliveryTime }}</dd></template></dl></section>
        <section><h2>其他信息</h2><dl><dt>支付方式</dt><dd>{{ order.payMethod === 1 ? '微信模拟支付' : '其他' }}</dd><dt>餐具数量</dt><dd>{{ order.tablewareStatus === 1 ? '按餐量提供' : `${order.tablewareNumber} 份` }}</dd><dt>订单备注</dt><dd>{{ order.remark || '无' }}</dd><template v-if="order.cancelReason || order.rejectionReason"><dt>取消原因</dt><dd>{{ order.cancelReason || order.rejectionReason }}</dd></template></dl></section>
      </aside>
    </div>
  </PageScaffold>
</template>
