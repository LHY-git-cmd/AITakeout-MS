import { defineStore } from 'pinia'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type UserNotification,
} from '@/api/notification'
import { ApiError } from '@/api/http'

const PAGE_SIZE = 20

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    items: [] as UserNotification[],
    unreadCount: 0,
    loading: false,
    loadingMore: false,
    hasMore: true,
    error: '',
    lastEventTime: null as string | null,
  }),
  actions: {
    async refresh() {
      if (this.loading) return
      this.loading = true
      this.error = ''
      try {
        const [items, unread] = await Promise.all([getNotifications(undefined, undefined, PAGE_SIZE), getUnreadCount()])
        this.items = items ?? []
        this.unreadCount = Number(unread.count || 0)
        this.hasMore = this.items.length === PAGE_SIZE
        this.rememberNewest()
      } catch (error) {
        this.error = error instanceof ApiError ? error.message : '通知加载失败，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      try {
        const page = await getNotifications(this.items.at(-1)?.id, undefined, PAGE_SIZE)
        this.items.push(...(page ?? []))
        this.hasMore = (page?.length ?? 0) === PAGE_SIZE
      } finally {
        this.loadingMore = false
      }
    },
    async catchUp() {
      try {
        const [updates, unread] = await Promise.all([
          getNotifications(undefined, this.lastEventTime ?? undefined, PAGE_SIZE),
          getUnreadCount(),
        ])
        const known = new Set(this.items.map(item => item.id))
        this.items = [...(updates ?? []).filter(item => !known.has(item.id)), ...this.items]
        this.unreadCount = Number(unread.count || 0)
        this.rememberNewest()
      } catch {
        // 重连补拉失败时保留已有通知，下次重连继续尝试。
      }
    },
    async markRead(item: UserNotification) {
      if (item.read) return
      await markNotificationRead(item.id)
      item.read = true
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },
    async markAllRead() {
      await markAllNotificationsRead()
      this.items.forEach(item => { item.read = true })
      this.unreadCount = 0
    },
    reset() {
      this.items = []
      this.unreadCount = 0
      this.hasMore = true
      this.error = ''
      this.lastEventTime = null
    },
    rememberNewest() {
      if (this.items[0]?.createTime) this.lastEventTime = this.items[0].createTime
    },
  },
})
