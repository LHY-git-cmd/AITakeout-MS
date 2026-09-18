<template>
  <!--
    营业状态设置弹层
    挂在布局根节点（#app 内、侧栏之外）：
    - 能继承主题变量（深浅模式一致）
    - 不受侧栏 overflow:hidden 裁切
    入口在侧栏左下角的 AccountPanel，通过 AccountModule 通信。
  -->
  <el-dialog
    title="营业状态设置"
    :visible.sync="visible"
    width="460px"
    :show-close="false"
    custom-class="status-dialog"
  >
    <el-radio-group v-model="setStatus" class="status-radio-group">
      <el-radio :label="1" class="status-radio">
        <span class="status-radio__title">营业中</span>
        <span class="status-radio__desc"
          >当前餐厅处于营业状态，自动接收任何订单，可点击打烊进入店铺打烊状态。</span
        >
      </el-radio>
      <el-radio :label="0" class="status-radio">
        <span class="status-radio__title">打烊中</span>
        <span class="status-radio__desc"
          >当前餐厅处于打烊状态，仅接受营业时间内的预定订单，可点击营业中手动恢复营业状态。</span
        >
      </el-radio>
    </el-radio-group>
    <span slot="footer" class="dialog-footer">
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">确 定</el-button>
    </span>
  </el-dialog>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import { AccountModule } from '@/store/modules/account'
import { getStatus, setStatus } from '@/api/users'

@Component({
  name: 'StatusDialog',
})
export default class extends Vue {
  private setStatus = 1
  private saving = false

  get visible() {
    return AccountModule.statusDialogVisible
  }

  set visible(val: boolean) {
    if (val) AccountModule.OpenStatusDialog()
    else AccountModule.CloseStatusDialog()
  }

  created() {
    this.syncStatus()
  }

  /** 弹层打开时拉取最新营业状态，避免显示过期值 */
  @Watch('visible')
  onVisibleChange(val: boolean) {
    if (val) this.syncStatus()
  }

  private async syncStatus() {
    try {
      const { data } = await getStatus()
      this.setStatus = Number(data.data) === 0 ? 0 : 1
      AccountModule.SetShopStatus(this.setStatus)
    } catch (e) {
      // 取不到就沿用当前值，不阻塞弹层
    }
  }

  private async handleSave() {
    this.saving = true
    try {
      const { data } = await setStatus(this.setStatus)
      if (data.code === 1) {
        AccountModule.SetShopStatus(this.setStatus)
        this.$message.success('营业状态已更新')
        this.visible = false
      } else {
        this.$message.error(data.msg)
      }
    } catch (e) {
      this.$message.error('营业状态更新失败，请稍后重试')
    } finally {
      this.saving = false
    }
  }
}
</script>

<style lang="scss">
/* ============================================================================
   用非 scoped 块：Element 弹层内容由 el-dialog 渲染，
   带不到父组件的 scoped 属性，写在 scoped 块里不会生效。
   ============================================================================ */
.status-dialog {
  background: var(--surface-raised, #ffffff);
  border: 1px solid var(--border-strong, rgba(140, 165, 220, 0.3));
  border-radius: 10px;
  box-shadow: 0 18px 44px rgba(2, 6, 20, 0.35);

  .el-dialog__header {
    padding: 16px 22px;
    background: transparent;
    border-bottom: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  }

  .el-dialog__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-1, #eef3ff);
  }

  .el-dialog__body {
    padding: 18px 22px 6px;
  }

  .el-dialog__footer {
    padding: 12px 22px 18px;
    border-top: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  }
}

.status-dialog {
  .status-radio-group {
    display: flex;
    flex-direction: column;
    gap: 12px;
    width: 100%;
  }

  .el-radio.status-radio {
    display: flex;
    align-items: flex-start;
    width: 100%;
    margin: 0;
    padding: 14px 16px;
    white-space: normal;
    background: var(--surface-inset, #f7f8fa);
    border: 1px solid var(--border, rgba(140, 165, 220, 0.16));
    border-radius: 8px;
    transition: border-color 0.2s ease, background 0.2s ease;

    &:hover {
      border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
    }

    &.is-checked {
      border-color: var(--action, #3b82f6);
      background: var(--accent-soft, rgba(37, 99, 235, 0.08));
    }
  }

  .el-radio__input {
    margin-top: 2px;
    flex: 0 0 auto;
  }

  .el-radio__inner {
    width: 16px;
    height: 16px;
  }

  .el-radio__input.is-checked .el-radio__inner {
    background: var(--action, #3b82f6);
    border-color: var(--action, #3b82f6);

    &::after {
      width: 5px;
      height: 5px;
      background: #ffffff;
    }
  }

  .el-radio__label {
    flex: 1;
    min-width: 0;
    padding-left: 10px;
    font-weight: 400;
    color: var(--text-2, #c2cde4);
    line-height: 1.5;

    .status-radio__title {
      display: block;
      font-size: 14px;
      font-weight: 600;
      color: var(--text-1, #eef3ff);
      margin-bottom: 6px;
    }

    .status-radio__desc {
      display: block;
      font-size: 12.5px;
      line-height: 1.6;
      color: var(--text-3, #8e9cb8);
      font-weight: normal;
    }
  }

  .el-radio__input.is-checked + .el-radio__label .status-radio__title {
    color: var(--action, #3b82f6);
  }
}
</style>
