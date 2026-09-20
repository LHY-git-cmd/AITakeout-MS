<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronRight, Clock3, MapPin, RefreshCw, ShieldCheck, Utensils } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import ProductImage from '@/components/ProductImage.vue'
import { fullAddress, getAddresses, type Address } from '@/api/address'
import type { DeliveryMode } from '@/api/checkout'
import type { SubmittedOrder } from '@/api/order'
import { beginOrderPayment } from '@/api/payment'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useCheckoutStore } from '@/stores/checkout'
import { useUiStore } from '@/stores/ui'

const authStore = useAuthStore()
const cartStore = useCartStore()
const checkoutStore = useCheckoutStore()
const uiStore = useUiStore()
const router = useRouter()
const addresses = ref<Address[]>([])
const selectedAddressId = ref<number | null>(null)
const deliveryMode = ref<DeliveryMode>('IMMEDIATE')
const deliverySlotStart = ref('')
const remark = ref('')
const tablewareMode = ref<'meal' | 'custom'>('meal')
const tablewareNumber = ref(1)
const loadingAddresses = ref(false)
const localError = ref('')
const errorSummary = ref<HTMLElement | null>(null)

const quote = computed(() => checkoutStore.quote)
const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) ?? null)
const canSubmit = computed(() => authStore.isAuthenticated && cartStore.items.length > 0
  && selectedAddress.value && quote.value && !checkoutStore.loading && !checkoutStore.submitting)
const displayError = computed(() => localError.value || checkoutStore.error)

function yuan(cents = 0) { return (cents / 100).toFixed(2) }
function requestDate(value: string) { return value.replace('T', ' ').replace(/\.\d+$/, '') }
function readableDate(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }

async function focusError() {
  await nextTick()
  errorSummary.value?.focus()
}

async function refreshQuote() {
  if (!selectedAddressId.value || !cartStore.items.length) return
  localError.value = ''
  await checkoutStore.preview({
    addressBookId: selectedAddressId.value,
    deliveryMode: deliveryMode.value,
    ...(deliveryMode.value === 'SCHEDULED' && deliverySlotStart.value
      ? { deliverySlotStart: requestDate(deliverySlotStart.value) } : {}),
  })
  if (checkoutStore.error) await focusError()
}

async function setDeliveryMode(mode: DeliveryMode) {
  if (mode === deliveryMode.value) return
  if (mode === 'SCHEDULED') {
    const first = quote.value?.availableSlots[0]
    if (!first) return
    deliverySlotStart.value = first.start
  } else deliverySlotStart.value = ''
  deliveryMode.value = mode
  await refreshQuote()
}

async function loadAddresses() {
  if (!authStore.isAuthenticated) return
  loadingAddresses.value = true
  localError.value = ''
  try {
    addresses.value = await getAddresses() ?? []
    selectedAddressId.value = addresses.value.find((item) => item.isDefault)?.id ?? addresses.value[0]?.id ?? null
    await refreshQuote()
  } catch (cause) {
    localError.value = cause instanceof ApiError ? cause.message : '地址加载失败'
    await focusError()
  } finally {
    loadingAddresses.value = false
  }
}

async function submit() {
  if (!canSubmit.value || !selectedAddressId.value || !quote.value) return
  localError.value = ''
  let order: SubmittedOrder | null = null
  try {
    order = await checkoutStore.submit({
      addressBookId: selectedAddressId.value,
      deliveryMode: deliveryMode.value,
      ...(deliveryMode.value === 'SCHEDULED' ? { deliverySlotStart: requestDate(deliverySlotStart.value) } : {}),
      payMethod: 1,
      remark: remark.value.trim(),
      deliveryStatus: deliveryMode.value === 'IMMEDIATE' ? 1 : 0,
      tablewareStatus: tablewareMode.value === 'meal' ? 1 : 0,
      tablewareNumber: tablewareMode.value === 'meal' ? cartStore.totalCount : tablewareNumber.value,
    })
    navigator.vibrate?.(10)
    sessionStorage.setItem('sky-last-order', JSON.stringify(order))
    cartStore.markSubmitted()
    const payment = await beginOrderPayment(order.id)
    await router.replace({ name: 'payment-result', query: { paymentNo: payment.paymentNo, id: String(order.id) } })
  } catch {
    if (order) await router.replace({ name: 'payment-result', query: { id: String(order.id) } })
    else await focusError()
  }
}

watch(selectedAddressId, (current, previous) => {
  if (previous !== null && current !== previous) void refreshQuote()
})
watch(deliverySlotStart, (current, previous) => {
  if (deliveryMode.value === 'SCHEDULED' && previous && current !== previous) void refreshQuote()
})

onMounted(() => authStore.isAuthenticated ? void loadAddresses() : uiStore.openLogin())
watch(() => authStore.isAuthenticated, (authenticated) => { if (authenticated) void loadAddresses() })
onBeforeUnmount(() => checkoutStore.reset())
</script>

<template>
  <PageScaffold title="确认订单" description="配送范围、时段与金额均由服务端实时核算">
    <div v-if="displayError" ref="errorSummary" class="checkout-error" role="alert" tabindex="-1">
      <strong>暂时无法完成结算</strong><span>{{ displayError }}</span>
      <button type="button" @click="refreshQuote"><RefreshCw :size="16" aria-hidden="true" />重新试算</button>
    </div>
    <div class="checkout-layout">
      <div class="checkout-main">
        <section class="checkout-section">
          <header><MapPin :size="20" aria-hidden="true" /><h2>配送地址</h2><RouterLink to="/addresses">管理地址 <ChevronRight :size="16" aria-hidden="true" /></RouterLink></header>
          <div v-if="loadingAddresses" class="checkout-placeholder">正在加载地址...</div>
          <div v-else-if="!addresses.length" class="checkout-placeholder"><span>暂无可用地址</span><RouterLink to="/addresses">新增地址</RouterLink></div>
          <div v-else class="checkout-addresses">
            <label v-for="address in addresses" :key="address.id" :class="{ 'is-selected': selectedAddressId === address.id }">
              <input v-model="selectedAddressId" type="radio" :value="address.id" />
              <span><strong>{{ address.consignee }} {{ address.phone }}</strong>{{ fullAddress(address) }}</span>
              <b v-if="address.isDefault">默认</b>
            </label>
          </div>
        </section>

        <section class="checkout-section checkout-options">
          <header><Clock3 :size="20" aria-hidden="true" /><h2>配送时间</h2></header>
          <fieldset class="segment-field">
            <legend>配送方式</legend>
            <button type="button" :class="{ 'is-selected': deliveryMode === 'IMMEDIATE' }" @click="setDeliveryMode('IMMEDIATE')">尽快送达</button>
            <button type="button" :class="{ 'is-selected': deliveryMode === 'SCHEDULED' }" :disabled="!quote" @click="setDeliveryMode('SCHEDULED')">预约配送</button>
          </fieldset>
          <label v-if="deliveryMode === 'SCHEDULED'">预约时段
            <select v-model="deliverySlotStart" :disabled="checkoutStore.loading">
              <option v-for="slot in quote?.availableSlots ?? []" :key="slot.start" :value="slot.start">{{ slot.label }}</option>
            </select>
          </label>
          <p v-else class="checkout-delivery-hint">预计 {{ readableDate(quote?.estimatedDeliveryTime) }} 送达</p>
        </section>

        <section class="checkout-section">
          <header><Utensils :size="20" aria-hidden="true" /><h2>商品明细</h2></header>
          <div v-if="checkoutStore.loading" class="checkout-placeholder">正在按最新价格核算...</div>
          <div v-else-if="!quote?.items.length" class="checkout-placeholder"><span>购物车为空</span><RouterLink to="/">返回点餐</RouterLink></div>
          <div v-else class="checkout-items">
            <article v-for="item in quote.items" :key="`${item.dishId}-${item.setmealId}-${item.flavor}`">
              <ProductImage :src="item.image" :alt="item.name" />
              <div><strong>{{ item.name }}</strong><span v-if="item.flavor">{{ item.flavor }}</span></div>
              <span>x{{ item.quantity }}</span><b>¥{{ yuan(item.subtotalCent) }}</b>
            </article>
          </div>
        </section>

        <section class="checkout-section checkout-options">
          <header><Utensils :size="20" aria-hidden="true" /><h2>订单偏好</h2></header>
          <label>订单备注<textarea v-model="remark" maxlength="100" rows="2" placeholder="口味、配送等特殊要求" /></label>
          <fieldset class="segment-field">
            <legend>餐具数量</legend>
            <label :class="{ 'is-selected': tablewareMode === 'meal' }"><input v-model="tablewareMode" type="radio" value="meal" />按餐量提供</label>
            <label :class="{ 'is-selected': tablewareMode === 'custom' }"><input v-model="tablewareMode" type="radio" value="custom" />指定数量</label>
          </fieldset>
          <label v-if="tablewareMode === 'custom'">数量<input v-model.number="tablewareNumber" type="number" min="0" max="20" /></label>
        </section>
      </div>

      <aside class="checkout-summary" aria-live="polite">
        <h2>费用明细</h2>
        <template v-if="quote">
          <div><span>商品小计</span><b>¥{{ yuan(quote.goodsAmountCent) }}</b></div>
          <div><span>打包费</span><b>¥{{ yuan(quote.packAmountCent) }}</b></div>
          <div><span>配送费（{{ (quote.distanceMeters / 1000).toFixed(1) }}km）</span><b>¥{{ yuan(quote.deliveryFeeCent) }}</b></div>
          <div v-if="quote.discountAmountCent"><span>优惠</span><b>-¥{{ yuan(quote.discountAmountCent) }}</b></div>
          <div class="checkout-summary__total"><span>合计</span><strong>¥{{ yuan(quote.amountCent) }}</strong></div>
        </template>
        <div v-else class="checkout-quote-loading">{{ checkoutStore.loading ? '正在实时试算…' : '等待试算' }}</div>
        <p><ShieldCheck :size="16" aria-hidden="true" /> 下单时将再次核价，避免价格与库存变化</p>
        <button data-test="submit" type="button" :disabled="!canSubmit" @click="submit">
          {{ checkoutStore.submitting ? '正在安全提交...' : '提交订单并支付' }}
        </button>
      </aside>
    </div>
  </PageScaffold>
</template>
