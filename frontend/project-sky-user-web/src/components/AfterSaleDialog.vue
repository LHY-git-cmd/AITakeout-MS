<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { AlertCircle, X } from '@lucide/vue'

const props = defineProps<{ open: boolean; orderStatus: number; submitting?: boolean; error?: string }>()
const emit = defineEmits<{ close: []; submit: [reason: string] }>()
const reason = ref('')
const localError = ref('')
const title = computed(() => props.orderStatus <= 3 ? '申请取消订单' : '申请整单售后')
const rule = computed(() => {
  if (props.orderStatus === 1) return '待付款订单将直接取消，不产生退款。'
  if (props.orderStatus === 2) return '取消后将自动全额退回模拟余额。'
  if (props.orderStatus === 3) return '订单制作中，需要商家审核后才能取消并退款。'
  if (props.orderStatus === 4) return '订单配送中，需要商家审核整单售后。'
  return '送达后 24 小时内可申请整单全额售后。'
})

watch(() => props.open, (open) => {
  if (open) localError.value = ''
  else reason.value = ''
})

function submit() {
  if (!reason.value.trim()) {
    localError.value = '请填写申请原因'
    return
  }
  localError.value = ''
  emit('submit', reason.value.trim())
}
</script>

<template>
  <div v-if="open" class="dialog-layer" role="presentation" @click.self="emit('close')">
    <section class="after-sale-dialog" role="dialog" aria-modal="true" aria-labelledby="after-sale-title">
      <header><h2 id="after-sale-title">{{ title }}</h2><button type="button" aria-label="关闭" @click="emit('close')"><X :size="20" /></button></header>
      <p class="after-sale-dialog__rule"><AlertCircle :size="18" aria-hidden="true" />{{ rule }}</p>
      <label for="after-sale-reason">申请原因</label>
      <textarea id="after-sale-reason" v-model="reason" maxlength="255" rows="4" placeholder="请说明取消或售后原因" />
      <p v-if="localError || error" class="page-error" role="alert">{{ localError || error }}</p>
      <footer><button type="button" :disabled="submitting" @click="emit('close')">暂不申请</button><button class="is-primary" type="button" :disabled="submitting" @click="submit">{{ submitting ? '正在提交...' : '确认提交' }}</button></footer>
    </section>
  </div>
</template>
