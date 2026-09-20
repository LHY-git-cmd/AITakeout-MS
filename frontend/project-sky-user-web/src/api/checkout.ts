import { request } from './http'
import type { SubmittedOrder } from './order'

export type DeliveryMode = 'IMMEDIATE' | 'SCHEDULED'

export interface CheckoutRequest {
  addressBookId: number
  deliveryMode: DeliveryMode
  deliverySlotStart?: string
}

export interface CheckoutSlot {
  start: string
  end: string
  label: string
}

export interface CheckoutItem {
  dishId: number | null
  setmealId: number | null
  name: string
  flavor: string | null
  quantity: number
  unitPriceCent: number
  subtotalCent: number
  image: string | null
}

export interface CheckoutQuote {
  goodsAmountCent: number
  packAmountCent: number
  deliveryFeeCent: number
  discountAmountCent: number
  amountCent: number
  distanceMeters: number
  pricingRuleVersion: string
  estimatedDeliveryTime: string
  availableSlots: CheckoutSlot[]
  items: CheckoutItem[]
  previewToken: string
  expiresAt: string
}

export interface SubmitCheckoutPayload extends CheckoutRequest {
  previewToken: string
  payMethod: number
  remark: string
  deliveryStatus: number
  tablewareNumber: number
  tablewareStatus: number
}

export function previewCheckout(payload: CheckoutRequest, signal?: AbortSignal) {
  return request<CheckoutQuote>({ url: '/user/order/preview', method: 'POST', data: payload, signal })
}

export function submitCheckout(payload: SubmitCheckoutPayload, idempotencyKey: string) {
  return request<SubmittedOrder>({
    url: '/user/order/submit', method: 'POST', data: payload,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}
