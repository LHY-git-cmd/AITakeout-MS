/** 验证凭据不在 URL 中，且只有服务端确认认证后才开始订阅与心跳。 */
// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { orderSocket } from '@/services/orderSocket'

class FakeSocket {
  static OPEN = 1
  static CONNECTING = 0
  static instances: FakeSocket[] = []
  readyState = FakeSocket.OPEN
  onopen?: () => void
  onmessage?: (event: { data: string }) => void
  onclose?: (event: { code: number }) => void
  send = vi.fn()
  close = vi.fn()
  constructor(public url: string) { FakeSocket.instances.push(this) }
}

describe('WebSocket authentication', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeSocket.instances = []
    vi.stubGlobal('WebSocket', FakeSocket)
  })
  afterEach(() => {
    orderSocket.disconnect()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('sends token only in the authentication frame and waits for confirmation', () => {
    orderSocket.connect('test-private-access-token')
    const socket = FakeSocket.instances[0]!
    expect(socket.url).not.toContain('token')
    socket.onopen?.()
    expect(socket.send).toHaveBeenCalledTimes(1)
    expect(JSON.parse(socket.send.mock.calls[0]![0])).toEqual({
      event: 'authenticate', role: 'user', token: 'test-private-access-token',
    })
    socket.onmessage?.({ data: JSON.stringify({ event: 'connected' }) })
    expect(socket.send).toHaveBeenLastCalledWith(JSON.stringify({ event: 'orders.subscribe' }))
    vi.advanceTimersByTime(25_000)
    expect(socket.send).toHaveBeenLastCalledWith(JSON.stringify({ event: 'ping' }))
  })

  it('stops reconnecting after authentication rejection', () => {
    orderSocket.connect('test-invalid-token')
    FakeSocket.instances[0]!.onclose?.({ code: 1008 })
    vi.advanceTimersByTime(60_000)
    expect(FakeSocket.instances).toHaveLength(1)
  })
})
