<script setup lang="ts">
import { LogIn, LogOut, Phone, UserRound } from '@lucide/vue'
import PageScaffold from '@/components/PageScaffold.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const authStore = useAuthStore()
const uiStore = useUiStore()
</script>

<template>
  <PageScaffold title="我的">
    <div v-if="authStore.isAuthenticated" class="profile-summary">
      <div class="profile-summary__avatar"><UserRound :size="30" aria-hidden="true" /></div>
      <div class="profile-summary__identity">
        <strong>{{ authStore.displayName }}</strong>
        <span v-if="authStore.user?.phone"><Phone :size="15" /> {{ authStore.user.phone }}</span>
      </div>
      <button type="button" @click="authStore.logout">
        <LogOut :size="18" aria-hidden="true" /> 退出登录
      </button>
    </div>
    <div v-else class="profile-guest">
      <div><UserRound :size="30" aria-hidden="true" /></div>
      <strong>登录后查看订单和地址</strong>
      <button type="button" @click="uiStore.openLogin">
        <LogIn :size="18" aria-hidden="true" /> 登录
      </button>
    </div>
  </PageScaffold>
</template>
