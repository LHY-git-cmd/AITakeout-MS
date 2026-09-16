import { normalizeAgentError } from '@/utils/agentErrors'

describe('normalizeAgentError', () => {
  const cases = [
    ['forbidden HTTP response', { response: { status: 403, data: {} } }, 'forbidden', '当前账号无权执行此操作', '联系管理员或更换账号', false],
    ['conflicting task HTTP response', { response: { status: 409, data: {} } }, 'conflict', '任务或确认状态已经变化', '刷新任务/确认状态', true],
    ['rate limited HTTP response', { response: { status: 429, data: {} } }, 'busy', 'Agent 当前繁忙', '稍后重试，不会自动重提原任务', true],
    ['capacity structured response', { response: { status: 400, data: { errorType: 'CAPACITY_EXCEEDED' } } }, 'busy', 'Agent 当前繁忙', '稍后重试，不会自动重提原任务', true],
    ['legacy capacity response', { response: { status: 200, data: { msg: 'AI任务队列已满，请稍后重试' } } }, 'busy', 'Agent 当前繁忙', '稍后重试，不会自动重提原任务', true],
    ['unavailable HTTP response', { response: { status: 503, data: {} } }, 'unavailable', 'AI 依赖暂不可用', '查看服务状态，恢复后重试', true],
    ['LLM first token timeout', { response: { status: 500, data: { code: 'LLM_FIRST_TOKEN_TIMEOUT' } } }, 'timeout', '模型响应超时', '查询任务终态后允许手动重试', true],
    ['knowledge base structured response', { response: { status: 500, data: { error_type: 'QDRANT_UNAVAILABLE' } } }, 'knowledge', '知识库暂不可用', '新建普通会话并保留原问题', true],
    ['network interruption', { code: 'ERR_NETWORK', request: {} }, 'network', '连接中断，正在恢复', '自动执行有限 SSE 重连', true],
    ['expired confirmation takes precedence over conflict', { response: { status: 409, data: { errorType: 'CONFIRMATION_EXPIRED' } } }, 'confirmationExpired', '操作确认已过期', '重新发起原业务请求', true],
    ['forbidden response beats an expired confirmation payload', { response: { status: 403, data: { errorType: 'CONFIRMATION_EXPIRED' } } }, 'forbidden', '当前账号无权执行此操作', '联系管理员或更换账号', false],
    ['unavailable response beats a legacy capacity message', { response: { status: 503, data: { msg: 'AI任务队列已满，请稍后重试' } } }, 'unavailable', 'AI 依赖暂不可用', '查看服务状态，恢复后重试', true],
    ['ambiguous processed or expired confirmation falls back to conflict', { response: { status: 409, data: { msg: '确认凭证已处理或已过期' } } }, 'conflict', '任务或确认状态已经变化', '刷新任务/确认状态', true],
    ['explicit legacy confirmation expiration uses the reinit action', { response: { status: 200, data: { msg: '确认已过期' } } }, 'confirmationExpired', '操作确认已过期', '重新发起原业务请求', true],
  ] as const

  it.each(cases)('maps %s to a safe recovery presentation', (
    _name,
    error,
    kind,
    message,
    action,
    retryable
  ) => {
    expect(normalizeAgentError(error)).toMatchObject({
      kind,
      message,
      action,
      retryable,
    })
  })

  it('uses a task id but never exposes an unknown error payload', () => {
    const presentation = normalizeAgentError({
      response: {
        status: 500,
        data: { message: 'java.lang.IllegalStateException at http://internal/api?token=secret' },
      },
    }, 'task-safe-1')

    expect(presentation).toEqual({
      kind: 'unknown',
      message: '请求未完成',
      action: '请使用任务 ID 联系管理员定位日志',
      retryable: true,
      recovery: 'refreshTask',
      taskId: 'task-safe-1',
    })
    expect(JSON.stringify(presentation)).not.toContain('internal')
    expect(JSON.stringify(presentation)).not.toContain('secret')
  })

  it('treats a submit network failure as a manual retry, not SSE recovery', () => {
    expect(normalizeAgentError(
      { code: 'ERR_NETWORK', request: {} },
      'task-submit-1',
      'submit'
    )).toMatchObject({
      kind: 'network',
      message: '网络连接失败，请检查网络后手动重试',
      action: '检查网络后手动重试，不会自动重提原任务',
      taskId: 'task-submit-1',
    })
  })
})
