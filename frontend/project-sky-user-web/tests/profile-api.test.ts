import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/http', () => ({ request: requestMock }))

import { uploadAvatar } from '@/api/profile'

describe('头像上传请求', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue({})
  })

  it('让浏览器为 FormData 自动生成 multipart boundary', async () => {
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })

    await uploadAvatar(file)

    const config = requestMock.mock.calls[0][0]
    expect(config.headers).toEqual({ 'Content-Type': undefined })
    expect(config.data).toBeInstanceOf(FormData)
    expect(config.data.get('file')).toBe(file)
  })
})
