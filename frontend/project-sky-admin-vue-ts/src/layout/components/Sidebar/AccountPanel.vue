<template>
  <!-- 侧栏左下角：主题切换 + 营业状态设置（账户已移至导航栏右侧） -->
  <div class="account-panel" :class="{ compact: collapsed }">
    <!-- 主题切换 -->
    <button
      type="button"
      class="panel-btn theme-btn"
      :aria-label="isLightTheme ? '切换到深色星空模式' : '切换到白天模式'"
      :title="isLightTheme ? '切换到深色星空模式' : '切换到白天模式'"
      @click="toggleTheme"
    >
      <i :class="isLightTheme ? 'el-icon-moon' : 'el-icon-sunny'" />
      <span v-if="!collapsed">{{ isLightTheme ? '深色模式' : '白天模式' }}</span>
    </button>

    <!-- 营业状态设置 -->
    <button
      type="button"
      class="panel-btn status-btn"
      @click="handleStatus"
    >
      <i class="el-icon-time" />
      <span v-if="!collapsed">营业状态设置</span>
    </button>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import { ThemeModule } from '@/store/modules/theme'
import { AccountModule } from '@/store/modules/account'

@Component({
  name: 'AccountPanel',
})
export default class extends Vue {
  /** 侧栏折叠（80px）时只显示图标 */
  @Prop({ default: false }) private collapsed!: boolean

  get isLightTheme() {
    return ThemeModule.isLight
  }

  private toggleTheme() {
    ThemeModule.ToggleTheme()
  }

  /** 营业状态设置：弹层挂在布局根节点，这里只负责打开 */
  private handleStatus() {
    AccountModule.OpenStatusDialog()
  }
}
</script>

<style lang="scss" scoped>
.account-panel {
  padding: 10px 12px;
  border-top: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.panel-btn {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-2, #c2cde4);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;

  i {
    font-size: 15px;
    flex: 0 0 auto;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.12);
    border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
    color: var(--text-1, #eef3ff);
  }
}

/* 折叠态（80px 侧栏）：只留图标居中 */
.account-panel.compact {
  padding: 10px 8px;

  .panel-btn {
    justify-content: center;
    padding: 0;
  }
}
</style>


