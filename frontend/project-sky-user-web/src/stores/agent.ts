/** 用户端Agent对话状态：把结构化事件转换为可渲染卡片，并管理确认动作。 */
import { markRaw } from 'vue'
import { defineStore } from 'pinia'
import {
  createAgentSession,
  decideAgentConfirmation,
  getAgentSession,
  streamAgentEvents,
  submitAgentTask,
  type AgentStreamEvent,
} from '@/api/agent'

const SESSION_KEY = 'sky-user-agent-session'

export interface AgentProductCard {
  id: number
  productType?: 'dish' | 'setmeal'
  name: string
  price: number
  image?: string | null
  description?: string | null
  hasFlavor?: boolean
}

export interface AgentChatBlock {
  id: string
  kind: 'message' | 'recommendations' | 'cart' | 'order' | 'confirmation' | 'citations' | 'fallback'
  role?: 'user' | 'assistant'
  text?: string
  taskId?: string
  items?: Record<string, unknown>[]
  notices?: string[]
  toolName?: string
  data?: unknown
  confirmationId?: string
  status?: 'pending' | 'approved' | 'rejected'
}

function taskId() {
  return `user-${crypto.randomUUID()}`
}

function objectData(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

export const useAgentStore = defineStore('agent', {
  state: () => ({
    sessionId: null as string | null,
    activeTaskId: null as string | null,
    blocks: [] as AgentChatBlock[],
    running: false,
    initializing: false,
    error: '',
    activeTool: '',
    lastEventId: 0,
    _abortController: null as AbortController | null,
  }),
  actions: {
    async initialize() {
      if (this.initializing || this.sessionId) return
      const saved = sessionStorage.getItem(SESSION_KEY)
      if (!saved) return
      this.initializing = true
      try {
        const detail = await getAgentSession(saved)
        this.sessionId = detail.session.sessionId
        this.blocks = detail.messages
          .filter((message) => message.role === 1 || message.role === 2)
          .map((message) => ({
            id: message.messageId,
            kind: 'message' as const,
            role: message.role === 1 ? 'user' as const : 'assistant' as const,
            text: message.content,
            taskId: message.taskId ?? undefined,
          }))
      } catch {
        sessionStorage.removeItem(SESSION_KEY)
      } finally {
        this.initializing = false
      }
    },
    async ensureSession() {
      if (this.sessionId) return this.sessionId
      const session = await createAgentSession()
      this.sessionId = session.sessionId
      sessionStorage.setItem(SESSION_KEY, session.sessionId)
      return session.sessionId
    },
    async send(message: string, clientContext: Record<string, unknown>) {
      const content = message.trim()
      if (!content || this.running) return
      this.error = ''
      this.running = true
      this.activeTool = ''
      this.lastEventId = 0
      const nextTaskId = taskId()
      this.activeTaskId = nextTaskId
      this.blocks.push({
        id: `user-${nextTaskId}`, kind: 'message', role: 'user',
        text: content, taskId: nextTaskId,
      })
      try {
        const sessionId = await this.ensureSession()
        const submitted = await submitAgentTask(nextTaskId, content, sessionId, clientContext)
        this.sessionId = submitted.sessionId
        this._abortController?.abort()
        this._abortController = markRaw(new AbortController())
        await streamAgentEvents(
          submitted.taskId,
          (event) => this.acceptEvent(event),
          this._abortController.signal,
          this.lastEventId,
        )
      } catch (error) {
        if ((error as Error).name === 'AbortError') return
        this.error = error instanceof Error ? error.message : '助手暂时不可用'
        this.blocks.push({
          id: `fallback-${nextTaskId}`, kind: 'fallback', taskId: nextTaskId,
          text: this.error,
        })
      } finally {
        this.running = false
        this.activeTool = ''
      }
    },
    acceptEvent(event: AgentStreamEvent) {
      if (event.seq_no <= this.lastEventId) return
      this.lastEventId = event.seq_no
      const data = objectData(event.data)
      const id = `${event.task_id}-${event.seq_no}`
      switch (event.event) {
        case 'task_started':
          this.running = true
          break
        case 'message_delta': {
          const content = String(data.content ?? '')
          let message = this.blocks.find((block) =>
            block.kind === 'message' && block.role === 'assistant'
            && block.taskId === event.task_id)
          if (!message) {
            message = { id: `assistant-${event.task_id}`, kind: 'message', role: 'assistant', text: '', taskId: event.task_id }
            this.blocks.push(message)
          }
          message.text = `${message.text ?? ''}${content}`
          break
        }
        case 'recommendation_cards':
          this.blocks.push({
            id, kind: 'recommendations', taskId: event.task_id,
            items: Array.isArray(data.items) ? data.items as Record<string, unknown>[] : [],
            notices: Array.isArray(data.notices) ? data.notices.map(String) : [],
          })
          break
        case 'tool_started':
          this.activeTool = String(data.tool_name ?? '')
          break
        case 'tool_completed': {
          this.activeTool = ''
          const toolName = String(data.tool_name ?? '')
          const orderTools = new Set([
            'list_my_orders', 'get_my_order_detail', 'get_order_timeline',
            'get_after_sale_status',
          ])
          if (data.status === 'success' && orderTools.has(toolName)) {
            this.blocks.push({ id, kind: 'order', taskId: event.task_id, toolName, data: data.data })
          }
          break
        }
        case 'business_state_changed':
          this.blocks.push({ id, kind: 'cart', taskId: event.task_id, data })
          window.dispatchEvent(new CustomEvent('sky:cart-changed'))
          break
        case 'confirmation_required':
          {
            const confirmationId = String(data.confirmation_id ?? '')
            const existing = this.blocks.find((block) => block.kind === 'confirmation'
              && block.confirmationId === confirmationId)
            if (existing) existing.data = data
            else this.blocks.push({ id, kind: 'confirmation', taskId: event.task_id, data,
              confirmationId, status: 'pending' })
          }
          break
        case 'operation_preview':
          this.blocks.push({
            id, kind: 'confirmation', taskId: event.task_id, data,
            confirmationId: String(data.confirmation_id ?? ''), status: 'pending',
          })
          break
        case 'clarification_required':
          this.blocks.push({
            id, kind: 'fallback', taskId: event.task_id,
            text: String(data.question ?? data.message ?? '请补充必要信息'),
          })
          break
        case 'knowledge_citations':
          this.blocks.push({ id, kind: 'citations', taskId: event.task_id, data: data.citations ?? data })
          break
        case 'task_completed':
          this.running = false
          this.activeTool = ''
          break
        case 'task_failed':
          this.running = false
          this.activeTool = ''
          this.error = String(data.error_msg ?? '助手执行失败，请稍后重试')
          this.blocks.push({ id, kind: 'fallback', taskId: event.task_id, text: this.error })
          break
      }
    },
    async decide(block: AgentChatBlock, approved: boolean) {
      if (!block.confirmationId || block.status !== 'pending') return
      await decideAgentConfirmation(block.confirmationId, approved)
      block.status = approved ? 'approved' : 'rejected'
    },
    resetConversation() {
      this._abortController?.abort()
      this._abortController = null
      this.sessionId = null
      this.activeTaskId = null
      this.blocks = []
      this.running = false
      this.error = ''
      this.activeTool = ''
      this.lastEventId = 0
      sessionStorage.removeItem(SESSION_KEY)
    },
    disconnect() {
      this._abortController?.abort()
      this._abortController = null
    },
  },
})
