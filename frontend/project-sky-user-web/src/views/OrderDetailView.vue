<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, Clock3, MapPin, PackageOpen, ReceiptText, RefreshCw, UserRound } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import OrderActions from '@/components/OrderActions.vue'
import OrderTimeline from '@/components/OrderTimeline.vue'
import AfterSaleDialog from '@/components/AfterSaleDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import { getOrderDetail, remindOrder, repeatOrder, type OrderRecord } from '@/api/order'
import { applyAfterSale, getLatestAfterSale, getOrderTimeline, type AfterSaleRecord, type TimelineItem } from '@/api/aftersale'
import { beginOrderPayment } from '@/api/payment'
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
const timeline = ref<TimelineItem[]>([])
const afterSale = ref<AfterSaleRecord | null>(null)
const dialogOpen = ref(false)
const afterSaleError = ref('')

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
    const [detail, events] = await Promise.all([getOrderDetail(orderId.value), getOrderTimeline(orderId.value)])
    order.value = detail
    timeline.value = events
    try { afterSale.value = await getLatestAfterSale(orderId.value) } catch { afterSale.value = null }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

async function run(action: 'remind' | 'repeat' | 'pay') {
  if (!order.value) return
  busyAction.value = action
  error.value = ''
  notice.value = ''
  try {
    if (action === 'remind') {
      await remindOrder(order.value.id)
      notice.value = '已提醒商家处理订单'
    } else if (action === 'repeat') {
      await repeatOrder(order.value.id)
      await cartStore.loadRemote()
      await router.push('/')
      uiStore.openCart()
    } else {
      const payment = await beginOrderPayment(order.value.id)
      sessionStorage.setItem('sky-last-order', JSON.stringify({
        id: order.value.id, orderNumber: order.value.number, orderAmount: order.value.amount, orderTime: order.value.orderTime,
      }))
      await router.push({ name: 'payment-result', query: { paymentNo: payment.paymentNo, id: String(order.value.id) } })
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '操作失败，请稍后重试'
  } finally {
    busyAction.value = ''
  }
}

async function submitAfterSale(reason: string) {
  if (!order.value) return
  busyAction.value = 'after-sale'
  afterSaleError.value = ''
  try {
    afterSale.value = await applyAfterSale(order.value.id, reason)
    notice.value = afterSale.value.status === 'COMPLETED' ? '申请已处理完成' : '申请已提交'
    dialogOpen.value = false
    await load()
  } catch (cause) {
    afterSaleError.value = cause instanceof ApiError ? cause.message : '申请提交失败，请稍后重试'
  } finally { busyAction.value = '' }
}

const afterSaleStatus = computed(() => {
  const labels: Record<string, string> = {
    PENDING: '待商家审核', APPROVED: '审核已通过', REJECTED: '申请未通过',
    REFUND_PROCESSING: '退款处理中', COMPLETED: '退款成功', REFUND_FAILED: '退款失败',
  }
  return afterSale.value ? labels[afterSale.value.status] ?? afterSale.value.status : ''
})

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
          <OrderActions :order="order" :busy="busyAction" @after-sale="dialogOpen = true" @remind="run('remind')" @repeat="run('repeat')" @pay="run('pay')" />
        </section>

        <section v-if="afterSale" class="refund-status-card" :class="{ 'is-danger': afterSale.status === 'REFUND_FAILED' }" aria-live="polite">
          <div><small>退款与售后</small><h2>{{ afterSaleStatus }}</h2><p>{{ afterSale.status === 'REFUND_FAILED' ? '退款暂未到账，系统会自动重试，请勿重复申请。' : afterSale.reviewReason || afterSale.reason }}</p></div>
          <strong v-if="afterSale.refundAmountCent">¥{{ (afterSale.refundAmountCent / 100).toFixed(2) }}</strong>
        </section>

        <OrderTimeline :items="timeline" :loading="loading" />

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
    <AfterSaleDialog :open="dialogOpen" :order-status="order?.status ?? 1" :submitting="busyAction === 'after-sale'" :error="afterSaleError" @close="dialogOpen = false" @submit="submitAfterSale" />
  </PageScaffold>
</template>
