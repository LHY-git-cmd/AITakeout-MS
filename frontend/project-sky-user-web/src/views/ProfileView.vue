<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Bell, KeyRound, LogIn, LogOut, MapPin, Pencil, Phone, RotateCcw, ShieldOff, UserRound, WalletCards } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

const authStore = useAuthStore()
const accountStore = useAccountStore()
const uiStore = useUiStore()
const loggingOut = ref(false)
const logoutError = ref('')

function money(cent: number) {
  return (Number(cent || 0) / 100).toFixed(2)
}

async function logout(allDevices = false) {
  if (allDevices && !window.confirm('确定退出全部设备吗？其他设备需要重新登录。')) return
  loggingOut.value = true
  logoutError.value = ''
  try {
    await authStore.logout(allDevices)
    accountStore.reset()
  } catch (error) {
    logoutError.value = error instanceof ApiError
      ? `本机会话已清除，但服务端退出失败：${error.message}`
      : '本机会话已清除，但服务端退出失败，请稍后重试'
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  if (authStore.isAuthenticated && !accountStore.account) void accountStore.load()
})
watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated && !accountStore.account) void accountStore.load()
})
</script>

<template>
  <PageScaffold title="我的">
    <p v-if="logoutError" class="page-error" role="alert">{{ logoutError }}</p>
    <div v-if="authStore.isAuthenticated" class="profile-summary">
      <div class="profile-summary__avatar">
        <img v-if="authStore.user?.avatar" :src="authStore.user.avatar" alt="用户头像" />
        <UserRound v-else :size="30" aria-hidden="true" />
      </div>
      <div class="profile-summary__identity">
        <strong>{{ authStore.displayName }}</strong>
        <span v-if="authStore.user?.phone"><Phone :size="15" aria-hidden="true" /> {{ authStore.user.phone }}</span>
      </div>
      <div class="profile-summary__actions">
        <button type="button" :disabled="loggingOut" @click="logout(false)">
          <LogOut :size="18" aria-hidden="true" /> 退出当前设备
        </button>
        <button type="button" :disabled="loggingOut" @click="logout(true)">
          <ShieldOff :size="18" aria-hidden="true" /> 退出全部设备
        </button>
      </div>
    </div>
    <RouterLink v-if="authStore.isAuthenticated" class="profile-menu-link" to="/profile/edit">
      <Pencil :size="20" aria-hidden="true" />
      <span><strong>编辑个人资料</strong><small>修改昵称和头像</small></span>
    </RouterLink>
    <RouterLink v-if="authStore.isAuthenticated" class="profile-menu-link" to="/profile/security">
      <KeyRound :size="20" aria-hidden="true" />
      <span><strong>账号与安全</strong><small>换绑手机号或修改密码</small></span>
    </RouterLink>
    <div v-else class="profile-guest">
      <div><UserRound :size="30" aria-hidden="true" /></div>
      <strong>登录后查看订单和地址</strong>
      <button type="button" @click="uiStore.openLogin">
        <LogIn :size="18" aria-hidden="true" /> 登录
      </button>
    </div>
    <RouterLink v-if="authStore.isAuthenticated" class="profile-menu-link profile-wallet-link" to="/wallet">
      <WalletCards :size="20" aria-hidden="true" />
      <span><strong>模拟钱包</strong><small>查看余额、领取模拟金和资金流水</small></span>
      <b>¥{{ money(accountStore.availableCent) }}</b>
    </RouterLink>
    <RouterLink class="profile-menu-link" to="/addresses">
      <MapPin :size="20" aria-hidden="true" />
      <span><strong>收货地址</strong><small>管理配送联系人和地址</small></span>
    </RouterLink>
    <RouterLink v-if="authStore.isAuthenticated" class="profile-menu-link" to="/notifications">
      <Bell :size="20" aria-hidden="true" />
      <span><strong>通知中心</strong><small>查看订单、退款和售后消息</small></span>
    </RouterLink>
    <RouterLink v-if="authStore.isAuthenticated" class="profile-menu-link" to="/after-sales">
      <RotateCcw :size="20" aria-hidden="true" />
      <span><strong>退款与售后</strong><small>跟踪审核及退款到账进度</small></span>
    </RouterLink>
  </PageScaffold>
</template>
