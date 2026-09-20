import { defineStore } from 'pinia'
import {
  getAccountLedger,
  getMockAccount,
  grantMockMoney,
  type LedgerEntry,
  type MockAccount,
} from '@/api/account'
import { ApiError } from '@/api/http'

function requestKey() {
  return globalThis.crypto?.randomUUID?.()
    ?? `grant-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export const useAccountStore = defineStore('account', {
  state: () => ({
    account: null as MockAccount | null,
    entries: [] as LedgerEntry[],
    loading: false,
    granting: false,
    error: '',
    pendingGrantKey: null as string | null,
  }),
  getters: {
    availableCent: (state) => state.account?.availableCent ?? 0,
  },
  actions: {
    async load() {
      if (this.loading) return
      this.loading = true
      this.error = ''
      try {
        const [account, entries] = await Promise.all([getMockAccount(), getAccountLedger()])
        this.account = account
        this.entries = entries ?? []
      } catch (error) {
        this.error = error instanceof ApiError ? error.message : '钱包加载失败，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    async grant() {
      if (this.granting || this.loading) return
      this.granting = true
      this.error = ''
      this.pendingGrantKey ??= requestKey()
      try {
        await grantMockMoney(this.pendingGrantKey)
        this.pendingGrantKey = null
        await this.load()
      } catch (error) {
        this.error = error instanceof ApiError ? error.message : '领取失败，请稍后重试'
      } finally {
        this.granting = false
      }
    },
    reset() {
      this.account = null
      this.entries = []
      this.error = ''
      this.pendingGrantKey = null
    },
  },
})
