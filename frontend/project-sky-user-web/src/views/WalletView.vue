<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { ArrowDownLeft, ArrowUpRight, CircleDollarSign, RefreshCw, WalletCards } from '@lucide/vue'
import PageScaffold from '@/components/PageScaffold.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const accountStore = useAccountStore()
const authStore = useAuthStore()
const uiStore = useUiStore()
const entries = computed(() => accountStore.entries)

function money(cent: number) {
  return (Number(cent || 0) / 100).toFixed(2)
}

function loadWallet() {
  if (authStore.isAuthenticated) void accountStore.load()
  else uiStore.openLogin()
}

onMounted(loadWallet)
watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void accountStore.load()
  else accountStore.reset()
})
</script>

<template>
  <PageScaffold title="模拟钱包" description="余额和每一笔变动均由服务端账本记录">
    <section v-if="authStore.isAuthenticated" class="wallet-balance" :aria-busy="accountStore.loading">
      <div class="wallet-balance__icon"><WalletCards :size="28" aria-hidden="true" /></div>
      <div><span>可用模拟余额</span><strong><small>¥</small>{{ money(accountStore.availableCent) }}</strong></div>
      <div class="wallet-balance__frozen"><span>冻结金额</span><b>¥{{ money(accountStore.account?.frozenCent ?? 0) }}</b></div>
      <button type="button" :disabled="accountStore.granting || accountStore.loading" @click="accountStore.grant">
        <RefreshCw v-if="accountStore.granting" class="spin" :size="18" aria-hidden="true" />
        <CircleDollarSign v-else :size="18" aria-hidden="true" />
        {{ accountStore.granting ? '领取中' : '领取 ¥500.00 模拟金' }}
      </button>
      <p>余额上限 ¥10,000.00；模拟金仅用于测试点餐，不代表真实资金。</p>
    </section>

    <p v-if="accountStore.error" class="page-error wallet-error" role="alert">
      {{ accountStore.error }}
      <button type="button" @click="accountStore.load">重新加载</button>
    </p>

    <section v-if="authStore.isAuthenticated" class="wallet-ledger">
      <header><h2>资金流水</h2><span>{{ entries.length }} 笔</span></header>
      <div v-if="accountStore.loading && !entries.length" class="content-empty" role="status">
        <RefreshCw class="spin" :size="26" aria-hidden="true" />正在加载流水...
      </div>
      <div v-else-if="!entries.length" class="content-empty">
        <CircleDollarSign :size="32" aria-hidden="true" /><strong>暂无资金流水</strong>
      </div>
      <ol v-else class="ledger-list">
        <li v-for="entry in entries" :key="entry.id">
          <span :class="['ledger-list__icon', `is-${entry.direction.toLowerCase()}`]">
            <ArrowDownLeft v-if="entry.direction === 'CREDIT'" :size="18" aria-hidden="true" />
            <ArrowUpRight v-else :size="18" aria-hidden="true" />
          </span>
          <div><strong>{{ entry.direction === 'CREDIT' ? '模拟资金入账' : '订单支付支出' }}</strong><small>{{ entry.createTime }}</small></div>
          <div class="ledger-list__amount">
            <b>{{ entry.direction === 'CREDIT' ? '+' : '-' }}¥{{ money(entry.amountCent) }}</b>
            <small>余额 ¥{{ money(entry.balanceAfterCent) }}</small>
          </div>
        </li>
      </ol>
    </section>

    <div v-else class="content-empty wallet-guest">
      <WalletCards :size="36" aria-hidden="true" /><strong>登录后查看模拟钱包</strong>
      <button type="button" @click="uiStore.openLogin">登录</button>
    </div>
  </PageScaffold>
</template>
