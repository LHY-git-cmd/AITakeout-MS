export interface OrderStatusEvent {
  event: string
  orderId?: number
  status?: number
  content?: string
  timestamp?: number
}

const TOKEN_KEY = 'sky-user-token'

class OrderSocket {
  private socket: WebSocket | null = null
  private heartbeatTimer: number | undefined
  private reconnectTimer: number | undefined
  private reconnectAttempt = 0
  private shouldReconnect = false

  connect(token = localStorage.getItem(TOKEN_KEY)) {
    if (!token || typeof WebSocket === 'undefined') return
    this.shouldReconnect = true
    this.clearReconnect()
    if (this.socket
      && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) return

    const base = import.meta.env.VITE_WS_URL || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/`
    const separator = base.includes('?') ? '&' : '?'
    const sid = `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const url = `${base}${sid}${separator}role=user&token=${encodeURIComponent(token)}`
    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempt = 0
      this.startHeartbeat()
      this.send({ event: 'orders.subscribe' })
    }
    socket.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data) as OrderStatusEvent
        if (event.event === 'order.status.changed') {
          window.dispatchEvent(new CustomEvent<OrderStatusEvent>('sky:order-status', { detail: event }))
        }
      } catch {
        // Ignore malformed server messages; the HTTP refresh remains authoritative.
      }
    }
    socket.onerror = () => {
      // The following close event carries the actionable close code.
    }
    socket.onclose = (event) => {
      this.stopHeartbeat()
      if (this.socket === socket) this.socket = null
      if (event.code === 1008) {
        this.shouldReconnect = false
        localStorage.removeItem(TOKEN_KEY)
        window.dispatchEvent(new CustomEvent('sky:unauthorized'))
        return
      }
      this.scheduleReconnect()
    }
  }

  disconnect() {
    this.shouldReconnect = false
    this.clearReconnect()
    this.stopHeartbeat()
    this.socket?.close()
    this.socket = null
  }

  private send(payload: Record<string, unknown>) {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(payload))
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => this.send({ event: 'ping' }), 25_000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer !== undefined) window.clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = undefined
  }

  private scheduleReconnect() {
    if (!this.shouldReconnect || this.reconnectTimer !== undefined) return
    const delay = Math.min(30_000, 1_000 * 2 ** this.reconnectAttempt++)
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = undefined
      this.connect()
    }, delay)
  }

  private clearReconnect() {
    if (this.reconnectTimer !== undefined) window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = undefined
  }
}

export const orderSocket = new OrderSocket()
