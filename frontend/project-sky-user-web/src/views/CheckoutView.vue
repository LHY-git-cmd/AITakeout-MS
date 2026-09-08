<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronRight, Clock3, MapPin, ShieldCheck, Utensils } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import ProductImage from '@/components/ProductImage.vue'
import { fullAddress, getAddresses, type Address } from '@/api/address'
import { payOrder, submitOrder, type SubmittedOrder } from '@/api/order'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'

const DELIVERY_FEE = 6
const authStore = useAuthStore()
const cartStore = useCartStore()
const uiStore = useUiStore()
const router = useRouter()
const addresses = ref<Address[]>([])
const selectedAddressId = ref<number | null>(null)
const remark = ref('')
const tablewareMode = ref<'meal' | 'custom'>('meal')
const tablewareNumber = ref(1)
const loadingAddresses = ref(false)
const submitting = ref(false)
const error = ref('')

const packAmount = computed(() => cartStore.totalCount)
const estimatedTotal = computed(() => cartStore.totalAmount + packAmount.value + DELIVERY_FEE)
const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null)
const canSubmit = computed(() => authStore.isAuthenticated && cartStore.items.length > 0 && selectedAddress.value && !submitting.value)

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadAddresses() {
  if (!authStore.isAuthenticated) return
  loadingAddresses.value = true
  try {
    addresses.value = await getAddresses() ?? []
    selectedAddressId.value = addresses.value.find((item) => item.isDefault)?.id ?? addresses.value[0]?.id ?? null
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '地址加载失败'
  } finally {
    loadingAddresses.value = false
  }
}

async function submit() {
  if (!canSubmit.value || !selectedAddressId.value) return
  submitting.value = true
  error.value = ''
  let order: SubmittedOrder | null = null
  try {
    const arrival = new Date(Date.now() + 30 * 60 * 1000)
    order = await submitOrder({
      addressBookId: selectedAddressId.value,
      payMethod: 1,
      remark: remark.value.trim(),
      estimatedDeliveryTime: formatDateTime(arrival),
      deliveryStatus: 1,
      tablewareStatus: tablewareMode.value === 'meal' ? 1 : 0,
      tablewareNumber: tablewareMode.value === 'meal' ? cartStore.totalCount : tablewareNumber.value,
      packAmount: packAmount.value,
      amount: estimatedTotal.value,
    })
    sessionStorage.setItem('sky-last-order', JSON.stringify(order))
    cartStore.markSubmitted()
    await new Promise((resolve) => window.setTimeout(resolve, 800))
    const payment = await payOrder(order.orderNumber)
    if (!payment.mockPay) throw new Error('当前演示环境未返回模拟支付结果')
    await router.replace({ name: 'payment-result', query: { success: '1', id: String(order.id) } })
  } catch (cause) {
    if (order) {
      await router.replace({ name: 'payment-result', query: { success: '0', id: String(order.id) } })
    } else {
      error.value = cause instanceof ApiError ? cause.message : '提交订单失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (authStore.isAuthenticated) void loadAddresses()
  else uiStore.openLogin()
})

watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void loadAddresses()
})
</script>

<template>
  <PageScaffold title="确认订单" description="核对配送信息与商品明细">
    <div class="checkout-layout">
      <div class="checkout-main">
        <section class="checkout-section">
          <header><MapPin :size="20" /><h2>配送地址</h2><RouterLink to="/addresses">管理地址 <ChevronRight :size="16" /></RouterLink></header>
          <div v-if="loadingAddresses" class="checkout-placeholder">正在加载地址...</div>
          <div v-else-if="!addresses.length" class="checkout-placeholder">
            <span>暂无可用地址</span><RouterLink to="/addresses">新增地址</RouterLink>
          </div>
          <div v-else class="checkout-addresses">
            <label v-for="address in addresses" :key="address.id" :class="{ 'is-selected': selectedAddressId === address.id }">
              <input v-model="selectedAddressId" type="radio" :value="address.id" />
              <span><strong>{{ address.consignee }} {{ address.phone }}</strong>{{ fullAddress(address) }}</span>
              <b v-if="address.isDefault">默认</b>
            </label>
          </div>
        </section>

        <section class="checkout-section">
          <header><Utensils :size="20" /><h2>商品明细</h2></header>
          <div v-if="!cartStore.items.length" class="checkout-placeholder"><span>购物车为空</span><RouterLink to="/">返回点餐</RouterLink></div>
          <div v-else class="checkout-items">
            <article v-for="item in cartStore.items" :key="item.key">
              <ProductImage :src="item.image" :alt="item.name" />
              <div><strong>{{ item.name }}</strong><span v-if="item.dishFlavor">{{ item.dishFlavor }}</span></div>
              <span>x{{ item.number }}</span><b>¥{{ (item.amount * item.number).toFixed(2) }}</b>
            </article>
          </div>
        </section>

        <section class="checkout-section checkout-options">
          <header><Clock3 :size="20" /><h2>配送偏好</h2></header>
          <label>订单备注<textarea v-model="remark" maxlength="100" rows="2" placeholder="口味、配送等特殊要求" /></label>
          <fieldset class="segment-field">
            <legend>餐具数量</legend>
            <label :class="{ 'is-selected': tablewareMode === 'meal' }"><input v-model="tablewareMode" type="radio" value="meal" />按餐量提供</label>
            <label :class="{ 'is-selected': tablewareMode === 'custom' }"><input v-model="tablewareMode" type="radio" value="custom" />指定数量</label>
          </fieldset>
          <label v-if="tablewareMode === 'custom'">数量<input v-model.number="tablewareNumber" type="number" min="0" max="20" /></label>
        </section>
      </div>

      <aside class="checkout-summary">
        <h2>费用明细</h2>
        <div><span>商品小计</span><b>¥{{ cartStore.totalAmount.toFixed(2) }}</b></div>
        <div><span>打包费</span><b>¥{{ packAmount.toFixed(2) }}</b></div>
        <div><span>配送费</span><b>¥{{ DELIVERY_FEE.toFixed(2) }}</b></div>
        <div class="checkout-summary__total"><span>合计</span><strong>¥{{ estimatedTotal.toFixed(2) }}</strong></div>
        <p><ShieldCheck :size="16" /> 金额将由服务端重新核算</p>
        <p v-if="error" class="page-error" role="alert">{{ error }}</p>
        <button type="button" :disabled="!canSubmit" @click="submit">{{ submitting ? '模拟支付处理中...' : '提交订单并支付' }}</button>
      </aside>
    </div>
  </PageScaffold>
</template>
