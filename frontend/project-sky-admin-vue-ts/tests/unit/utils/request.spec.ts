import request from '@/utils/request'

describe('request response errors', () => {
  it('preserves a structured Agent error when Axios has no request config', async() => {
    const error = {
      response: {
        status: 429,
        data: { errorType: 'CAPACITY_EXCEEDED' },
      },
    }
    const handlers = (request.interceptors.response as any).handlers
    const rejected = handlers[handlers.length - 1].rejected

    await expect(rejected(error)).rejects.toBe(error)
  })

  it('rejects an Agent business failure so the page can classify its structured fields', async() => {
    const response: any = {
      config: { url: '/agent/tasks/submit', method: 'post' },
      data: { code: 0, errorType: 'CAPACITY_EXCEEDED' },
    }
    const handlers = (request.interceptors.response as any).handlers
    const fulfilled = handlers[handlers.length - 1].fulfilled

    await expect(fulfilled(response)).rejects.toMatchObject({ response })
  })
})
