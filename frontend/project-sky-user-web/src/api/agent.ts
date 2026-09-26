/** 用户端Agent会话、任务提交和带JWT的SSE断线续传客户端。 */
import { getAccessToken, request } from './http'

export interface AgentSession {
  sessionId: string
  title: string | null
  status: number
  messageCount: number
}

export interface AgentSubmitResult {
  taskId: string
  sessionId: string
  eventsUrl: string
  status: number
}

export interface AgentStreamEvent<T = Record<string, unknown>> {
  task_id: string
  trace_id?: string | null
  seq_no: number
  event: string
  data: T
}

export interface AgentMessageHistory {
  messageId: string
  taskId: string | null
  role: 1 | 2 | 3
  content: string
  contentType: string
  seqNo: number
}

export interface AgentSessionDetail {
  session: AgentSession
  messages: AgentMessageHistory[]
}

export function createAgentSession(title = '饱饱助手对话') {
  return request<AgentSession>({ url: '/user/agent/sessions', method: 'POST', data: { title } })
}

export function getAgentSession(sessionId: string) {
  return request<AgentSessionDetail>({ url: `/user/agent/sessions/${sessionId}`, method: 'GET' })
}

export function submitAgentTask(
  taskId: string,
  message: string,
  sessionId: string | null,
  clientContext: Record<string, unknown>,
) {
  return request<AgentSubmitResult>({
    url: '/user/agent/tasks',
    method: 'POST',
    headers: { 'Idempotency-Key': taskId },
    data: { sessionId, message, clientContext },
  })
}

export function decideAgentConfirmation(confirmationId: string, approved: boolean) {
  const action = approved ? 'approve' : 'reject'
  return request<Record<string, unknown>>({
    url: `/user/agent/confirmations/${confirmationId}/${action}`,
    method: 'POST',
  })
}

function decodeBlock(block: string): AgentStreamEvent | null {
  let eventName = 'message'
  let eventId = 0
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    else if (line.startsWith('id:')) eventId = Number(line.slice(3).trim()) || 0
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return null
  const value = JSON.parse(dataLines.join('\n')) as Partial<AgentStreamEvent>
  return {
    task_id: String(value.task_id ?? ''),
    trace_id: value.trace_id,
    seq_no: Number(value.seq_no ?? eventId),
    event: String(value.event ?? eventName),
    data: (value.data ?? {}) as Record<string, unknown>,
  }
}

/** 按SSE空行边界增量解析字节流，正确处理跨网络分片的JSON。 */
export async function* parseAgentSse(
  stream: ReadableStream<Uint8Array>,
): AsyncGenerator<AgentStreamEvent> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const parsed = decodeBlock(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        if (parsed) yield parsed
        boundary = buffer.indexOf('\n\n')
      }
      if (done) break
    }
    const parsed = decodeBlock(buffer.trim())
    if (parsed) yield parsed
  } finally {
    reader.releaseLock()
  }
}

const TERMINAL_EVENTS = new Set(['task_completed', 'task_failed', 'task_cancelled'])

/** 使用fetch携带JWT订阅SSE；意外断线时从最后序号自动续传。 */
export async function streamAgentEvents(
  taskId: string,
  onEvent: (event: AgentStreamEvent) => void | Promise<void>,
  signal: AbortSignal,
  initialLastEventId = 0,
) {
  let lastEventId = initialLastEventId
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  for (let attempt = 0; attempt < 3 && !signal.aborted; attempt += 1) {
    const token = getAccessToken()
    if (!token) throw new Error('请先登录后使用饱饱助手')
    const response = await fetch(`${baseUrl}/user/agent/tasks/${taskId}/events`, {
      headers: {
        Accept: 'text/event-stream',
        authentication: token,
        'Last-Event-ID': String(lastEventId),
      },
      credentials: 'include',
      signal,
    })
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('sky:unauthorized'))
      throw new Error('登录状态已过期，请重新登录')
    }
    if (!response.ok || !response.body) throw new Error('助手连接失败，请稍后重试')

    let terminal = false
    for await (const event of parseAgentSse(response.body)) {
      if (event.seq_no <= lastEventId) continue
      lastEventId = event.seq_no
      await onEvent(event)
      if (TERMINAL_EVENTS.has(event.event)) {
        terminal = true
        break
      }
    }
    if (terminal || signal.aborted) return lastEventId
    await new Promise<void>((resolve, reject) => {
      const timeout = window.setTimeout(resolve, 400 * (attempt + 1))
      signal.addEventListener('abort', () => {
        window.clearTimeout(timeout)
        reject(new DOMException('Aborted', 'AbortError'))
      }, { once: true })
    })
  }
  throw new Error('助手连接已中断，请重试')
}
