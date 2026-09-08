import { request } from './http'

export interface SubmitOrderPayload {
  addressBookId: number
  payMethod: number
  remark: string
  estimatedDeliveryTime: string
  deliveryStatus: number
  tablewareNumber: number
  tablewareStatus: number
  packAmount: number
  amount: number
}

export interface SubmittedOrder {
  id: number
  orderNumber: string
  orderAmount: number
  orderTime: string
}

export interface PaymentResult {
  mockPay: boolean
  timeStamp: string
  nonceStr: string
  signType: string
  packageStr: string
  paySign: string
}

export interface OrderDetailItem {
  id: number
  name: string
  orderId: number
  dishId: number | null
  setmealId: number | null
  dishFlavor: string | null
  number: number
  amount: number
  image: string | null
}

export interface OrderRecord {
  id: number
  number: string
  status: number
  payStatus: number
  payMethod: number
  amount: number
  orderTime: string
  checkoutTime: string | null
  estimatedDeliveryTime: string | null
  deliveryTime: string | null
  deliveryStatus: number
  packAmount: number
  tablewareNumber: number
  tablewareStatus: number
  remark: string | null
  phone: string
  address: string
  consignee: string
  cancelReason: string | null
  rejectionReason: string | null
  orderDishes: string | null
  orderDetailList: OrderDetailItem[]
}

export interface OrderPage {
  total: number
  records: OrderRecord[]
}

export function submitOrder(payload: SubmitOrderPayload) {
  return request<SubmittedOrder>({ url: '/user/order/submit', method: 'POST', data: payload })
}

export function payOrder(orderNumber: string) {
  return request<PaymentResult>({
    url: '/user/order/payment',
    method: 'PUT',
    data: { orderNumber, payMethod: 1 },
  })
}

export function getOrderPage(page: number, pageSize: number, status?: number) {
  return request<OrderPage>({
    url: '/user/order/historyOrders',
    method: 'GET',
    params: { page, pageSize, ...(status ? { status } : {}) },
  })
}

export function getOrderDetail(id: number) {
  return request<OrderRecord>({ url: `/user/order/orderDetail/${id}`, method: 'GET' })
}

export function cancelOrder(id: number) {
  return request<void>({ url: `/user/order/cancel/${id}`, method: 'PUT' })
}

export function repeatOrder(id: number) {
  return request<void>({ url: `/user/order/repetition/${id}`, method: 'POST' })
}

export function remindOrder(id: number) {
  return request<void>({ url: `/user/order/reminder/${id}`, method: 'GET' })
}
