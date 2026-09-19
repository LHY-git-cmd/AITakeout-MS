/**
 * 验证并发 401 只触发一次 Refresh Cookie 换取，并在成功后重放原请求。
 */
// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const axiosMock = vi.hoisted(() => {
  const handlers: { rejected?: (error: unknown) => Promise<unknown> } = {}
  const main = {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn((_success, rejected) => { handlers.rejected = rejected }) },
    },
    request: vi.fn(async (config) => ({ config })),
  }
  const refresh = { post: vi.fn() }
  const create = vi.fn()
  create.mockReturnValueOnce(main).mockReturnValueOnce(refresh)
  return { create, handlers, main, refresh }
})

vi.mock('axios', () => ({ default: { create: axiosMock.create } }))

import '@/api/http'

describe('HTTP 会话刷新', () => {
  beforeEach(() => {
    axiosMock.main.request.mockClear()
    axiosMock.refresh.post.mockReset()
  })

  it('合并并发 401 为一次刷新并重放每个请求', async () => {
    axiosMock.refresh.post.mockResolvedValue({
      status: 200,
      data: { code: 1, data: {
        accessToken: 'refreshed-token',
        user: { id: 7, name: '测试用户', phone: '13800138000', avatar: null },
      } },
    })
    const rejected = axiosMock.handlers.rejected!
    const first = { response: { status: 401, data: { code: 0 } }, config: { url: '/user/order/a', headers: {} } }
    const second = { response: { status: 401, data: { code: 0 } }, config: { url: '/user/order/b', headers: {} } }

    await Promise.all([rejected(first), rejected(second)])

    expect(axiosMock.refresh.post).toHaveBeenCalledTimes(1)
    expect(axiosMock.main.request).toHaveBeenCalledTimes(2)
    expect(first.config.headers).toMatchObject({ authentication: 'refreshed-token' })
    expect(second.config.headers).toMatchObject({ authentication: 'refreshed-token' })
  })
})
