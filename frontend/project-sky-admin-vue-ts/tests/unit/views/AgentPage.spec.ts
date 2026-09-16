import AgentPage from '@/views/agent/index.vue'
import type { AgentStreamEnvelope } from '@/utils/agentSse'
import { submitAgentTask } from '@/api/agent'
import { TextDecoder, TextEncoder } from 'util'

jest.mock('@/api/agent', () => ({
  listAgentSessions: jest.fn(),
  getAgentSession: jest.fn(),
  updateAgentSession: jest.fn(),
  submitAgentTask: jest.fn(),
  getAgentTask: jest.fn(),
  cancelAgentTask: jest.fn(),
  confirmAgentTool: jest.fn(),
  rejectAgentTool: jest.fn(),
}))

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
    seq_no: id,
    event,
    data,
  })}\n\n`
}

function agentMethods() {
  return (AgentPage as any).options.methods
}

describe('AgentPage stream events', () => {
  it('applies token content and terminal citations to the current assistant message', () => {
    const methods = agentMethods()
    const assistant = {
      role: 'assistant',
      content: '',
      citations: [],
      confirmation: null,
    }
    const context = {
      streamStatus: '正在恢复连接…',
      normalizeCitation: methods.normalizeCitation,
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }
    const tokenEvent: AgentStreamEnvelope = {
      taskId: 'task-1',
      seqNo: 1,
      event: 'token',
      data: { content: '你好' },
    }
    const endEvent: AgentStreamEnvelope = {
      taskId: 'task-1',
      seqNo: 2,
      event: 'task_end',
      data: {
        result: '你好',
        citations: [{
          document_id: 'doc-1',
          document_version: 1,
          chunk_id: 'chunk-1',
          file_name: '手册.md',
        }],
      },
    }

    methods.applyAgentEvent.call(context, tokenEvent, assistant)
    methods.applyAgentEvent.call(context, endEvent, assistant)

    expect(assistant.content).toBe('你好')
    expect(assistant.citations).toEqual([expect.objectContaining({
      documentId: 'doc-1',
      chunkId: 'chunk-1',
      fileName: '手册.md',
    })])
    expect(context.streamStatus).toBe('正在思考...')
  })

  it('creates one confirmation model and gives cancelled tasks a visible result', () => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '',
      citations: [],
      confirmation: null,
    }
    const context = {
      streamStatus: '正在思考...',
      normalizeCitation: methods.normalizeCitation,
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }

    methods.applyAgentEvent.call(context, {
      taskId: 'task-1',
      seqNo: 1,
      event: 'tool_confirmation_required',
      data: {
        confirmation_id: 'confirm-1',
        summary: '修改店铺营业状态',
        expires_at: '2026-09-15T10:00:00Z',
      },
    }, assistant)
    methods.applyAgentEvent.call(context, {
      taskId: 'task-1',
      seqNo: 2,
      event: 'task_cancelled',
      data: { status: 'cancelled' },
    }, assistant)

    expect(assistant.confirmation).toEqual(expect.objectContaining({
      id: 'confirm-1',
      summary: '修改店铺营业状态',
    }))
    expect(assistant.content).toBe('已停止生成。')
  })

  it('preserves received tokens when the task ends with an error', () => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '',
      citations: [],
      confirmation: null,
    }
    const context = {
      streamStatus: '正在思考...',
      normalizeCitation: methods.normalizeCitation,
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }

    methods.applyAgentEvent.call(context, {
      taskId: 'task-1',
      seqNo: 1,
      event: 'token',
      data: { content: '已经收到的部分内容' },
    }, assistant)
    methods.applyAgentEvent.call(context, {
      taskId: 'task-1',
      seqNo: 2,
      event: 'task_error',
      data: { error_msg: '模型连接中断' },
    }, assistant)

    expect(assistant.content).toContain('已经收到的部分内容')
    expect(assistant.content).toContain('模型连接中断')
  })

  it('uses the reliable stream client to resume and render the final answer', async() => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '',
      citations: [],
      confirmation: null,
    }
    const context: any = {
      streamStatus: '正在思考...',
      sessionId: 'session-1',
      applyAgentEvent: (event: AgentStreamEnvelope, message: any) =>
        methods.applyAgentEvent.call(context, event, message),
      getAgentTaskStatus: jest.fn().mockResolvedValue(1),
      openSession: jest.fn().mockResolvedValue(undefined),
      normalizeCitation: methods.normalizeCitation,
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }
    const fetchFn = jest.fn()
      .mockResolvedValueOnce(sseResponse([
        eventFrame(1, 'token', { content: '第一段' }),
      ]))
      .mockResolvedValueOnce(sseResponse([
        eventFrame(2, 'task_end', { status: 'completed', result: '第一段' }),
      ]))

    await methods.consumeEvents.call(
      context,
      'task-1',
      assistant,
      new AbortController().signal,
      { fetchFn, sleep: async() => undefined }
    )

    expect(fetchFn).toHaveBeenCalledTimes(2)
    expect(fetchFn.mock.calls[1][1].headers['Last-Event-ID']).toBe('1')
    expect(assistant.content).toBe('第一段')
    expect(context.streamStatus).toBe('正在思考...')
  })

  it('reloads the session when MySQL has a terminal state missing from the stream', async() => {
    const methods = agentMethods()
    const assistant: any = { role: 'assistant', content: '', citations: [] }
    const context: any = {
      streamStatus: '正在思考...',
      sessionId: 'session-1',
      applyAgentEvent: jest.fn(),
      getAgentTaskStatus: jest.fn().mockResolvedValue(2),
      openSession: jest.fn().mockResolvedValue(undefined),
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }
    const fetchFn = jest.fn().mockImplementation(async() => sseResponse([]))

    await methods.consumeEvents.call(
      context,
      'task-1',
      assistant,
      new AbortController().signal,
      { fetchFn, sleep: async() => undefined }
    )

    expect(context.getAgentTaskStatus).toHaveBeenCalledTimes(1)
    expect(context.openSession).toHaveBeenCalledWith('session-1')
  })

  it('preserves partial content and shows a recovery path while the task remains active', async() => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '已经收到的部分内容',
      citations: [],
    }
    const context: any = {
      streamStatus: '正在思考...',
      sessionId: 'session-1',
      applyAgentEvent: jest.fn(),
      getAgentTaskStatus: jest.fn().mockResolvedValue(1),
      openSession: jest.fn().mockResolvedValue(undefined),
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }
    const fetchFn = jest.fn().mockImplementation(async() => sseResponse([]))

    const result = await methods.consumeEvents.call(
      context,
      'task-1',
      assistant,
      new AbortController().signal,
      { fetchFn, sleep: async() => undefined }
    )

    expect(result).toEqual({ lastSeqNo: 0, terminal: false, status: 1 })
    expect(assistant.content).toContain('已经收到的部分内容')
    expect(assistant.content).toContain('任务仍在后台执行，可重新进入会话查看。')
    expect(context.streamStatus).toBe('任务仍在后台执行，可重新进入会话查看。')
    expect(context.openSession).not.toHaveBeenCalled()
  })

  it('preserves partial content when stream recovery ultimately fails', () => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '已经收到的部分内容',
      citations: [],
    }
    const context = {
      cancelRequested: false,
      $message: { error: jest.fn() },
    }

    methods.applyStreamFailure.call(context, assistant)

    expect(assistant.content).toContain('已经收到的部分内容')
    expect(assistant.content).toContain('连接恢复失败')
    expect(context.$message.error).toHaveBeenCalledWith('AI 服务连接恢复失败')
  })

  it('closes only the local stream when the page is destroyed', () => {
    const abort = jest.fn()
    const context = {
      streamController: { abort },
      cancelRequested: false,
    }

    const registeredHook = (AgentPage as any).options.beforeDestroy
    const beforeDestroy = Array.isArray(registeredHook)
      ? registeredHook[0]
      : registeredHook
    beforeDestroy.call(context)

    expect(abort).toHaveBeenCalledTimes(1)
    expect(context.cancelRequested).toBe(false)
  })

  it('does not open a stream when the page is destroyed while task submission is pending', async() => {
    const methods = agentMethods()
    let resolveSubmission: (value: any) => void = () => undefined
    const submission = new Promise((resolve) => {
      resolveSubmission = resolve
    })
    ;(submitAgentTask as jest.Mock).mockReturnValueOnce(submission)
    const context: any = {
      draft: '测试问题',
      running: false,
      messages: [],
      cancelling: false,
      cancelRequested: false,
      currentTaskId: '',
      streamController: null,
      streamStatus: '正在思考...',
      sessionId: '',
      kbId: '',
      kbLocked: false,
      model: '',
      pageDestroyed: false,
      consumeEvents: jest.fn(),
      loadSessions: jest.fn(),
      applyStreamFailure: methods.applyStreamFailure,
      scrollToBottom: jest.fn(),
      $nextTick: jest.fn(),
      $message: { error: jest.fn() },
    }

    const sendPromise = methods.send.call(context)
    await Promise.resolve()
    const registeredHook = (AgentPage as any).options.beforeDestroy
    const beforeDestroy = Array.isArray(registeredHook)
      ? registeredHook[0]
      : registeredHook
    beforeDestroy.call(context)
    resolveSubmission({
      data: { data: { taskId: 'task-1', sessionId: 'session-1' } },
    })
    await sendPromise

    expect(context.consumeEvents).not.toHaveBeenCalled()
    expect(context.pageDestroyed).toBe(true)
  })
})
