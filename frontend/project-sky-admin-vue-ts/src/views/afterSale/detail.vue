<template>
  <div class="app-container" v-loading="loading">
    <el-page-header content="售后详情" @back="$router.push('/after-sale')" />
    <el-card v-if="record" class="detail-card">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请单号">{{ record.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ record.orderId }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ record.userId }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">{{ record.requestType === 'CANCELLATION' ? '取消申请' : '整单售后' }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ record.reason }}</el-descriptions-item>
        <el-descriptions-item label="实付/退款金额">{{ record.refundAmountCent == null ? '审核通过后生成退款单' : `¥${money(record.refundAmountCent)}` }}</el-descriptions-item>
        <el-descriptions-item label="退款来源">{{ record.requestType === 'AFTER_SALE' ? '商家结算账户或平台待结算账户' : '平台待结算账户' }}</el-descriptions-item>
        <el-descriptions-item v-if="record.refundNo" label="退款单号">{{ record.refundNo }}</el-descriptions-item>
        <el-descriptions-item v-if="record.refundStatus" label="退款状态">{{ record.refundStatus }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="record.status === 'PENDING'" class="actions">
        <el-button data-test="reject" :disabled="submitting" @click="rejectVisible = true">拒绝</el-button>
        <el-button type="primary" :loading="submitting" @click="approve">批准整单退款</el-button>
      </div>
      <el-alert v-if="record.status === 'REFUND_FAILED'" title="退款失败，资金仍保持冻结；重试不会重复冻结。" type="error" show-icon :closable="false" />
      <div v-if="record.status === 'REFUND_FAILED'" class="actions"><el-button type="danger" :loading="submitting" @click="retry">重试原退款单</el-button></div>
    </el-card>
    <el-dialog title="拒绝售后申请" :visible.sync="rejectVisible" width="480px">
      <el-form label-position="top"><el-form-item label="拒绝原因" :error="rejectError"><el-input v-model="rejectReason" data-test="reject-reason" type="textarea" :rows="4" maxlength="255" show-word-limit /></el-form-item></el-form>
      <span slot="footer"><el-button @click="rejectVisible = false">取消</el-button><el-button data-test="reject-submit" type="danger" :loading="submitting" @click="reject">确认拒绝</el-button></span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import { getAfterSale, retryRefund, reviewAfterSale, type AfterSaleRecord } from '@/api/afterSale'

export default Vue.extend({
  data() { return { record: null as AfterSaleRecord | null, loading: false, submitting: false, rejectVisible: false, rejectReason: '', rejectError: '' } },
  mounted() { this.load() },
  methods: {
    money(cent: number) { return (cent / 100).toFixed(2) },
    async load() { this.loading = true; try { const response: any = await getAfterSale(Number(this.$route.params.id)); this.record = response.data.data } finally { this.loading = false } },
    async approve() {
      if (!this.record) return
      const amount = this.record.refundAmountCent == null ? '订单实付全额' : `¥${this.money(this.record.refundAmountCent)}`
      try { await this.$confirm(`确认批准并退还${amount}？退款金额不可修改。`, '批准售后', { type: 'warning' }) } catch { return }
      await this.submitReview(true)
    },
    async reject() {
      if (!this.rejectReason.trim()) { this.rejectError = '请填写拒绝原因'; return }
      this.rejectError = ''
      await this.submitReview(false, this.rejectReason.trim())
      this.rejectVisible = false
    },
    async submitReview(approved: boolean, reason?: string) {
      if (!this.record || this.submitting) return
      this.submitting = true
      try { await reviewAfterSale(this.record.id, { approved, reason }); this.$message.success('审核结果已提交'); await this.load() }
      catch { this.$message.error('审核提交失败，请稍后重试') }
      finally { this.submitting = false }
    },
    async retry() {
      if (!this.record?.refundNo || this.submitting) return
      this.submitting = true
      try { await retryRefund(this.record.refundNo); this.$message.success('已重试原退款单'); await this.load() }
      catch { this.$message.error('退款重试失败，请稍后重试') }
      finally { this.submitting = false }
    },
  },
})
</script>

<style scoped>
.detail-card { margin-top: 20px; }
.actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }
</style>
