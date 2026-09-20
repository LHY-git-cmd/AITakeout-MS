import { request } from './http'

export interface UserNotification {
  id: number
  eventId: string
  type: string
  title: string
  content: string
  orderId: number | null
  read: boolean
  createTime: string
}

export function getNotifications(beforeId?: number, since?: string, limit = 20) {
  return request<UserNotification[]>({ url: '/user/notifications', method: 'GET', params: { beforeId, since, limit } })
}

export function getUnreadCount() {
  return request<{ count: number }>({ url: '/user/notifications/unread-count', method: 'GET' })
}

export function markNotificationRead(id: number) {
  return request<void>({ url: `/user/notifications/${id}/read`, method: 'PUT' })
}

export function markAllNotificationsRead() {
  return request<void>({ url: '/user/notifications/read-all', method: 'PUT' })
}
