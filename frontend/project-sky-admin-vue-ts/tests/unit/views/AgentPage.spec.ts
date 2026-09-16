import AgentPage from '@/views/agent/index.vue'
import type { AgentStreamEnvelope } from '@/utils/agentSse'
import { confirmAgentTool, getAgentHealth, submitAgentTask } from '@/api/agent'
import { TextDecoder, TextEncoder } from 'util'

jest.mock('@/api/agent', () => ({
  listAgentSessions: jest.fn(),
  getAgentSession: jest.fn(),
  updateAgentSession: jest.fn(),
  submitAgentTask: jest.fn(),
  getAgentHealth: jest.fn(),
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
  it('starts with an unknown service state until the first health response arrives', () => {
    const state = (AgentPage as any).options.data()

    expect(state.agentHealth).toEqual({ status: 'unknown', errorType: null })
  })

  it('uses the safe degraded health result without clearing the active session', async() => {
    const methods = agentMethods()
    ;(getAgentHealth as jest.Mock).mockResolvedValueOnce({
      data: { data: { status: 'degraded', errorType: 'QDRANT_UNAVAILABLE' } },
    })
    const context: any = {
      agentHealth: { status: 'unknown', errorType: null },
      sessionId: 'session-1',
      messages: [{ role: 'user', content: '保留中的会话' }],
      pageDestroyed: false,
    }

    await methods.refreshAgentHealth.call(context)

    expect(context.agentHealth).toEqual({
      status: 'degraded',
      errorType: 'QDRANT_UNAVAILABLE',
    })
    expect(context.sessionId).toBe('session-1')
    expect(context.messages).toHaveLength(1)
  })

  it('keeps the draft and session intact when an unavailable knowledge base is selected', async() => {
    const methods = agentMethods()
    ;(submitAgentTask as jest.Mock).mockClear()
    const context: any = {
      draft: '基于当前知识库的问题',
      running: false,
      agentHealth: { status: 'degraded', errorType: 'QDRANT_UNAVAILABLE' },
      knowledgeUnavailable: true,
      kbId: 'kb-1',
      messages: [],
      $message: { error: jest.fn() },
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
      applyStreamFailure: jest.fn(),
    }

    await methods.send.call(context)

    expect(submitAgentTask).not.toHaveBeenCalled()
    expect(context.draft).toBe('基于当前知识库的问题')
    expect(context.messages).toEqual([])
    expect(context.$message.error).toHaveBeenCalledWith(
      '知识库服务暂不可用，请等待恢复或新建普通会话'
    )
  })

  it('blocks new questions while the health endpoint reports the agent offline', async() => {
    const methods = agentMethods()
    ;(submitAgentTask as jest.Mock).mockClear()
    const context: any = {
      draft: '服务恢复前的问题',
      running: false,
      agentHealth: { status: 'offline', errorType: 'LLM_CALL_FAILED' },
      kbId: '',
      knowledgeUnavailable: false,
      messages: [],
      $message: { error: jest.fn() },
    }

    await methods.send.call(context)

    expect(submitAgentTask).not.toHaveBeenCalled()
    expect(context.draft).toBe('服务恢复前的问题')
    expect(context.messages).toEqual([])
    expect(context.$message.error).toHaveBeenCalledWith('Agent 服务不可用，请恢复服务后重试')
  })

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

    expect(assistant.content).toBe('已经收到的部分内容')
    expect(assistant.failure).toMatchObject({
      kind: 'unknown',
      message: '请求未完成',
      taskId: 'task-1',
    })
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

    expect(assistant.content).toBe('已经收到的部分内容')
    expect(assistant.failure).toMatchObject({
      kind: 'network',
      message: '连接中断，正在恢复',
    })
    expect(context.$message.error).toHaveBeenCalledWith('连接中断，正在恢复')
  })

  it('keeps received tokens and presents a safe recovery action for submit errors', () => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '已经收到的部分内容',
      citations: [],
    }
    const context = {
      $message: { error: jest.fn() },
    }

    methods.applyStreamFailure.call(context, assistant, {
      response: {
        status: 429,
        data: { message: 'http://internal/queue?token=secret' },
      },
    }, 'task-1')

    expect(assistant.content).toBe('已经收到的部分内容')
    expect(assistant.failure).toEqual({
      kind: 'busy',
      message: 'Agent 当前繁忙',
      action: '稍后重试，不会自动重提原任务',
      retryable: true,
      recovery: 'none',
      taskId: 'task-1',
    })
    expect(JSON.stringify(assistant.failure)).not.toContain('secret')
    expect(context.$message.error).toHaveBeenCalledWith('Agent 当前繁忙')
  })

  it('uses the safe expired-confirmation recovery instead of a backend error message', async() => {
    const methods = agentMethods()
    ;(confirmAgentTool as jest.Mock).mockRejectedValueOnce({
      response: {
        status: 409,
        data: {
          errorType: 'CONFIRMATION_EXPIRED',
          message: 'java.lang.IllegalStateException at http://internal?token=secret',
        },
      },
    })
    const message: any = {
      confirmation: {
        id: 'confirmation-1',
        processing: false,
        decided: false,
        decision: '',
      },
    }
    const context = { $message: { error: jest.fn(), success: jest.fn() } }

    await methods.decideTool.call(context, message, true)

    expect(message.confirmation).toMatchObject({
      processing: false,
      decided: true,
      decision: '操作确认已过期，重新发起原业务请求',
    })
    expect(message.confirmation.decision).not.toContain('secret')
    expect(context.$message.error).toHaveBeenCalledWith('操作确认已过期，重新发起原业务请求')
  })

  it('does not append a string task error payload to received tokens', () => {
    const methods = agentMethods()
    const assistant: any = { role: 'assistant', content: '已接收内容', citations: [] }
    const context = {
      streamStatus: '正在思考...',
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
    }

    methods.applyAgentEvent.call(context, {
      taskId: 'task-safe-2',
      seqNo: 2,
      event: 'task_error',
      data: 'java.lang.IllegalStateException at http://internal?token=secret',
    }, assistant)

    expect(assistant.content).toBe('已接收内容')
    expect(assistant.failure).toMatchObject({
      kind: 'unknown',
      taskId: 'task-safe-2',
    })
    expect(JSON.stringify(assistant)).not.toContain('secret')
  })

  it('keeps partial content and recovery entry while a refreshed task is still running', async() => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '已经收到的部分内容',
      failure: { kind: 'conflict', recovery: 'refreshTask', taskId: 'task-1' },
    }
    const context: any = {
      sessionId: 'session-1',
      streamStatus: '连接恢复已停止，请查询任务状态',
      getAgentTaskStatus: jest.fn().mockResolvedValue(1),
      openSession: jest.fn().mockResolvedValue(undefined),
      $message: { success: jest.fn(), error: jest.fn() },
    }

    await methods.recoverAgentFailure.call(context, assistant)

    expect(context.getAgentTaskStatus).toHaveBeenCalledWith('task-1')
    expect(context.openSession).not.toHaveBeenCalled()
    expect(assistant.content).toBe('已经收到的部分内容')
    expect(assistant.failure.taskId).toBe('task-1')
    expect(context.streamStatus).toBe('任务仍在后台执行，可稍后查询。')
    expect(context.$message.error).not.toHaveBeenCalled()
  })

  it('reloads the session only after a refreshed task reaches a terminal state', async() => {
    const methods = agentMethods()
    const context: any = {
      sessionId: 'session-1',
      getAgentTaskStatus: jest.fn().mockResolvedValue(2),
      openSession: jest.fn().mockResolvedValue(undefined),
      $message: { success: jest.fn(), error: jest.fn() },
    }

    await methods.recoverAgentFailure.call(context, {
      failure: { kind: 'timeout', recovery: 'refreshTask', taskId: 'task-terminal' },
    })

    expect(context.openSession).toHaveBeenCalledWith('session-1')
    expect(context.$message.error).not.toHaveBeenCalled()
  })

  it('keeps local content and task recovery when refreshing task status fails', async() => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '已经收到的部分内容',
      failure: { kind: 'network', recovery: 'refreshTask', taskId: 'task-lookup' },
    }
    const context: any = {
      sessionId: 'session-1',
      getAgentTaskStatus: jest.fn().mockRejectedValue(new Error('network offline')),
      openSession: jest.fn(),
      $message: { success: jest.fn(), error: jest.fn() },
    }

    await methods.recoverAgentFailure.call(context, assistant)

    expect(assistant.content).toBe('已经收到的部分内容')
    expect(assistant.failure.taskId).toBe('task-lookup')
    expect(context.openSession).not.toHaveBeenCalled()
    expect(context.$message.error).toHaveBeenCalledWith('恢复操作未完成，请稍后重试')
  })

  it('starts a plain session and keeps the original question for knowledge recovery', async() => {
    const methods = agentMethods()
    const assistant: any = {
      role: 'assistant',
      content: '',
      failure: { kind: 'knowledge', recovery: 'newPlainSession' },
    }
    const context: any = {
      sessionId: 'session-locked',
      kbId: 'kb-1',
      kbLocked: true,
      draft: '',
      messages: [{ role: 'user', content: '保留这个知识库问题' }, assistant],
      newChat: methods.newChat,
      $message: { success: jest.fn(), error: jest.fn() },
    }

    await methods.recoverAgentFailure.call(context, assistant)

    expect(context.sessionId).toBe('')
    expect(context.kbId).toBe('')
    expect(context.kbLocked).toBe(false)
    expect(context.draft).toBe('保留这个知识库问题')
  })

  it('shows a safe message when refreshing an unavailable service fails', async() => {
    const methods = agentMethods()
    const context: any = {
      refreshAgentHealth: jest.fn().mockResolvedValue(false),
      $message: { success: jest.fn(), error: jest.fn() },
    }

    await methods.recoverAgentFailure.call(context, {
      failure: { kind: 'unavailable', recovery: 'refreshHealth' },
    })

    expect(context.$message.success).not.toHaveBeenCalled()
    expect(context.$message.error).toHaveBeenCalledWith('恢复操作未完成，请稍后重试')
  })

  it('disables confirmation re-submission when a forbidden error is not retryable', async() => {
    const methods = agentMethods()
    ;(confirmAgentTool as jest.Mock).mockRejectedValueOnce({
      response: { status: 403, data: { message: 'http://internal?token=secret' } },
    })
    const message: any = {
      confirmation: { id: 'confirmation-forbidden', taskId: 'task-3', processing: false, decided: false, decision: '' },
    }
    const context = { $message: { error: jest.fn(), success: jest.fn() } }

    await methods.decideTool.call(context, message, true)

    expect(message.confirmation).toMatchObject({ processing: false, decided: true, retryable: false })
    expect(message.failure).toMatchObject({ kind: 'forbidden', taskId: 'task-3' })
    expect(JSON.stringify(message)).not.toContain('secret')
  })

  it('stops recovery after four SSE failures and offers a task status query', async() => {
    const methods = agentMethods()
    const assistant: any = { role: 'assistant', content: '已接收内容', citations: [] }
    const context: any = {
      streamStatus: '连接中断，正在恢复（3/3）...',
      sessionId: 'session-1',
      applyAgentEvent: jest.fn(),
      getAgentTaskStatus: jest.fn().mockRejectedValue(new Error('network offline')),
      openSession: jest.fn(),
      $nextTick: jest.fn(),
      scrollToBottom: jest.fn(),
      $message: { error: jest.fn() },
    }
    const networkError = Object.assign(new Error('offline'), { code: 'ERR_NETWORK', request: {} })
    const fetchFn = jest.fn().mockRejectedValue(networkError)

    await expect(methods.consumeEvents.call(
      context,
      'task-stream-1',
      assistant,
      new AbortController().signal,
      { fetchFn, sleep: async() => undefined }
    )).rejects.toThrow('network offline')
    methods.applyStreamFailure.call(context, assistant, networkError, 'task-stream-1', 'streamStopped')

    expect(fetchFn).toHaveBeenCalledTimes(4)
    expect(assistant.content).toBe('已接收内容')
    expect(assistant.failure).toMatchObject({
      kind: 'network',
      message: '连接恢复已停止，请查询任务状态',
      action: '查询任务状态',
      recovery: 'refreshTask',
      taskId: 'task-stream-1',
    })
    expect(context.$message.error).toHaveBeenCalledWith('连接恢复已停止，请查询任务状态')
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
