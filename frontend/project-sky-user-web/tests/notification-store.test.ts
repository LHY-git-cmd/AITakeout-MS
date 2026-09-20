/** 验证通知断线重连后按时间补拉并更新未读数。 */
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getNotifications: vi.fn(), getUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(), markAllNotificationsRead: vi.fn(),
}))
vi.mock('@/api/notification', () => api)

import { useNotificationStore } from '@/stores/notification'

describe('通知补拉', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('重连后合并新通知且不重复旧事件', async () => {
    api.getNotifications.mockResolvedValueOnce([{ id: 1, eventId: 'E1', type: 'ORDER', title: '已接单', content: '制作中', orderId: 7, read: false, createTime: '2026-09-21T10:00:00' }])
      .mockResolvedValueOnce([
        { id: 2, eventId: 'E2', type: 'REFUND', title: '退款成功', content: '已到账', orderId: 7, read: false, createTime: '2026-09-21T10:05:00' },
        { id: 1, eventId: 'E1', type: 'ORDER', title: '已接单', content: '制作中', orderId: 7, read: false, createTime: '2026-09-21T10:00:00' },
      ])
    api.getUnreadCount.mockResolvedValueOnce({ count: 1 }).mockResolvedValueOnce({ count: 2 })
    const store = useNotificationStore()
    await store.refresh()
    await store.catchUp()

    expect(store.items.map(item => item.id)).toEqual([2, 1])
    expect(store.unreadCount).toBe(2)
    expect(api.getNotifications).toHaveBeenLastCalledWith(undefined, '2026-09-21T10:00:00', 20)
  })
})
