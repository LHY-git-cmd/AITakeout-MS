import request from '@/utils/request'

export interface AfterSaleRecord {
  id: number
  requestNo: string
  orderId: number
  userId: number
  requestType: string
  reason: string
  status: string
  reviewReason?: string
  refundNo?: string
  refundAmountCent?: number
  refundStatus?: string
  createTime: string
}

export const listAfterSales = (params: { status?: string; beforeId?: number; limit?: number }) => request({
  url: '/after-sales', method: 'get', params,
})

export const getAfterSale = (id: number) => request({ url: `/after-sales/${id}`, method: 'get' })

export const reviewAfterSale = (id: number, data: { approved: boolean; reason?: string }) => request({
  url: `/after-sales/${id}/review`, method: 'put', data,
})

export const retryRefund = (refundNo: string) => request({
  url: `/after-sales/refunds/${refundNo}/retry`, method: 'post',
})
