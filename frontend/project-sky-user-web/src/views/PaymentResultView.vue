<script setup lang="ts">
import { computed, ref } from 'vue'
import { CheckCircle2, LoaderCircle, RefreshCw, XCircle } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import { payOrder, type SubmittedOrder } from '@/api/order'

const route = useRoute()
const router = useRouter()
const retrying = ref(false)
const retryError = ref('')
const success = computed(() => route.query.success === '1')
const order = computed<SubmittedOrder | null>(() => {
  try { return JSON.parse(sessionStorage.getItem('sky-last-order') || 'null') as SubmittedOrder | null }
  catch { return null }
})

async function retryPayment() {
  if (!order.value) return
  retrying.value = true
  retryError.value = ''
  try {
    await payOrder(order.value.orderNumber)
    await router.replace({ name: 'payment-result', query: { success: '1', id: String(order.value.id) } })
  } catch {
    retryError.value = '模拟支付失败，请稍后重试'
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <PageScaffold title="支付结果">
    <div :class="['payment-result', { 'is-failed': !success }]">
      <div class="payment-result__icon"><CheckCircle2 v-if="success" :size="42" /><XCircle v-else :size="42" /></div>
      <h2>{{ success ? '模拟支付成功' : '订单已创建，支付未完成' }}</h2>
      <p v-if="order">订单号：{{ order.orderNumber }}</p>
      <strong v-if="order">¥{{ Number(order.orderAmount).toFixed(2) }}</strong>
      <p v-if="retryError" class="page-error">{{ retryError }}</p>
      <div class="payment-result__actions">
        <RouterLink :to="order ? `/orders/${order.id}` : '/orders'">查看订单</RouterLink>
        <RouterLink v-if="success" to="/">继续点餐</RouterLink>
        <button v-else type="button" :disabled="retrying || !order" @click="retryPayment">
          <LoaderCircle v-if="retrying" class="spin" :size="17" /><RefreshCw v-else :size="17" />重新支付
        </button>
      </div>
    </div>
  </PageScaffold>
</template>
