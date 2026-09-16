import {
  consumeAgentEventStream,
  type AgentStreamEnvelope,
} from '@/utils/agentSse'
import { TextDecoder, TextEncoder } from 'util'

;(global as any).TextDecoder = TextDecoder

function sseResponse(frames: string[]) {
  const encoder = new TextEncoder()
  let index = 0
  return {
    ok: true,
    body: {
      getReader: () => ({
        read: async() => index < frames.length
          ? { done: false, value: encoder.encode(frames[index++]) }
          : { done: true, value: undefined },
      }),
    },
  } as any
}

function eventFrame(id: number, event: string, data: Record<string, unknown>) {
  return `id: ${id}\nevent: ${event}\ndata: ${JSON.stringify({
    task_id: 'task-1',
    trace_id: 'trace-1',
    seq_no: id,
    event,
    data,
  })}\n\n`
}

describe('consumeAgentEventStream', () => {
  it('delivers sequential events once and returns the greatest sequence number', async() => {
    const received: AgentStreamEnvelope[] = []
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([
      eventFrame(1, 'task_start', { status: 'running' }),
      eventFrame(2, 'token', { content: '你好' }),
      eventFrame(3, 'task_end', { status: 'completed', result: '你好' }),
    ]))

    const result = await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: (event) => received.push(event),
        getTaskStatus: async() => 1,
      },
      { fetchFn }
    )

    expect(received.map((event) => `${event.seqNo}:${event.event}`)).toEqual([
      '1:task_start',
      '2:token',
      '3:task_end',
    ])
    expect(result).toEqual({ lastSeqNo: 3, terminal: true, status: 2 })
  })

  it('ignores replayed sequence numbers so tokens and confirmations are not duplicated', async() => {
    const received: AgentStreamEnvelope[] = []
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([
      eventFrame(1, 'token', { content: '首段' }),
      eventFrame(1, 'token', { content: '重复首段' }),
      eventFrame(2, 'tool_confirmation_required', { confirmation_id: 'confirm-1' }),
      eventFrame(2, 'tool_confirmation_required', { confirmation_id: 'confirm-1' }),
      eventFrame(3, 'task_end', { status: 'completed', result: '完成' }),
    ]))

    await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: (event) => received.push(event),
        getTaskStatus: async() => 1,
      },
      { fetchFn }
    )

    expect(received.map((event) => `${event.seqNo}:${event.event}`)).toEqual([
      '1:token',
      '2:tool_confirmation_required',
      '3:task_end',
    ])
  })

  it('reconnects with Last-Event-ID and resumes after the last delivered event', async() => {
    const received: AgentStreamEnvelope[] = []
    const reconnects: number[] = []
    const sleep = jest.fn().mockResolvedValue(undefined)
    const fetchFn = jest.fn()
      .mockResolvedValueOnce(sseResponse([
        eventFrame(1, 'token', { content: '第一段' }),
      ]))
      .mockResolvedValueOnce(sseResponse([
        eventFrame(1, 'token', { content: '重复第一段' }),
        eventFrame(2, 'task_end', { status: 'completed', result: '第一段' }),
      ]))

    const result = await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: (event) => received.push(event),
        getTaskStatus: async() => 1,
        onReconnect: (attempt) => reconnects.push(attempt),
      },
      { fetchFn, sleep }
    )

    expect(result).toEqual({ lastSeqNo: 2, terminal: true, status: 2 })
    expect(received.map((event) => event.seqNo)).toEqual([1, 2])
    expect(fetchFn).toHaveBeenCalledTimes(2)
    expect(fetchFn.mock.calls[1][1].headers['Last-Event-ID']).toBe('1')
    expect(sleep).toHaveBeenCalledWith(1000)
    expect(reconnects).toEqual([1])
  })

  it('queries the authoritative task status after three reconnects are exhausted', async() => {
    const sleep = jest.fn().mockResolvedValue(undefined)
    const fetchFn = jest.fn().mockImplementation(async() => sseResponse([]))
    const getTaskStatus = jest.fn()
      .mockResolvedValueOnce(1)
      .mockResolvedValueOnce(1)
      .mockResolvedValueOnce(2)

    const result = await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: () => undefined,
        getTaskStatus,
      },
      { fetchFn, sleep }
    )

    expect(fetchFn).toHaveBeenCalledTimes(4)
    expect(getTaskStatus).toHaveBeenCalledTimes(3)
    expect(sleep.mock.calls.map((call) => call[0])).toEqual([
      1000,
      2000,
      4000,
      2000,
      2000,
      2000,
    ])
    expect(result).toEqual({ lastSeqNo: 0, terminal: true, status: 2 })
  })

  it('reports that the task remains in the background after five active status checks', async() => {
    const onBackground = jest.fn()
    const fetchFn = jest.fn().mockImplementation(async() => sseResponse([]))
    const getTaskStatus = jest.fn().mockResolvedValue(1)

    const result = await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: () => undefined,
        getTaskStatus,
        onBackground,
      },
      { fetchFn, sleep: async() => undefined }
    )

    expect(getTaskStatus).toHaveBeenCalledTimes(5)
    expect(onBackground).toHaveBeenCalledTimes(1)
    expect(result).toEqual({ lastSeqNo: 0, terminal: false, status: 1 })
  })

  it('rejects an invalid authoritative task status instead of reporting background execution', async() => {
    const onBackground = jest.fn()
    const fetchFn = jest.fn().mockImplementation(async() => sseResponse([]))

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: () => undefined,
        getTaskStatus: async() => Number.NaN,
        onBackground,
      },
      { fetchFn, sleep: async() => undefined }
    )).rejects.toThrow('Agent task status is invalid')

    expect(onBackground).not.toHaveBeenCalled()
  })

  it('does not reconnect or poll when the caller aborts the stream', async() => {
    const controller = new AbortController()
    const abortError = Object.assign(new Error('aborted'), { name: 'AbortError' })
    const fetchFn = jest.fn().mockImplementation(async() => {
      controller.abort()
      throw abortError
    })
    const sleep = jest.fn().mockResolvedValue(undefined)
    const getTaskStatus = jest.fn().mockResolvedValue(1)

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      controller.signal,
      {
        onEvent: () => undefined,
        getTaskStatus,
      },
      { fetchFn, sleep }
    )).rejects.toMatchObject({ name: 'AbortError' })

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(sleep).not.toHaveBeenCalled()
    expect(getTaskStatus).not.toHaveBeenCalled()
  })

  it('does not start another connection when aborted during reconnect backoff', async() => {
    const controller = new AbortController()
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([]))
    const sleep = jest.fn().mockImplementation(async() => {
      controller.abort()
    })
    const getTaskStatus = jest.fn().mockResolvedValue(1)

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      controller.signal,
      {
        onEvent: () => undefined,
        getTaskStatus,
      },
      { fetchFn, sleep }
    )).rejects.toMatchObject({ name: 'AbortError' })

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(getTaskStatus).not.toHaveBeenCalled()
  })

  it('does not poll task status when aborted during the polling delay', async() => {
    const controller = new AbortController()
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([]))
    let sleepCount = 0
    const sleep = jest.fn().mockImplementation(async() => {
      sleepCount += 1
      if (sleepCount === 4) controller.abort()
    })
    const getTaskStatus = jest.fn().mockResolvedValue(1)

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      controller.signal,
      {
        onEvent: () => undefined,
        getTaskStatus,
      },
      { fetchFn, sleep }
    )).rejects.toMatchObject({ name: 'AbortError' })

    expect(fetchFn).toHaveBeenCalledTimes(4)
    expect(getTaskStatus).not.toHaveBeenCalled()
  })

  it('stops immediately when the SSE id and envelope sequence disagree', async() => {
    const mismatchedFrame = `id: 2\nevent: token\ndata: ${JSON.stringify({
      task_id: 'task-1',
      seq_no: 1,
      event: 'token',
      data: { content: '内容' },
    })}\n\n`
    const fetchFn = jest.fn().mockImplementation(async() =>
      sseResponse([mismatchedFrame]))
    const sleep = jest.fn().mockResolvedValue(undefined)

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: () => undefined,
        getTaskStatus: async() => 1,
      },
      { fetchFn, sleep }
    )).rejects.toThrow('Agent SSE event sequence is invalid')

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(sleep).not.toHaveBeenCalled()
  })

  it('rejects an event envelope belonging to another task', async() => {
    const frame = `id: 1\nevent: task_end\ndata: ${JSON.stringify({
      task_id: 'task-other',
      seq_no: 1,
      event: 'task_end',
      data: { status: 'completed', result: '错误任务内容' },
    })}\n\n`
    const onEvent = jest.fn()
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([frame]))

    await expect(consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent,
        getTaskStatus: async() => 1,
      },
      { fetchFn, sleep: async() => undefined }
    )).rejects.toThrow('Agent SSE task id does not match the subscription')

    expect(onEvent).not.toHaveBeenCalled()
  })

  it('parses CRLF event boundaries split across network chunks', async() => {
    const payload = JSON.stringify({
      task_id: 'task-1',
      seq_no: 1,
      event: 'task_end',
      data: { status: 'completed', result: '完成' },
    })
    const prefix = `id: 1\r\nevent: task_end\r\ndata: ${payload}`
    const received: AgentStreamEnvelope[] = []
    const fetchFn = jest.fn().mockResolvedValue(sseResponse([
      `${prefix}\r`,
      '\n\r',
      '\n',
    ]))

    const result = await consumeAgentEventStream(
      'task-1',
      'token-value',
      new AbortController().signal,
      {
        onEvent: (event) => received.push(event),
        getTaskStatus: async() => 1,
      },
      { fetchFn, sleep: async() => undefined }
    )

    expect(received.map((event) => event.event)).toEqual(['task_end'])
    expect(result).toEqual({ lastSeqNo: 1, terminal: true, status: 2 })
  })
})
