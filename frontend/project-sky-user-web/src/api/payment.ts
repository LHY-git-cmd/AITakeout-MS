import { request } from './http'

export type PaymentStatus = 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'CLOSED'

export interface PaymentView {
  paymentNo: string
  orderId: number
  status: PaymentStatus
  amountCent: number
  expiresAt: string
  failureCode: string | null
}

interface PaymentRecovery {
  idempotencyKey: string
  paymentNo?: string
}

function storageKey(orderId: number) {
  return `sky-payment-recovery:${orderId}`
}

function newRequestKey() {
  return globalThis.crypto?.randomUUID?.()
    ?? `payment-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function readPaymentRecovery(orderId: number): PaymentRecovery | null {
  try {
    return JSON.parse(sessionStorage.getItem(storageKey(orderId)) || 'null') as PaymentRecovery | null
  } catch {
    sessionStorage.removeItem(storageKey(orderId))
    return null
  }
}

export function clearPaymentRecovery(orderId: number) {
  sessionStorage.removeItem(storageKey(orderId))
}

/** 同一浏览器会话复用幂等键，可在创建响应丢失后安全恢复原支付单。 */
export async function beginOrderPayment(orderId: number) {
  const recovery = readPaymentRecovery(orderId) ?? { idempotencyKey: newRequestKey() }
  sessionStorage.setItem(storageKey(orderId), JSON.stringify(recovery))
  const payment = await request<PaymentView>({
    url: `/user/orders/${orderId}/payments`,
    method: 'POST',
    headers: { 'Idempotency-Key': recovery.idempotencyKey },
  })
  sessionStorage.setItem(storageKey(orderId), JSON.stringify({ ...recovery, paymentNo: payment.paymentNo }))
  return payment
}

export function queryPayment(paymentNo: string) {
  return request<PaymentView>({
    url: `/user/payments/${encodeURIComponent(paymentNo)}`,
    method: 'GET',
  })
}
