/**
 * 验证模拟金领取在网络失败后复用原幂等键，避免用户重试时重复入账。
 */
// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const accountApi = vi.hoisted(() => ({
  getAccountLedger: vi.fn(),
  getMockAccount: vi.fn(),
  grantMockMoney: vi.fn(),
}))

vi.mock('@/api/account', () => accountApi)
vi.mock('@/api/http', () => ({ ApiError: class ApiError extends Error {} }))

import { useAccountStore } from '@/stores/account'

describe('模拟钱包状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('领取失败后重试复用同一个幂等键', async () => {
    accountApi.grantMockMoney.mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ amountCent: 50_000, balanceAfterCent: 100_000 })
    accountApi.getMockAccount.mockResolvedValue({ availableCent: 100_000, frozenCent: 0 })
    accountApi.getAccountLedger.mockResolvedValue([])
    const store = useAccountStore()

    await store.grant()
    const firstKey = accountApi.grantMockMoney.mock.calls[0][0]
    expect(store.pendingGrantKey).toBe(firstKey)

    await store.grant()

    expect(accountApi.grantMockMoney.mock.calls[1][0]).toBe(firstKey)
    expect(store.pendingGrantKey).toBeNull()
    expect(store.availableCent).toBe(100_000)
  })
})
