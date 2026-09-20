<template>
  <div class="app-container" v-loading="loading">
    <el-card>
      <div slot="header">用户模拟账户</div>
      <el-table :data="accounts" empty-text="暂无账户数据">
        <el-table-column prop="ownerId" label="用户ID" />
        <el-table-column label="可用余额（元）"><template slot-scope="scope">{{ formatCent(scope.row.availableCent) }}</template></el-table-column>
        <el-table-column label="冻结余额（元）"><template slot-scope="scope">{{ formatCent(scope.row.frozenCent) }}</template></el-table-column>
        <el-table-column label="操作" width="120"><template slot-scope="scope"><el-button data-test="adjust-open" size="mini" type="primary" @click="openAdjust(scope.row)">调账</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog title="账户调账" :visible.sync="dialogVisible" width="460px">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户" prop="userId"><span>{{ form.userId }}</span></el-form-item>
        <el-form-item label="调整金额（分）" prop="deltaCent"><el-input v-model.number="form.deltaCent" /></el-form-item>
        <el-form-item label="调账原因" prop="reason"><el-input v-model="form.reason" type="textarea" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialogVisible = false">取消</el-button><el-button data-test="adjust" type="primary" :loading="submitting" @click="submit">确认调账</el-button></span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import { adjustAccount, listUserAccounts } from '@/api/mockAccount'

export default Vue.extend({
  data() {
    return {
      loading: false,
      submitting: false,
      dialogVisible: false,
      accounts: [] as any[],
      form: { userId: 0, deltaCent: 0, reason: '', idempotencyKey: '' },
      rules: {
        deltaCent: [{ validator: (_: any, value: number, done: any) => value === 0 ? done(new Error('调整金额不能为0')) : done(), trigger: 'blur' }],
        reason: [{ required: true, message: '请输入调账原因', trigger: 'blur' }],
      },
    }
  },
  mounted() { this.load() },
  methods: {
    formatCent(value: number) { return (value / 100).toFixed(2) },
    async load() { this.loading = true; try { const response: any = await listUserAccounts(); this.accounts = response.data.data || [] } finally { this.loading = false } },
    openAdjust(account: any) { this.form = { userId: account.ownerId, deltaCent: 0, reason: '', idempotencyKey: `admin-${Date.now()}` }; this.dialogVisible = true },
    submit() {
      if (this.submitting) return
      this.submitting = true
      ;(this.$refs.form as any).validate(async(valid: boolean) => {
        if (!valid) { this.submitting = false; return }
        const signed = this.formatCent(this.form.deltaCent)
        try {
          await (this as any).$confirm(`用户 ${this.form.userId} 将调整 ${signed}元，原因：${this.form.reason}`, '确认账户调账', { type: 'warning' })
          await adjustAccount(this.form)
          this.$message.success('调账成功')
          this.dialogVisible = false
          await this.load()
        } catch (error) {
          if (error !== 'cancel' && error !== 'close') this.$message.error('调账失败，请稍后重试')
        } finally { this.submitting = false }
      })
    },
  },
})
</script>
