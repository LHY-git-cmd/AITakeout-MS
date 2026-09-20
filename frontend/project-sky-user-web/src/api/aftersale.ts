import { request } from './http'

export interface TimelineItem {
  id: number
  eventType: string
  message: string
  operatorType: string
  eventTime: string
}

export interface AfterSaleRecord {
  id: number
  requestNo: string
  orderId: number
  requestType: 'CANCELLATION' | 'AFTER_SALE'
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'REFUND_PROCESSING' | 'COMPLETED' | 'REFUND_FAILED'
  reviewReason: string | null
  refundNo: string | null
  refundAmountCent: number | null
  refundStatus: 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | null
  createTime: string
  reviewedAt: string | null
}

function idempotencyKey(orderId: number) {
  return globalThis.crypto?.randomUUID?.() ?? `after-sale-${orderId}-${Date.now()}`
}

export function applyAfterSale(orderId: number, reason: string, key = idempotencyKey(orderId)) {
  return request<AfterSaleRecord>({
    url: `/user/orders/${orderId}/after-sales`,
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    data: { reason },
  })
}

export function getLatestAfterSale(orderId: number) {
  return request<AfterSaleRecord>({ url: `/user/orders/${orderId}/after-sales/latest`, method: 'GET' })
}

export function getAfterSales(beforeId?: number, limit = 20) {
  return request<AfterSaleRecord[]>({ url: '/user/after-sales', method: 'GET', params: { beforeId, limit } })
}

export function getOrderTimeline(orderId: number) {
  return request<TimelineItem[]>({ url: `/user/orders/${orderId}/timeline`, method: 'GET' })
}
