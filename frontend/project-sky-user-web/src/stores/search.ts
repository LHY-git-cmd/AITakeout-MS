import { defineStore } from 'pinia'
import { clearSearchHistory, getSearchHistory, recordSearchHistory, searchProducts, type ProductSearchItem } from '@/api/search'

interface SearchSnapshot {
  keyword: string
  items: ProductSearchItem[]
  nextCursor: string | null
  hasMore: boolean
  searched: boolean
  scrollY: number
}

export const useSearchStore = defineStore('search', {
  state: () => ({
    keyword: '',
    items: [] as ProductSearchItem[],
    nextCursor: null as string | null,
    hasMore: false,
    loading: false,
    loadingMore: false,
    searched: false,
    error: '',
    history: [] as string[],
    snapshots: {} as Record<string, SearchSnapshot>,
    controller: null as AbortController | null,
  }),
  getters: {
    retryable: (state) => Boolean(state.error && state.items.length && state.hasMore),
  },
  actions: {
    merge(incoming: ProductSearchItem[]) {
      const existing = new Set(this.items.map((item) => item.stableKey))
      this.items.push(...incoming.filter((item) => !existing.has(item.stableKey)))
    },
    async search(keyword: string, saveHistory = false) {
      const normalized = keyword.trim().replace(/\s+/g, ' ')
      this.controller?.abort()
      this.controller = new AbortController()
      this.keyword = normalized
      this.items = []
      this.nextCursor = null
      this.hasMore = false
      this.searched = Boolean(normalized)
      this.error = ''
      if (!normalized) return
      this.loading = true
      try {
        const page = await searchProducts(normalized, null, 20, this.controller.signal)
        this.items = page.items
        this.nextCursor = page.nextCursor
        this.hasMore = page.hasMore
        if (saveHistory) {
          await recordSearchHistory(normalized)
          await this.loadHistory()
        }
      } catch (cause) {
        if ((cause as { code?: string })?.code !== 'ERR_CANCELED') this.error = '搜索失败，请检查网络后重试'
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (this.loading || this.loadingMore || !this.hasMore || !this.nextCursor) return
      this.loadingMore = true
      this.error = ''
      try {
        const page = await searchProducts(this.keyword, this.nextCursor)
        this.merge(page.items)
        this.nextCursor = page.nextCursor
        this.hasMore = page.hasMore
      } catch {
        this.error = '后续商品加载失败，已保留当前结果'
      } finally {
        this.loadingMore = false
      }
    },
    reset() {
      this.controller?.abort()
      this.keyword = ''
      this.items = []
      this.nextCursor = null
      this.hasMore = false
      this.loading = false
      this.loadingMore = false
      this.searched = false
      this.error = ''
    },
    save(routeKey: string, scrollY: number) {
      this.snapshots[routeKey] = {
        keyword: this.keyword, items: [...this.items], nextCursor: this.nextCursor,
        hasMore: this.hasMore, searched: this.searched, scrollY,
      }
    },
    restore(routeKey: string) {
      const snapshot = this.snapshots[routeKey]
      if (!snapshot) return null
      this.keyword = snapshot.keyword
      this.items = [...snapshot.items]
      this.nextCursor = snapshot.nextCursor
      this.hasMore = snapshot.hasMore
      this.searched = snapshot.searched
      this.error = ''
      return snapshot.scrollY
    },
    async loadHistory() {
      try { this.history = await getSearchHistory() ?? [] } catch { this.history = [] }
    },
    async clearHistory() {
      await clearSearchHistory()
      this.history = []
    },
  },
})
