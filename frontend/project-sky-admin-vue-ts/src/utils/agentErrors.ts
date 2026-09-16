export type AgentErrorKind =
  | 'forbidden'
  | 'conflict'
  | 'busy'
  | 'unavailable'
  | 'timeout'
  | 'knowledge'
  | 'network'
  | 'confirmationExpired'
  | 'unknown'

export interface AgentErrorPresentation {
  kind: AgentErrorKind
  message: string
  action: string
  retryable: boolean
  taskId?: string
}

interface AgentErrorPayload {
  [key: string]: unknown
  code?: unknown
  errorType?: unknown
  message?: unknown
  msg?: unknown
  taskId?: unknown
}

const structuredCodes = (payload: AgentErrorPayload) => [
  payload.code,
  payload.errorType,
  payload.error_type,
].filter((value): value is string => typeof value === 'string')
  .map((value) => value.toUpperCase())

function presentation(
  kind: AgentErrorKind,
  message: string,
  action: string,
  retryable: boolean,
  taskId?: string
): AgentErrorPresentation {
  return { kind, message, action, retryable, ...(taskId ? { taskId } : {}) }
}

/**
 * 将 Agent 的 HTTP、SSE 与网络异常转换为可安全展示的恢复指引。
 * 优先使用状态码和结构化错误码；文本仅用于兼容旧服务的已知业务提示。
 */
export function normalizeAgentError(error: any, fallbackTaskId?: string): AgentErrorPresentation {
  const response = error && error.response
  const payload: AgentErrorPayload = response && response.data && typeof response.data === 'object'
    ? response.data
    : {}
  const status = Number(response && response.status)
  const codes = structuredCodes(payload)
  const compatibilityMessage = typeof payload.message === 'string'
    ? payload.message
    : typeof payload.msg === 'string' ? payload.msg : ''
  const taskId = typeof payload.taskId === 'string'
    ? payload.taskId
    : typeof payload.task_id === 'string' ? payload.task_id : fallbackTaskId

  if (codes.some((code) => ['CONFIRMATION_EXPIRED', 'CONFIRMATION_TIMEOUT'].includes(code)) ||
    /确认.*(过期|已处理)|确认凭证.*过期/.test(compatibilityMessage)) {
    return presentation('confirmationExpired', '操作确认已过期', '重新发起原业务请求', true, taskId)
  }
  if (status === 403) {
    return presentation('forbidden', '当前账号无权执行此操作', '联系管理员或更换账号', false, taskId)
  }
  if (status === 409) {
    return presentation('conflict', '任务或确认状态已经变化', '刷新任务/确认状态', true, taskId)
  }
  if (
    status === 429 ||
    codes.some((code) => ['CAPACITY_EXCEEDED', 'RATE_LIMITED', 'QUEUE_FULL'].includes(code)) ||
    /队列已满|当前繁忙/.test(compatibilityMessage)
  ) {
    return presentation('busy', 'Agent 当前繁忙', '稍后重试，不会自动重提原任务', true, taskId)
  }
  if (status === 503) {
    return presentation('unavailable', 'AI 依赖暂不可用', '查看服务状态，恢复后重试', true, taskId)
  }
  if (codes.some((code) => ['LLM_FIRST_TOKEN_TIMEOUT', 'LLM_TOTAL_TIMEOUT', 'TIMEOUT'].includes(code))) {
    return presentation('timeout', '模型响应超时', '查询任务终态后允许手动重试', true, taskId)
  }
  if (codes.some((code) => [
    'KNOWLEDGE_UNAVAILABLE',
    'QDRANT_UNAVAILABLE',
    'EMBEDDING_UNAVAILABLE',
  ].includes(code))) {
    return presentation('knowledge', '知识库暂不可用', '取消知识库后重新提问', true, taskId)
  }
  if (!response && ((error && error.request) || ['ERR_NETWORK', 'ECONNRESET', 'ETIMEDOUT'].includes(error && error.code))) {
    return presentation('network', '连接中断，正在恢复', '自动执行有限 SSE 重连', true, taskId)
  }
  return presentation('unknown', '请求未完成', '请使用任务 ID 联系管理员定位日志', true, taskId)
}
