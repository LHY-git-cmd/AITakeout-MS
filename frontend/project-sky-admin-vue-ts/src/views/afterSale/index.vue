<template>
  <div class="app-container" v-loading="loading">
    <el-card>
      <div slot="header" class="toolbar">
        <strong>退款与售后</strong>
        <el-select v-model="status" clearable placeholder="全部状态" @change="load">
          <el-option label="待审核" value="PENDING" />
          <el-option label="退款处理中" value="REFUND_PROCESSING" />
          <el-option label="退款失败" value="REFUND_FAILED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </div>
      <el-table :data="records" empty-text="暂无售后申请">
        <el-table-column prop="requestNo" label="申请单号" min-width="210" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column label="类型" width="100"><template slot-scope="scope">{{ scope.row.requestType === 'CANCELLATION' ? '取消' : '售后' }}</template></el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="退款金额" width="120"><template slot-scope="scope">{{ scope.row.refundAmountCent == null ? '-' : `¥${money(scope.row.refundAmountCent)}` }}</template></el-table-column>
        <el-table-column label="状态" width="130"><template slot-scope="scope"><el-tag :type="tagType(scope.row.status)">{{ label(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="110"><template slot-scope="scope"><el-button size="mini" type="primary" @click="$router.push(`/after-sale/${scope.row.id}`)">处理</el-button></template></el-table-column>
      </el-table>
      <div class="load-more"><el-button v-if="hasMore" :loading="loadingMore" @click="loadMore">加载更多</el-button></div>
    </el-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import { listAfterSales, type AfterSaleRecord } from '@/api/afterSale'

export default Vue.extend({
  data() { return { records: [] as AfterSaleRecord[], status: '', loading: false, loadingMore: false, hasMore: false } },
  mounted() { this.load() },
  methods: {
    money(cent: number) { return (cent / 100).toFixed(2) },
    label(status: string) { return ({ PENDING: '待审核', REFUND_PROCESSING: '退款处理中', REFUND_FAILED: '退款失败', COMPLETED: '已完成', REJECTED: '已拒绝', APPROVED: '已批准' } as any)[status] || status },
    tagType(status: string) { return status === 'REFUND_FAILED' ? 'danger' : status === 'PENDING' ? 'warning' : status === 'COMPLETED' ? 'success' : 'info' },
    async load() {
      this.loading = true
      try { const response: any = await listAfterSales({ status: this.status || undefined, limit: 20 }); this.records = response.data.data || []; this.hasMore = this.records.length === 20 }
      finally { this.loading = false }
    },
    async loadMore() {
      this.loadingMore = true
      try { const response: any = await listAfterSales({ status: this.status || undefined, beforeId: this.records[this.records.length - 1]?.id, limit: 20 }); const page = response.data.data || []; this.records.push(...page); this.hasMore = page.length === 20 }
      finally { this.loadingMore = false }
    },
  },
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.load-more { margin-top: 20px; text-align: center; }
</style>
