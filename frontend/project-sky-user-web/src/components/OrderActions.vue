<script setup lang="ts">
import { BellRing, CreditCard, RefreshCw, RotateCcw, XCircle } from '@lucide/vue'
import type { OrderRecord } from '@/api/order'
import { canCancel, canRemind } from '@/utils/order'

defineProps<{ order: OrderRecord; busy?: string }>()
defineEmits<{ cancel: []; remind: []; repeat: []; pay: [] }>()
</script>

<template>
  <div class="order-actions">
    <button v-if="canCancel(order.status)" type="button" :disabled="Boolean(busy)" @click="$emit('cancel')">
      <XCircle :size="16" />取消订单
    </button>
    <button v-if="canRemind(order.status)" type="button" :disabled="Boolean(busy)" @click="$emit('remind')">
      <BellRing :size="16" />催单
    </button>
    <button type="button" :disabled="Boolean(busy)" @click="$emit('repeat')">
      <RotateCcw :size="16" />再来一单
    </button>
    <button v-if="order.status === 1" class="is-primary" type="button" :disabled="Boolean(busy)" @click="$emit('pay')">
      <RefreshCw v-if="busy === 'pay'" class="spin" :size="16" /><CreditCard v-else :size="16" />立即支付
    </button>
  </div>
</template>
