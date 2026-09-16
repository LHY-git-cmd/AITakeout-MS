/**
 * 管理端 Agent SSE 消费器：解析统一事件信封，并以 Java/MySQL 任务状态作为终态依据。
 */
export type AgentTerminalEvent = 'task_end' | 'task_error' | 'task_cancelled'

export interface AgentStreamEnvelope {
  taskId: string
  traceId?: string
  seqNo: number
  event: string
  data: Record<string, any> | string
}

export interface AgentStreamCallbacks {
  onEvent(event: AgentStreamEnvelope): void
  getTaskStatus(taskId: string): Promise<number>
  onReconnect?(attempt: number, maxAttempts: number): void
  onBackground?(): void
}

export interface AgentStreamDependencies {
  fetchFn?: typeof fetch
  sleep?: (milliseconds: number) => Promise<void>
  baseUrl?: string
}

export interface AgentStreamResult {
  lastSeqNo: number
  terminal: boolean
  status?: number
}

export class AgentSseProtocolError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AgentSseProtocolError'
  }
}

const TERMINAL_STATUS: Record<AgentTerminalEvent, number> = {
  task_end: 2,
  task_error: 3,
  task_cancelled: 4,
}

function isTerminalStatus(status: number) {
  return status === 2 || status === 3 || status === 4
}

function createAbortError() {
  const error = new Error('Agent SSE consumption aborted')
  error.name = 'AbortError'
  return error
}

async function waitForDelay(
  sleep: (milliseconds: number) => Promise<void>,
  milliseconds: number,
  signal: AbortSignal
) {
  if (signal.aborted) throw createAbortError()
  await new Promise<void>((resolve, reject) => {
    let settled = false
    const finish = (callback: () => void) => {
      if (settled) return
      settled = true
      signal.removeEventListener('abort', onAbort)
      callback()
    }
    const onAbort = () => finish(() => reject(createAbortError()))
    signal.addEventListener('abort', onAbort, { once: true })
    sleep(milliseconds).then(
      () => finish(() => signal.aborted ? reject(createAbortError()) : resolve()),
      (error) => finish(() => reject(error))
    )
  })
}

function parseFrame(frame: string): AgentStreamEnvelope | null {
  const lines = frame.split('\n')
  const idLine = lines.find((line) => line.startsWith('id:'))
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .join('')
  if (!data) return null

  const payload = JSON.parse(data)
  const seqNo = Number(payload.seq_no ?? payload.seqNo)
  const eventId = idLine ? Number(idLine.slice(3).trim()) : seqNo
  if (!Number.isInteger(seqNo) || seqNo <= 0 || eventId !== seqNo) {
    throw new AgentSseProtocolError('Agent SSE event sequence is invalid')
  }
  return {
    taskId: String(payload.task_id ?? payload.taskId ?? ''),
    traceId: payload.trace_id ?? payload.traceId,
    seqNo,
    event: String(payload.event ?? ''),
    data: payload.data ?? {},
  }
}

export async function consumeAgentEventStream(
  taskId: string,
  token: string,
  signal: AbortSignal,
  callbacks: AgentStreamCallbacks,
  dependencies: AgentStreamDependencies = {}
): Promise<AgentStreamResult> {
  const fetchFn = dependencies.fetchFn || fetch
  const sleep = dependencies.sleep || ((milliseconds: number) =>
    new Promise<void>((resolve) => setTimeout(resolve, milliseconds)))
  const baseUrl = dependencies.baseUrl ?? process.env.VUE_APP_BASE_API ?? '/api'
  let lastSeqNo = 0
  let status: number | undefined
  let reconnectAttempts = 0

  while (status === undefined) {
    if (signal.aborted) throw createAbortError()
    const headers: Record<string, string> = {
      token,
      Accept: 'text/event-stream',
    }
    if (lastSeqNo > 0) headers['Last-Event-ID'] = String(lastSeqNo)

    try {
      const response = await fetchFn(`${baseUrl}/agent/tasks/${taskId}/events`, {
        headers,
        signal,
      })
      if (!response.ok || !response.body) {
        throw new Error('Agent SSE is unavailable')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (status === undefined) {
        const chunk = await reader.read()
        if (chunk.done) break
        buffer += decoder.decode(chunk.value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n')
        const frames = buffer.split('\n\n')
        buffer = frames.pop() || ''
        for (const frame of frames) {
          const event = parseFrame(frame)
          if (!event || event.seqNo <= lastSeqNo) continue
          if (event.taskId !== taskId) {
            throw new AgentSseProtocolError(
              'Agent SSE task id does not match the subscription'
            )
          }
          callbacks.onEvent(event)
          lastSeqNo = event.seqNo
          status = TERMINAL_STATUS[event.event as AgentTerminalEvent]
          if (status !== undefined) break
        }
      }
    } catch (error) {
      if (signal.aborted || error instanceof AgentSseProtocolError) throw error
    }

    if (status !== undefined || signal.aborted || reconnectAttempts >= 3) break
    reconnectAttempts += 1
    callbacks.onReconnect?.(reconnectAttempts, 3)
    await waitForDelay(sleep, 2 ** (reconnectAttempts - 1) * 1000, signal)
  }

  if (signal.aborted) throw createAbortError()
  if (status === undefined && !signal.aborted) {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      await waitForDelay(sleep, 2000, signal)
      status = await callbacks.getTaskStatus(taskId)
      if (!Number.isInteger(status) || status < 0 || status > 4) {
        throw new AgentSseProtocolError('Agent task status is invalid')
      }
      if (isTerminalStatus(status)) break
    }
    if (status !== undefined && !isTerminalStatus(status)) {
      callbacks.onBackground?.()
    }
  }

  return {
    lastSeqNo,
    terminal: status !== undefined && isTerminalStatus(status),
    status,
  }
}
