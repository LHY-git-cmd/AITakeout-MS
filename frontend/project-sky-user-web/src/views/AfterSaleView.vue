<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw, RotateCcw } from '@lucide/vue'
import PageScaffold from '@/components/PageScaffold.vue'
import { getAfterSales, type AfterSaleRecord } from '@/api/aftersale'
import { ApiError } from '@/api/http'

const records = ref<AfterSaleRecord[]>([])
const loading = ref(true)
const error = ref('')
const hasMore = ref(true)

const labels: Record<AfterSaleRecord['status'], string> = {
  PENDING: '待商家审核', APPROVED: '审核已通过', REJECTED: '申请未通过',
  REFUND_PROCESSING: '退款处理中', COMPLETED: '处理完成', REFUND_FAILED: '退款失败',
}

async function load(more = false) {
  loading.value = true
  error.value = ''
  try {
    const page = await getAfterSales(more ? records.value.at(-1)?.id : undefined)
    records.value = more ? [...records.value, ...page] : page
    hasMore.value = page.length === 20
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '售后记录加载失败'
  } finally { loading.value = false }
}

onMounted(() => { void load() })
</script>

<template>
  <PageScaffold title="退款与售后" description="查看取消审核和整单退款进度">
    <p v-if="error" class="page-error" role="alert">{{ error }}</p>
    <div v-if="loading && !records.length" class="content-empty"><RefreshCw class="spin" :size="28" />正在加载售后记录...</div>
    <div v-else-if="!records.length" class="content-empty"><RotateCcw :size="34" /><strong>暂无售后记录</strong></div>
    <div v-else class="after-sale-list">
      <RouterLink v-for="item in records" :key="item.id" :to="`/orders/${item.orderId}`">
        <header><strong>{{ item.requestType === 'CANCELLATION' ? '取消申请' : '整单售后' }}</strong><b :class="{ 'is-danger': item.status === 'REFUND_FAILED' }">{{ labels[item.status] }}</b></header>
        <p>{{ item.reason }}</p>
        <dl><dt>订单</dt><dd>#{{ item.orderId }}</dd><template v-if="item.refundAmountCent"><dt>退款金额</dt><dd>¥{{ (item.refundAmountCent / 100).toFixed(2) }}</dd></template><template v-if="item.reviewReason"><dt>处理说明</dt><dd>{{ item.reviewReason }}</dd></template></dl>
      </RouterLink>
    </div>
    <button v-if="hasMore && records.length" class="load-more" type="button" :disabled="loading" @click="load(true)">{{ loading ? '加载中...' : '加载更多' }}</button>
  </PageScaffold>
</template>
