<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CheckCircle2, CircleAlert, Clock3, LoaderCircle, RefreshCw, WalletCards, XCircle } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import type { SubmittedOrder } from '@/api/order'
import {
  beginOrderPayment,
  clearPaymentRecovery,
  queryPayment,
  readPaymentRecovery,
  type PaymentStatus,
  type PaymentView,
} from '@/api/payment'
import { ApiError } from '@/api/http'

type ResultState = PaymentStatus | 'LOADING' | 'STILL_CONFIRMING' | 'QUERY_ERROR' | 'MISSING'

const POLL_DELAYS = [1_000, 2_000, 3_000, 5_000]
const MAX_POLL_MILLIS = 30_000
const route = useRoute()
const router = useRouter()
const payment = ref<PaymentView | null>(null)
const state = ref<ResultState>('LOADING')
const error = ref('')
const retryingPayment = ref(false)
const orderId = computed(() => Number(route.query.id))
const order = computed<SubmittedOrder | null>(() => {
  try { return JSON.parse(sessionStorage.getItem('sky-last-order') || 'null') as SubmittedOrder | null }
  catch { return null }
})
const paymentNo = ref(typeof route.query.paymentNo === 'string' ? route.query.paymentNo : '')
let pollTimer: number | undefined
let pollAttempt = 0
let elapsedMillis = 0
let stopped = false

const amount = computed(() => payment.value
  ? (payment.value.amountCent / 100).toFixed(2)
  : order.value ? Number(order.value.orderAmount).toFixed(2) : null)
const isProcessing = computed(() => state.value === 'LOADING' || state.value === 'CREATED' || state.value === 'PROCESSING')
const title = computed(() => {
  if (state.value === 'SUCCEEDED') return '支付成功'
  if (state.value === 'FAILED') return payment.value?.failureCode === 'INSUFFICIENT_BALANCE' ? '余额不足，支付未完成' : '支付失败'
  if (state.value === 'CLOSED') return '支付已超时'
  if (state.value === 'STILL_CONFIRMING') return '支付仍在确认'
  if (state.value === 'QUERY_ERROR') return '暂时无法查询支付结果'
  if (state.value === 'MISSING') return '未找到可查询的支付单'
  return '支付结果确认中'
})
const description = computed(() => {
  if (state.value === 'SUCCEEDED') return '服务端已确认到账，订单正在等待商家接单。'
  if (state.value === 'FAILED' && payment.value?.failureCode === 'INSUFFICIENT_BALANCE') return '模拟余额不足，本次未扣款。领取模拟金后可以重新支付。'
  if (state.value === 'FAILED') return '本次支付未完成且不会扣款，你可以重新发起支付。'
  if (state.value === 'CLOSED') return '支付单已关闭，订单可能已自动取消，请从订单页确认。'
  if (state.value === 'STILL_CONFIRMING') return '确认时间较长，可稍后从订单查看；请勿重复支付。'
  if (state.value === 'QUERY_ERROR') return error.value || '网络异常，支付结果仍以服务端记录为准。'
  if (state.value === 'MISSING') return '可以重新发起支付，系统会使用幂等键避免重复扣款。'
  return '正在向服务端查询可信支付状态，请稍候。'
})

function stopTimer() {
  if (pollTimer !== undefined) window.clearTimeout(pollTimer)
  pollTimer = undefined
}

function scheduleNext() {
  if (elapsedMillis >= MAX_POLL_MILLIS) {
    state.value = 'STILL_CONFIRMING'
    return
  }
  const baseDelay = POLL_DELAYS[Math.min(pollAttempt, POLL_DELAYS.length - 1)]
  const delay = Math.min(baseDelay, MAX_POLL_MILLIS - elapsedMillis)
  pollAttempt += 1
  elapsedMillis += delay
  pollTimer = window.setTimeout(() => void checkPayment(false), delay)
}

async function checkPayment(resetBudget = true) {
  stopTimer()
  if (resetBudget) {
    pollAttempt = 0
    elapsedMillis = 0
  }
  if (!paymentNo.value) {
    const recovered = Number.isFinite(orderId.value) ? readPaymentRecovery(orderId.value) : null
    paymentNo.value = recovered?.paymentNo ?? ''
  }
  if (!paymentNo.value) {
    state.value = 'MISSING'
    return
  }
  error.value = ''
  if (!payment.value) state.value = 'LOADING'
  try {
    const result = await queryPayment(paymentNo.value)
    if (stopped) return
    payment.value = result
    state.value = result.status
    if (result.status === 'CREATED' || result.status === 'PROCESSING') scheduleNext()
    else clearPaymentRecovery(result.orderId)
  } catch (cause) {
    if (stopped) return
    state.value = 'QUERY_ERROR'
    error.value = cause instanceof ApiError ? cause.message : '查询失败，请检查网络后重试'
  }
}

async function retryPayment() {
  const id = Number.isFinite(orderId.value) ? orderId.value : order.value?.id
  if (!id) return
  retryingPayment.value = true
  error.value = ''
  try {
    if (state.value === 'FAILED') clearPaymentRecovery(id)
    const result = await beginOrderPayment(id)
    paymentNo.value = result.paymentNo
    payment.value = result
    await router.replace({ name: 'payment-result', query: { paymentNo: result.paymentNo, id: String(id) } })
    await checkPayment(true)
  } catch (cause) {
    state.value = 'QUERY_ERROR'
    error.value = cause instanceof ApiError ? cause.message : '重新支付失败，请稍后重试'
  } finally {
    retryingPayment.value = false
  }
}

onMounted(() => void checkPayment(true))
onBeforeUnmount(() => {
  stopped = true
  stopTimer()
})
</script>

<template>
  <PageScaffold title="支付结果">
    <div :class="['payment-result', `is-${state.toLowerCase()}`]" :aria-busy="isProcessing">
      <div class="payment-result__icon" aria-hidden="true">
        <CheckCircle2 v-if="state === 'SUCCEEDED'" :size="42" />
        <LoaderCircle v-else-if="isProcessing" class="spin" :size="42" />
        <Clock3 v-else-if="state === 'CLOSED' || state === 'STILL_CONFIRMING'" :size="42" />
        <XCircle v-else-if="state === 'FAILED'" :size="42" />
        <CircleAlert v-else :size="42" />
      </div>
      <h2>{{ title }}</h2>
      <p role="status">{{ description }}</p>
      <p v-if="order">订单号：{{ order.orderNumber }}</p>
      <p v-else-if="payment">订单编号：{{ payment.orderId }}</p>
      <strong v-if="amount">¥{{ amount }}</strong>
      <div class="payment-result__actions">
        <RouterLink :to="Number.isFinite(orderId) ? `/orders/${orderId}` : '/orders'">查看订单</RouterLink>
        <RouterLink v-if="state === 'SUCCEEDED'" to="/">继续点餐</RouterLink>
        <RouterLink v-else-if="state === 'FAILED' && payment?.failureCode === 'INSUFFICIENT_BALANCE'" to="/wallet">
          <WalletCards :size="17" aria-hidden="true" />领取模拟金
        </RouterLink>
        <RouterLink v-else-if="state === 'CLOSED'" to="/">重新点餐</RouterLink>
        <button v-if="state === 'QUERY_ERROR' || state === 'STILL_CONFIRMING'" type="button" @click="checkPayment(true)">
          <RefreshCw :size="17" aria-hidden="true" />重新查询
        </button>
        <button v-if="state === 'FAILED' || state === 'MISSING'" type="button" :disabled="retryingPayment" @click="retryPayment">
          <LoaderCircle v-if="retryingPayment" class="spin" :size="17" aria-hidden="true" />
          <RefreshCw v-else :size="17" aria-hidden="true" />重新支付
        </button>
      </div>
    </div>
  </PageScaffold>
</template>
