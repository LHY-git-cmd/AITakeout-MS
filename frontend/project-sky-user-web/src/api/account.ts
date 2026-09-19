import { request } from './http'

export interface MockAccount {
  id: number
  accountNo: string
  accountType: string
  ownerId: number
  availableCent: number
  frozenCent: number
  version: number
  createTime: string
  updateTime: string
}

export interface LedgerEntry {
  id: number
  transferId: number
  accountId: number
  direction: 'DEBIT' | 'CREDIT'
  amountCent: number
  balanceAfterCent: number
  createTime: string
}

export interface GrantResult {
  transferId: number
  transferNo: string
  accountId: number
  amountCent: number
  balanceAfterCent: number
  replayed: boolean
}

export function getMockAccount() {
  return request<MockAccount>({ url: '/user/mock-account', method: 'GET' })
}

export function getAccountLedger() {
  return request<LedgerEntry[]>({ url: '/user/mock-account/ledger', method: 'GET' })
}

export function grantMockMoney(idempotencyKey: string) {
  return request<GrantResult>({
    url: '/user/mock-account/grants',
    method: 'POST',
    params: { idempotencyKey },
  })
}
