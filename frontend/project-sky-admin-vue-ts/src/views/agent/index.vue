<template>
  <section class="agent-page">
    <aside class="session-panel">
      <div class="panel-heading">
        <div>
          <div class="eyebrow">SKY INTELLIGENCE</div>
          <h1>餐饮管理助手</h1>
        </div>
      </div>
      <el-button
        class="new-chat-wide"
        type="primary"
        icon="el-icon-plus"
        @click="newChat"
        >新建会话</el-button
      >
      <div class="session-label">最近会话</div>
      <div v-loading="loadingSessions" class="session-list">
        <div
          v-for="item in sortedSessions"
          :key="item.sessionId"
          class="session-item"
          :class="{ active: item.sessionId === sessionId }"
          @click="openSession(item.sessionId)"
          @keydown.enter="openSession(item.sessionId)"
          role="button"
          tabindex="0"
        >
          <span class="session-icon"><i class="el-icon-chat-dot-round" /></span>
          <span class="session-copy">
            <strong>{{ item.title || '新会话' }}</strong>
            <small>{{ formatDate(item.updateTime || item.createTime) }}</small>
          </span>
          <i
            class="el-icon-more"
            :aria-label="`管理会话：${item.title || '新会话'}`"
            @click.stop="toggleSessionMenu(item.sessionId)"
          />
          <div
            v-if="sessionMenuId === item.sessionId"
            class="session-menu"
            @click.stop
          >
            <button @click="archiveSession(item.sessionId)">
              <i class="el-icon-folder" />归档
            </button>
            <button @click="deleteSession(item.sessionId)">
              <i class="el-icon-delete" />删除
            </button>
            <button @click="togglePin(item)">
              <i :class="item.pinned ? 'el-icon-top' : 'el-icon-bottom'" />
              {{ item.pinned ? '取消置顶' : '置顶' }}
            </button>
          </div>
        </div>
        <div v-if="!loadingSessions && !sessions.length" class="empty-sessions">
          还没有会话记录
        </div>
      </div>
      <div class="panel-footer" role="status" aria-live="polite" aria-atomic="true">
        <span class="status-dot" :class="`status-${agentHealth.status}`" />
        {{ agentHealthText }}
      </div>
    </aside>

    <main class="chat-panel">
      <header class="chat-header">
        <div>
          <span class="header-mark"><i class="el-icon-magic-stick" /></span>
          <span class="header-title">{{ currentTitle }}</span>
          <el-tag size="mini" :type="agentHealthTagType">{{ agentHealthText }}</el-tag>
        </div>
        <div class="header-actions">
          <el-select
            v-model="kbId"
            size="small"
            class="kb-select"
            clearable
            placeholder="不使用知识库"
            :disabled="kbLocked || knowledgeUnavailable"
          >
            <el-option
              v-for="item in knowledgeBases"
              :key="item.kbId"
              :label="item.name"
              :value="item.kbId"
            />
          </el-select>
          <span v-if="knowledgeUnavailable" class="health-hint">知识库相关操作暂不可用</span>
          <el-select
            v-model="model"
            size="small"
            class="model-select"
            placeholder="选择模型"
          >
            <el-option label="默认模型" value="" />
            <el-option label="DeepSeek Chat" value="deepseek-chat" />
          </el-select>
          <el-button
            type="text"
            icon="el-icon-folder"
            @click="showArchived = true; loadArchivedSessions()"
          >
            已归档
          </el-button>
        </div>
      </header>

      <aside v-if="showArchived" class="archive-drawer" aria-label="已归档会话">
        <div class="archive-heading">
          <div>
            <strong>已归档</strong>
            <small>归档的对话会保留在这里</small>
          </div>
          <button aria-label="关闭已归档" @click="showArchived = false">
            <i class="el-icon-close" />
          </button>
        </div>
        <div v-loading="loadingArchived" class="archive-list">
          <div v-if="!loadingArchived && !archivedSessions.length" class="empty-sessions">
            暂无已归档对话
          </div>
          <div v-for="item in archivedSessions" :key="item.sessionId" class="archive-item">
            <button class="archive-open" @click="restoreSession(item.sessionId)">
              <strong>{{ item.title || '新会话' }}</strong>
              <small>{{ formatDate(item.updateTime || item.createTime) }}</small>
            </button>
            <button class="archive-delete" aria-label="删除已归档会话" @click="deleteSession(item.sessionId, true)">
              <i class="el-icon-delete" />
            </button>
          </div>
        </div>
      </aside>

      <div ref="messages" class="messages" role="log" aria-live="polite">
        <div v-if="!messages.length" class="welcome-state">
          <div class="welcome-icon"><i class="el-icon-magic-stick" /></div>
          <h2>你好，我是餐饮管理助手</h2>
          <p>我可以帮你分析经营数据、查询订单，或协助处理后台工作。</p>
          <div class="suggestions">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              @click="ask(suggestion)"
            >
              {{ suggestion }}
              <i class="el-icon-arrow-right" />
            </button>
          </div>
        </div>
        <article
          v-for="(message, index) in messages"
          :key="index"
          class="message-row"
          :class="message.role === 'user' ? 'user-row' : 'assistant-row'"
        >
          <div v-if="message.role !== 'user'" class="avatar assistant-avatar">
            <i class="el-icon-magic-stick" />
          </div>
          <div class="message-content">
            <div class="message-name">
              {{ message.role === 'user' ? '我' : '餐饮管理助手' }}
            </div>
            <div
              class="bubble"
              :class="
                message.role === 'user' ? 'user-bubble' : 'assistant-bubble'
              "
            >
              {{ message.content }}
              <span v-if="message.streaming" class="typing-caret" />
            </div>
            <section
              v-if="message.confirmation"
              class="tool-confirmation"
              :class="confirmationStateClass(message)"
              role="group"
              aria-label="AI 操作确认"
            >
              <!-- 已结束后不再出现「需要你的确认」标题与超时提示，只保留状态 -->
              <div v-if="!isConfirmationSettled(message)" class="confirmation-heading">
                <i class="el-icon-warning-outline" aria-hidden="true" />
                <strong>需要你的确认</strong>
              </div>
              <div v-else class="confirmation-heading">
                <i :class="confirmationStateIcon(message)" aria-hidden="true" />
                <strong>{{ confirmationStateLabel(message) }}</strong>
              </div>
              <p class="confirmation-summary">{{ message.confirmation.summary }}</p>
              <small v-if="!isConfirmationSettled(message)">请在 5 分钟内确认；确认后将立即执行。</small>
              <div v-if="!isConfirmationSettled(message)" class="confirmation-actions">
                <el-button
                  size="small"
                  :disabled="message.confirmation.processing || message.confirmation.retryable === false"
                  @click="decideTool(message, false)"
                >拒绝</el-button>
                <el-button
                  type="danger"
                  size="small"
                  :loading="message.confirmation.processing"
                  :disabled="message.confirmation.retryable === false"
                  @click="decideTool(message, true)"
                >确认执行</el-button>
              </div>
              <!-- 失败且可重试时保留明确的恢复入口 -->
              <div
                v-else-if="isConfirmationRetryable(message)"
                class="confirmation-actions"
              >
                <el-button
                  size="small"
                  :loading="message.confirmation.processing"
                  @click="retryTool(message)"
                >重试</el-button>
              </div>
              <div v-if="message.confirmation.decision" class="confirmation-result" role="status">
                {{ message.confirmation.decision }}
              </div>
            </section>
            <section
              v-if="message.failure"
              class="agent-error-recovery"
              role="alert"
            >
              <strong>{{ message.failure.message }}</strong>
              <p>{{ message.failure.action }}</p>
              <small v-if="message.failure.taskId">任务 ID：{{ message.failure.taskId }}</small>
              <el-button
                v-if="message.failure.recovery !== 'none'"
                size="small"
                @click="recoverAgentFailure(message)"
              >{{ recoveryButtonText(message.failure.recovery) }}</el-button>
            </section>
            <div
              v-if="message.citations && message.citations.length"
              class="citations"
            >
              <div class="citations-title">参考来源</div>
              <div class="citation-list">
                <span
                  v-for="(source, sourceIndex) in uniqueCitations(message.citations)"
                  :key="source.chunkId"
                  class="citation-marker"
                  tabindex="0"
                  :data-tooltip="citationTooltip(source)"
                >
                  {{ sourceIndex + 1 }}
                </span>
              </div>
            </div>
          </div>
          <div v-if="message.role === 'user'" class="avatar user-avatar">
            我
          </div>
        </article>
        <div v-if="running" class="running-line" role="status" aria-live="polite">
          <i class="el-icon-loading" />
          {{ streamStatus }}
        </div>
      </div>

      <footer class="composer-wrap">
        <div class="composer">
          <textarea
            v-model="draft"
            rows="1"
            placeholder="给餐饮管理助手发送消息..."
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer-toolbar">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              type="primary"
              circle
              :disabled="cancelling || (!draft.trim() && !running) || (!running && (agentHealth.status === 'offline' || (knowledgeUnavailable && kbId)))"
              :icon="running ? 'el-icon-close' : 'el-icon-top'"
              :aria-label="running ? '取消生成' : '发送消息'"
              @click="running ? stopGeneration() : send()"
            />
          </div>
        </div>
        <div class="disclaimer">AI 可能会产生错误，请核验重要信息</div>
      </footer>
    </main>
  </section>
</template>

<script lang="ts">
import Vue from 'vue'
import {
  listAgentSessions,
  getAgentSession,
  updateAgentSession,
  submitAgentTask,
  getAgentHealth,
  getAgentTask,
  cancelAgentTask,
  confirmAgentTool,
  rejectAgentTool,
} from '@/api/agent'
import { listKnowledgeBases } from '@/api/knowledge'
import { UserModule } from '@/store/modules/user'
import {
  consumeAgentEventStream,
  type AgentStreamDependencies,
  type AgentStreamEnvelope,
} from '@/utils/agentSse'
import {
  normalizeAgentError,
  type AgentErrorPresentation,
} from '@/utils/agentErrors'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  citations?: any[]
  confirmation?: any
  failure?: AgentErrorPresentation
}

type AgentHealthStatus = 'online' | 'degraded' | 'offline' | 'unknown'

interface AgentHealth {
  status: AgentHealthStatus
  errorType: string | null
}
export default Vue.extend({
  name: 'AgentPage',
  data() {
    return {
      sessions: [] as any[],
      archivedSessions: [] as any[],
      pinnedSessionIds: [] as string[],
      sessionMenuId: '',
      showArchived: false,
      sessionId: '',
      model: '',
      kbId: '',
      kbLocked: false,
      knowledgeBases: [] as any[],
      draft: '',
      messages: [] as ChatMessage[],
      running: false,
      cancelling: false,
      cancelRequested: false,
      currentTaskId: '',
      streamController: null as AbortController | null,
      streamStatus: '正在思考...',
      pageDestroyed: false,
      agentHealth: { status: 'unknown', errorType: null } as AgentHealth,
      healthRefreshTimer: null as number | null,
      loadingSessions: false,
      loadingArchived: false,
      suggestions: [
        '今天的订单情况怎么样？',
        '帮我分析一下营业数据',
        '如何设置一个新的套餐？',
      ],
    }
  },
  computed: {
    currentTitle(): string {
      const current: any = (this as any).sessions.find(
        (item: any) => item.sessionId === (this as any).sessionId
      )
      return current ? current.title || '新会话' : '新会话'
    },
    sortedSessions(): any[] {
      return [...(this as any).sessions].sort((a: any, b: any) => {
        if (Boolean(a.pinned) !== Boolean(b.pinned)) return a.pinned ? -1 : 1
        return String(b.updateTime || b.createTime || '').localeCompare(
          String(a.updateTime || a.createTime || '')
        )
      })
    },
    agentHealthText(): string {
      const status = (this as any).agentHealth.status as AgentHealthStatus
      return {
        online: '服务正常',
        degraded: '部分能力不可用',
        offline: '服务不可用',
        unknown: '状态未知',
      }[status]
    },
    agentHealthTagType(): string {
      const status = (this as any).agentHealth.status as AgentHealthStatus
      return { online: 'success', degraded: 'warning', offline: 'danger', unknown: 'info' }[status]
    },
    knowledgeUnavailable(): boolean {
      const errorType = (this as any).agentHealth.errorType
      return errorType === 'QDRANT_UNAVAILABLE' || errorType === 'EMBEDDING_UNAVAILABLE'
    },
  },
  mounted() {
    try {
      this.pinnedSessionIds = JSON.parse(
        window.localStorage.getItem('sky-agent-pinned-sessions') || '[]'
      )
    } catch (_) {
      this.pinnedSessionIds = []
    }
    this.loadSessions()
    this.loadKnowledgeBases()
    this.refreshAgentHealth()
    this.healthRefreshTimer = window.setInterval(() => this.refreshAgentHealth(), 60000)
  },
  beforeDestroy() {
    // 页面销毁只释放浏览器端 SSE，不把本地清理误当作后台任务取消。
    this.pageDestroyed = true
    if (this.streamController) {
      this.streamController.abort()
    }
    if (this.healthRefreshTimer !== null) {
      window.clearInterval(this.healthRefreshTimer)
      this.healthRefreshTimer = null
    }
  },
  methods: {
    async refreshAgentHealth(): Promise<boolean> {
      try {
        const response: any = await getAgentHealth()
        const payload = response.data?.data || {}
        const statuses: AgentHealthStatus[] = ['online', 'degraded', 'offline']
        const status = statuses.includes(payload.status) ? payload.status : 'unknown'
        if (!this.pageDestroyed) {
          this.agentHealth = {
            status,
            errorType: typeof payload.errorType === 'string' ? payload.errorType : null,
          }
        }
        return true
      } catch (_) {
        // 单次健康查询失败只能显示未知状态，不能影响会话和正在执行的任务。
        if (!this.pageDestroyed) {
          this.agentHealth = { status: 'unknown', errorType: null }
        }
        return false
      }
    },
    async loadSessions() {
      this.loadingSessions = true
      try {
        const res: any = await listAgentSessions({
          page: 1,
          pageSize: 50,
          status: 1,
        })
        this.sessions = (res.data?.data?.records || res.data?.data?.list || []).map(
          (item: any) => ({
            ...item,
            pinned: this.pinnedSessionIds.indexOf(item.sessionId) !== -1,
          })
        )
      } finally {
        this.loadingSessions = false
      }
    },
    async loadArchivedSessions() {
      this.loadingArchived = true
      try {
        const res: any = await listAgentSessions({ page: 1, pageSize: 50, status: 2 })
        this.archivedSessions = res.data?.data?.records || res.data?.data?.list || []
      } finally {
        this.loadingArchived = false
      }
    },
    async loadKnowledgeBases() {
      const res: any = await listKnowledgeBases()
      this.knowledgeBases = (res.data?.data || []).filter(
        (item: any) => item.status === 1
      )
    },
    async openSession(id: string) {
      this.sessionId = id
      const res: any = await getAgentSession(id)
      const detail = res.data?.data || {}
      this.kbId = detail.session?.kbId || ''
      this.kbLocked = Boolean(detail.session?.kbId)
      const history = detail.messages || []
      this.messages = history.map((item: any) => ({
        role: item.role === 1 ? 'user' : 'assistant',
        content: item.content || '',
        citations: item.citations || [],
      }))
    },
    newChat() {
      this.sessionId = ''
      this.kbId = ''
      this.kbLocked = false
      this.messages = []
      this.draft = ''
    },
    async archiveSession(id: string) {
      this.sessionMenuId = ''
      await updateAgentSession({ sessionId: id, status: 2 })
      this.$message.success('会话已归档')
      await this.loadSessions()
      if (id === this.sessionId) {
        this.newChat()
      }
    },
    toggleSessionMenu(id: string) {
      this.sessionMenuId = this.sessionMenuId === id ? '' : id
    },
    async deleteSession(id: string, archived = false) {
      if (!window.confirm('确定删除这个会话吗？删除后将无法在列表中恢复。')) return
      await updateAgentSession({ sessionId: id, status: 3 })
      this.sessionMenuId = ''
      this.$message.success('会话已删除')
      if (archived) await this.loadArchivedSessions()
      else await this.loadSessions()
      if (id === this.sessionId) this.newChat()
    },
    async restoreSession(id: string) {
      await updateAgentSession({ sessionId: id, status: 1 })
      this.showArchived = false
      await this.loadSessions()
      await this.openSession(id)
      this.$message.success('会话已恢复')
    },
    togglePin(item: any) {
      item.pinned = !item.pinned
      const ids = this.pinnedSessionIds.filter((id) => id !== item.sessionId)
      if (item.pinned) ids.push(item.sessionId)
      this.pinnedSessionIds = ids
      window.localStorage.setItem('sky-agent-pinned-sessions', JSON.stringify(ids))
      this.sessionMenuId = ''
    },
    citationTooltip(source: any) {
      const page = source.pageNo ? ` · 第${source.pageNo}页` : ''
      const score = typeof source.score === 'number' ? ` · 匹配度 ${(source.score * 100).toFixed(1)}%` : ''
      return `${source.fileName || '来源文档'}${page}${score}`
    },
    ask(text: string) {
      this.draft = text
      this.send()
    },
    formatDate(value: any) {
      if (!value) return ''
      const text = String(value)
      // 后端和数据库统一使用 UTC，LocalDateTime JSON 不自带 Z。
      const normalized = /(?:Z|[+-]\d\d:\d\d)$/.test(text) ? text : `${text}Z`
      const date = new Date(normalized)
      if (Number.isNaN(date.getTime())) return text.replace('T', ' ').slice(5, 16)
      const pad = (part: number) => String(part).padStart(2, '0')
      return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    async send() {
      const query = this.draft.trim()
      if (!query || this.running) {
        return
      }
      if (this.agentHealth && this.agentHealth.status === 'offline') {
        this.$message.error('Agent 服务不可用，请恢复服务后重试')
        return
      }
      if (this.knowledgeUnavailable && this.kbId) {
        this.$message.error('知识库服务暂不可用，请等待恢复或新建普通会话')
        return
      }
      this.draft = ''
      this.messages.push({ role: 'user', content: query })
      const assistant: ChatMessage = {
        role: 'assistant',
        content: '',
        streaming: true,
        citations: [],
        confirmation: null,
      }
      this.messages.push(assistant)
      this.running = true
      this.cancelRequested = false
      this.streamStatus = '正在思考...'
      this.$nextTick(this.scrollToBottom)
      try {
        const taskId =
          window.crypto && window.crypto.randomUUID
            ? window.crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(16).slice(2)}`
        this.currentTaskId = taskId
        const res: any = await submitAgentTask({
          taskId,
          query,
          sessionId: this.sessionId || null,
          kbId: this.kbId || null,
          model: this.model || null,
        })
        if (this.pageDestroyed) {
          return
        }
        const payload = res.data?.data || {}
        this.currentTaskId = payload.taskId || taskId
        this.sessionId = payload.sessionId || this.sessionId
        this.kbLocked = Boolean(this.kbId)
        if (this.cancelRequested) {
          try {
            await cancelAgentTask(this.currentTaskId)
          } catch (error) {
            this.cancelRequested = false
            throw error
          }
          assistant.content = assistant.content || '已停止生成。'
          return
        }
        this.streamController = new AbortController()
        await this.consumeEvents(this.currentTaskId, assistant, this.streamController.signal)
        await this.loadSessions()
      } catch (error) {
        if (this.cancelRequested || (error as any)?.name === 'AbortError') {
          assistant.content = assistant.content
            ? `${assistant.content}\n\n（已停止生成）`
            : '已停止生成。'
        } else {
          this.applyStreamFailure(
            assistant,
            error,
            this.currentTaskId,
            this.streamController ? 'streamStopped' : 'submit'
          )
        }
      } finally {
        assistant.streaming = false
        this.running = false
        this.cancelling = false
        this.currentTaskId = ''
        this.streamController = null
      }
    },
    applyStreamFailure(
      assistant: ChatMessage,
      error?: any,
      taskId?: string,
      phase: 'submit' | 'stream' | 'streamStopped' = 'stream'
    ) {
      const failure = normalizeAgentError(error || { code: 'ERR_NETWORK', request: {} }, taskId, phase)
      assistant.failure = failure
      this.$message.error(failure.message)
    },
    recoveryButtonText(recovery: string) {
      return {
        refreshTask: '刷新当前状态',
        newPlainSession: '新建普通会话并保留原问题',
        refreshHealth: '刷新服务状态',
      }[recovery] || ''
    },
    async recoverAgentFailure(message: ChatMessage) {
      const failure = message.failure
      if (!failure || failure.recovery === 'none') return
      try {
        if (failure.recovery === 'refreshTask') {
          if (!failure.taskId) throw new Error('Agent task id is required')
          const status = await this.getAgentTaskStatus(failure.taskId)
          if ([2, 3, 4].includes(status)) {
            if (this.sessionId) await this.openSession(this.sessionId)
          } else if (status === 0 || status === 1) {
            this.streamStatus = '任务仍在后台执行，可稍后查询。'
          } else {
            throw new Error('Agent task status is invalid')
          }
        } else if (failure.recovery === 'newPlainSession') {
          const index = this.messages.indexOf(message)
          const previous = index > 0 ? this.messages[index - 1] : null
          const originalQuestion = previous && previous.role === 'user' ? previous.content : this.draft
          this.newChat()
          this.draft = originalQuestion || ''
        } else if (!(await this.refreshAgentHealth())) {
          throw new Error('Agent health refresh failed')
        }
        this.$message.success('已刷新当前状态')
      } catch (_) {
        this.$message.error('恢复操作未完成，请稍后重试')
      }
    },
    async stopGeneration() {
      if (!this.running || this.cancelling) {
        return
      }
      this.cancelRequested = true
      this.cancelling = true
      try {
        if (this.currentTaskId) {
          await cancelAgentTask(this.currentTaskId)
          if (this.streamController) {
            this.streamController.abort()
          }
        }
      } catch (_) {
        this.cancelRequested = false
        this.$message.error('停止生成失败，请重试')
      } finally {
        this.cancelling = false
      }
    },
    applyAgentEvent(event: AgentStreamEnvelope, assistant: ChatMessage) {
      if (event.event === 'task_error') {
        const body = event.data && typeof event.data === 'object' ? event.data : {}
        assistant.failure = normalizeAgentError({ response: { data: body } }, event.taskId, 'stream')
        if (assistant.confirmation) {
          assistant.confirmation.processing = false
          assistant.confirmation.decided = true
          assistant.confirmation.failed = true
          assistant.confirmation.decision = '执行失败，请查看错误提示。'
        }
        this.$nextTick(this.scrollToBottom)
        return
      }
      const body: any = event.data && typeof event.data === 'object' ? event.data : {}
      this.streamStatus = '正在思考...'
      const text =
        body.content ||
        body.delta ||
        body.token ||
        body.text ||
        (event.event === 'task_end' ? body.result : '') ||
        (typeof event.data === 'string' ? event.data : '')
      if (text && !(event.event === 'task_end' && assistant.content)) {
        assistant.content += text
      }
      if (event.event === 'task_end') {
        assistant.citations = (body.citations || []).map(this.normalizeCitation)
        if (assistant.confirmation) {
          assistant.confirmation.processing = false
          assistant.confirmation.decided = true
          assistant.confirmation.decision = '已执行完成。'
        }
      }
      if (event.event === 'tool_confirmation_required') {
        assistant.confirmation = {
          id: body.confirmation_id,
          summary: body.summary || '执行业务状态修改',
          expiresAt: body.expires_at,
          processing: false,
          decided: false,
          decision: '',
          retryable: true,
          taskId: event.taskId,
        }
      }
      if (event.event === 'task_cancelled') {
        assistant.content = assistant.content
          ? `${assistant.content}\n\n（已停止生成）`
          : '已停止生成。'
        if (assistant.confirmation) {
          assistant.confirmation.processing = false
          assistant.confirmation.decided = true
          assistant.confirmation.cancelled = true
          assistant.confirmation.decision = '已取消执行。'
        }
      }
      this.$nextTick(this.scrollToBottom)
    },
    async getAgentTaskStatus(taskId: string) {
      const response: any = await getAgentTask(taskId)
      return Number(response.data?.data?.status)
    },
    async consumeEvents(
      taskId: string,
      assistant: ChatMessage,
      signal: AbortSignal,
      dependencies: AgentStreamDependencies = {}
    ) {
      if (!taskId) {
        return
      }
      let receivedTerminalEvent = false
      const result = await consumeAgentEventStream(
        taskId,
        UserModule.token,
        signal,
        {
          onEvent: (event) => {
            this.applyAgentEvent(event, assistant)
            receivedTerminalEvent = [
              'task_end',
              'task_error',
              'task_cancelled',
            ].includes(event.event) || receivedTerminalEvent
          },
          getTaskStatus: (id) => this.getAgentTaskStatus(id),
          onReconnect: (attempt, maxAttempts) => {
            this.streamStatus = `连接中断，正在恢复（${attempt}/${maxAttempts}）...`
          },
          onBackground: () => {
            const message = '任务仍在后台执行，可重新进入会话查看。'
            if (!assistant.content.includes(message)) {
              assistant.content = assistant.content
                ? `${assistant.content}\n\n${message}`
                : message
            }
            this.streamStatus = message
            this.$nextTick(this.scrollToBottom)
          },
        },
        dependencies
      )
      if (result.terminal && !receivedTerminalEvent && this.sessionId) {
        await this.openSession(this.sessionId)
      }
      return result
    },
    async retryTool(message: ChatMessage) {
      const confirmation: any = message.confirmation
      if (!confirmation || confirmation.processing) return
      confirmation.processing = true
      try {
        await confirmAgentTool(confirmation.id)
        // 重试成功 → 回到「进行中」，等 task_end / task_error 决定最终状态
        confirmation.failed = false
        confirmation.cancelled = false
        confirmation.decided = true
        confirmation.decision = '已确认，正在执行…'
        this.$message.success('已重新提交执行')
      } catch (error) {
        const failure = normalizeAgentError(error, confirmation.taskId)
        confirmation.failed = true
        confirmation.decided = true
        confirmation.retryable = failure.retryable
        confirmation.decision = `${failure.message}，${failure.action}`
        this.$message.error(confirmation.decision)
      } finally {
        confirmation.processing = false
        this.$nextTick(this.scrollToBottom)
      }
    },
    async decideTool(message: ChatMessage, approved: boolean) {
      const confirmation = message.confirmation
      if (!confirmation || confirmation.processing || confirmation.decided) return
      confirmation.processing = true
      try {
        if (approved) await confirmAgentTool(confirmation.id)
        else await rejectAgentTool(confirmation.id)
        confirmation.decided = true
        confirmation.decision = approved ? '已确认，正在执行…' : '已拒绝，本次操作不会执行。'
        this.$message.success(approved ? '操作已确认' : '操作已拒绝')
      } catch (error) {
        const failure = normalizeAgentError(error, confirmation.taskId)
        message.failure = failure
        confirmation.retryable = failure.retryable
        confirmation.failed = true
        confirmation.decision = `${failure.message}，${failure.action}`
        if (!failure.retryable || failure.kind === 'conflict' || failure.kind === 'confirmationExpired') {
          confirmation.decided = true
        }
        this.$message.error(confirmation.decision)
      } finally {
        confirmation.processing = false
      }
    },
    normalizeCitation(source: any) {
      return {
        chunkId: source.chunkId || source.chunk_id,
        documentId: source.documentId || source.document_id,
        documentVersion: source.documentVersion || source.document_version,
        fileName: source.fileName || source.file_name,
        pageNo: source.pageNo || source.page_no,
        score: source.score,
        quote: source.quote,
      }
    },
    uniqueCitations(citations: any[]) {
      const seen = new Set<string>()
      return (citations || []).filter((source: any) => {
        const documentId = source.documentId || source.document_id
        const documentVersion =
          source.documentVersion || source.document_version || ''
        const chunkId = source.chunkId || source.chunk_id
        const key = documentId
          ? `${documentId}:${documentVersion}`
          : chunkId || `${source.fileName || source.file_name}:${source.pageNo || source.page_no || ''}`
        if (seen.has(key)) {
          return false
        }
        seen.add(key)
        return true
      })
    },
    // AI 确认卡状态：确认完成后不再显示待确认标题/超时/操作按钮，
    // 失败与取消仍保留明确状态（失败可重试时给出恢复入口）。
    isConfirmationSettled(message: ChatMessage) {
      const confirmation: any = message.confirmation
      if (!confirmation) return false
      // 用户只点过「确认执行」、还没拿到终态事件 → 视为未结束：显示「正在执行」
      return !this.isConfirmationInFlight(message)
    },
    // 已确认正在执行：decided=true 且尚无终态标记，decision 仍是进行中文案
    isConfirmationInFlight(message: ChatMessage) {
      const confirmation: any = message.confirmation || {}
      return Boolean(
        confirmation.decided &&
          !confirmation.failed &&
          !confirmation.cancelled &&
          confirmation.decision === '已确认，正在执行…'
      )
    },
    confirmationStateKey(message: ChatMessage) {
      const confirmation: any = message.confirmation || {}
      if (confirmation.failed === true) return 'failed'
      if (confirmation.cancelled === true) return 'cancelled'
      const decision = String(confirmation.decision || '')
      if (/已取消|已拒绝/.test(decision)) return 'cancelled'
      if (/失败|未生效|错误/.test(decision)) return 'failed'
      return 'done'
    },
    confirmationStateClass(message: ChatMessage) {
      if (!this.isConfirmationSettled(message)) return 'confirmation-pending'
      return `confirmation-${this.confirmationStateKey(message)}`
    },
    confirmationStateLabel(message: ChatMessage) {
      const key = this.confirmationStateKey(message)
      if (key === 'failed') return '执行失败'
      if (key === 'cancelled') return '已取消'
      return '已执行完成'
    },
    confirmationStateIcon(message: ChatMessage) {
      const key = this.confirmationStateKey(message)
      if (key === 'failed') return 'el-icon-circle-close'
      if (key === 'cancelled') return 'el-icon-remove-outline'
      return 'el-icon-circle-check'
    },
    isConfirmationRetryable(message: ChatMessage) {
      const confirmation: any = message.confirmation
      return !!(
        confirmation &&
        confirmation.decided &&
        this.confirmationStateKey(message) === 'failed' &&
        confirmation.retryable !== false
      )
    },
    async openCitation(source: any) {
      const base = process.env.VUE_APP_BASE_API || '/api'
      const response = await fetch(
        `${base}/agent/documents/${source.documentId}/content`,
        {
          headers: { token: UserModule.token },
        }
      )
      if (!response.ok) {
        return this.$message.error('无法打开来源文档')
      }
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    },
    scrollToBottom() {
      const el: any = this.$refs.messages
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    },
  },
})
</script>

<style lang="scss" scoped>
.tool-confirmation {
  margin-top: 8px;
  padding: 9px 11px;
  border: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  border-radius: 8px;
  background: var(--surface-raised, rgba(35, 43, 74, 0.92));
  color: var(--text-2, #c2cde4);
  backdrop-filter: blur(8px);
}
/* 待确认：琥珀色提示 */
.tool-confirmation.confirmation-pending {
  border-color: rgba(251, 191, 36, 0.42);
}
.confirmation-pending .confirmation-heading {
  color: #fbbf24;
}
/* 已执行完成：绿色，只保留摘要 + 状态 */
.tool-confirmation.confirmation-done {
  border-color: rgba(52, 211, 153, 0.4);
}
.confirmation-done .confirmation-heading {
  color: #34d399;
}
.confirmation-done .confirmation-summary {
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.26);
  border-radius: 6px;
  padding: 8px 10px;
}
/* 失败：红色状态，保留重试入口 */
.tool-confirmation.confirmation-failed {
  border-color: rgba(255, 107, 107, 0.42);
}
.confirmation-failed .confirmation-heading {
  color: #ff8f8f;
}
/* 已取消：中性灰，不喧哗 */
.tool-confirmation.confirmation-cancelled {
  border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
}
.confirmation-cancelled .confirmation-heading {
  color: var(--text-3, #8e9cb8);
}

.confirmation-heading {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
}

.tool-confirmation p {
  margin: 6px 0 2px;
  line-height: 1.45;
  font-size: 13px;
  color: var(--text-2, #c2cde4);
}
.tool-confirmation.confirmation-done p.confirmation-summary {
  margin: 2px 0;
  color: var(--text-1, #eef3ff);
}

.tool-confirmation small {
  color: var(--text-4, #6c7893);
  font-size: 11px;
  line-height: 1.3;
}

.confirmation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 7px;
}

.confirmation-actions ::v-deep .el-button {
  min-width: 72px;
  min-height: 34px;
  padding: 7px 12px;
}

.confirmation-result {
  margin-top: 5px;
  color: var(--text-3, #8e9cb8);
  font-size: 12px;
  line-height: 1.3;
}

.agent-page {
  height: calc(100vh - 84px);
  min-height: 620px;
  display: flex;
  margin: 0;
  background: transparent;
  color: var(--text-1);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    sans-serif;
}
.session-panel {
  width: 276px;
  flex: 0 0 276px;
  display: flex;
  flex-direction: column;
  background: var(--surface-card);
  border-right: 1px solid var(--border);
  padding: 26px 18px 18px;
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 8px 20px;
}
.eyebrow {
  color: var(--text-3);
  font-size: 10px;
  letter-spacing: 1.4px;
  font-weight: 700;
}
.panel-heading h1 {
  margin: 7px 0 0;
  font-size: 23px;
  color: var(--text-1);
}
.new-chat-wide {
  width: 100%;
  background: rgba(255,255,255,0.08);
  border: 0;
  color: var(--text-2);
  margin-bottom: 24px;
}
.session-label {
  color: var(--text-3);
  font-size: 12px;
  margin: 0 8px 10px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  /* 滚动条：淡蓝色滑块，轨道与背景同色（不留灰底） */
  scrollbar-width: thin;                              /* Firefox */
  scrollbar-color: var(--action-light, #60a5fa) transparent;

  &::-webkit-scrollbar {
    width: 8px;
  }

  /* 轨道与背景一致，避免出现一条灰带 */
  &::-webkit-scrollbar-track,
  &::-webkit-scrollbar-track-piece,
  &::-webkit-scrollbar-corner {
    background: transparent;
  }

  /* 滑块：淡蓝，hover 更亮 */
  &::-webkit-scrollbar-thumb {
    background: var(--action-light, #60a5fa);
    border-radius: 999px;
    border: 2px solid transparent;
    background-clip: content-box;

    &:hover {
      background: var(--action, #3b82f6);
      background-clip: content-box;
    }
  }

  /* 上下箭头与背景同色 */
  &::-webkit-scrollbar-button {
    display: none;
    height: 0;
  }
}
.session-item {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 11px 9px;
  border-radius: 7px;
  text-align: left;
  cursor: pointer;
  color: var(--text-3);
}
.session-item:hover,
.session-item.active {
  background: var(--surface-hover);
  color: var(--text-1);
}
.session-icon {
  width: 28px;
  color: var(--text-4);
}
.session-copy {
  flex: 1;
  overflow: hidden;
}
.session-copy strong,
.session-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-copy strong {
  font-size: 13px;
  font-weight: 500;
}
.session-copy small {
  color: var(--text-4);
  font-size: 11px;
  margin-top: 4px;
}
.session-item > i {
  flex: 0 0 28px;
  padding: 8px 0;
  opacity: 0;
  text-align: center;
}
.session-item:hover > i {
  opacity: 1;
}
.empty-sessions {
  padding: 28px 8px;
  text-align: center;
  color: var(--text-4);
  font-size: 12px;
}
.session-menu {
  position: absolute;
  top: 42px;
  right: 8px;
  z-index: 5;
  width: 112px;
  padding: 5px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-card);
  box-shadow: 0 8px 20px rgba(28, 40, 58, 0.12);
}
.session-menu button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 34px;
  gap: 8px;
  padding: 0 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}
.session-menu button:hover {
  background: var(--surface-hover);
  color: var(--accent);
}
.panel-footer {
  // 与面板底色一致，不额外画分隔线（避免底部出现一条异色边）
  background: var(--surface-card);
  border-top: 1px solid var(--border);
  padding: 16px 8px 4px;
  color: var(--text-3);
  font-size: 12px;
}
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: var(--success);
}

.agent-error-recovery {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--surface-raised);
  color: var(--text-2);
}

.agent-error-recovery p {
  margin: 6px 0;
  line-height: 1.5;
}

.agent-error-recovery small {
  color: var(--text-3);
}
.status-dot.status-degraded { background: var(--warn); }
.status-dot.status-offline { background: #d9534f; }
.status-dot.status-unknown { background: #9aa3b0; }
.health-hint {
  color: var(--warn);
  font-size: 12px;
}
.chat-panel {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.archive-drawer {
  position: absolute;
  top: 66px;
  right: 0;
  bottom: 0;
  z-index: 10;
  width: min(360px, 92vw);
  padding: 22px;
  border-left: 1px solid var(--border);
  background: var(--surface-card);
  box-shadow: -8px 0 24px rgba(28, 40, 58, 0.08);
}
.archive-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.archive-heading strong,
.archive-heading small {
  display: block;
}
.archive-heading strong {
  color: var(--text-1);
  font-size: 17px;
}
.archive-heading small {
  margin-top: 5px;
  color: var(--text-4);
  font-size: 11px;
}
.archive-heading button,
.archive-delete {
  border: 0;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}
.archive-list {
  max-height: calc(100% - 74px);
  overflow-y: auto;
}
.archive-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.archive-open {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  text-align: left;
}
.archive-open strong,
.archive-open small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.archive-open strong { font-size: 13px; font-weight: 500; }
.archive-open small { margin-top: 5px; color: var(--text-4); font-size: 11px; }
.archive-open:hover { color: var(--accent); }
.archive-delete { min-width: 32px; min-height: 32px; }
.archive-delete:hover { color: var(--danger); }
.chat-header {
  height: 66px;
  flex: 0 0 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 38px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border);
}
.header-mark {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--accent);
}
.header-title {
  margin-right: 10px;
  font-size: 15px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}
.model-select {
  width: 142px;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 42px clamp(28px, 8vw, 130px) 24px;
}
.welcome-state {
  max-width: 680px;
  margin: 10vh auto 0;
  text-align: center;
}
.welcome-icon {
  display: inline-flex;
  width: 56px;
  height: 56px;
  align-items: center;
  justify-content: center;
  border-radius: 17px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 25px;
}
.welcome-state h2 {
  margin: 18px 0 8px;
  color: var(--text-1);
  font-size: 25px;
  font-weight: 600;
}
.welcome-state p {
  margin: 0 0 27px;
  color: var(--text-3);
  font-size: 14px;
}
.suggestions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}
.suggestions button {
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--surface-card);
  color: var(--text-3);
  padding: 11px 13px;
  cursor: pointer;
  font-size: 12px;
}
.suggestions button:hover {
  border-color: #a7c6ef;
  color: var(--accent);
}
.suggestions i {
  margin-left: 9px;
}
.message-row {
  display: flex;
  gap: 12px;
  max-width: 760px;
  margin: 0 auto 27px;
}
.user-row {
  justify-content: flex-end;
}
.message-content {
  max-width: calc(100% - 45px);
}
.message-name {
  margin: 0 0 7px 2px;
  color: var(--text-4);
  font-size: 11px;
}
.user-row .message-name {
  text-align: right;
  margin-right: 2px;
}
.bubble {
  padding: 12px 15px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.assistant-bubble {
  background: var(--surface-card);
  border: 1px solid var(--border);
  color: var(--text-2);
}
.user-bubble {
  background: #202b3c;
  color: var(--text-on-accent);
}
.citations {
  margin-top: 9px;
}
.citations-title {
  margin-bottom: 6px;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 500;
}
.citation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.citation-marker {
  position: relative;
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border: 1px solid #b8d0ed;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  cursor: default;
}
.citation-marker::after {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  z-index: 4;
  width: max-content;
  max-width: 260px;
  padding: 7px 9px;
  border-radius: 6px;
  background: #263447;
  color: var(--text-on-accent);
  content: attr(data-tooltip);
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 3px);
  transition: opacity 0.16s ease, transform 0.16s ease;
  white-space: normal;
  font-size: 11px;
  font-weight: 400;
}
.citation-marker:hover::after,
.citation-marker:focus::after {
  opacity: 1;
  transform: translate(-50%, 0);
}
.citation-chip {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  padding: 8px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-card);
  color: var(--text-2);
  font: inherit;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease,
    box-shadow 0.16s ease;
}
.citation-chip:hover {
  border-color: #b9cee8;
  background: var(--surface-inset);
}
.citation-chip:focus-visible {
  outline: none;
  border-color: #6d9bd3;
  box-shadow: 0 0 0 3px rgba(72, 132, 208, 0.16);
}
.citation-chip i {
  flex: 0 0 auto;
  color: var(--accent);
  font-size: 14px;
}
.citation-index {
  flex: 0 0 auto;
  color: var(--accent);
  font-weight: 600;
}
.citation-name {
  overflow: hidden;
  color: var(--text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  font-size: 12px;
}
.assistant-avatar {
  background: var(--accent-soft);
  color: var(--accent);
}
.user-avatar {
  background: rgba(255,255,255,0.14);
  color: var(--text-2);
}
.typing-caret {
  display: inline-block;
  width: 2px;
  height: 15px;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--accent);
  animation: blink 1s step-end infinite;
}
.running-line {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 760px;
  margin: -12px auto 20px 0;
  color: var(--text-4);
  font-size: 12px;
}
.stop-button {
  min-width: 88px;
  min-height: 44px;
  margin-left: 4px;
}
.running-line i {
  margin-right: 6px;
  color: var(--accent);
}
.composer-wrap {
  padding: 0 clamp(28px, 8vw, 130px) 21px;
  background: transparent;
}
.composer {
  max-width: 760px;
  margin: 0 auto;
  padding: 13px 15px 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  /* 贴合深色背景：用比面板更深的 inset 槽色，而不是与背景同色的 card 色 */
  background: var(--surface-inset);
  backdrop-filter: blur(10px);
  box-shadow: 0 6px 18px rgba(2, 6, 20, 0.28);
}
.composer:focus-within {
  border-color: rgba(76, 141, 255, 0.65);
  box-shadow: 0 0 0 3px rgba(76, 141, 255, 0.16), 0 6px 18px rgba(2, 6, 20, 0.28);
}
.composer textarea {
  display: block;
  width: 100%;
  resize: none;
  border: 0;
  outline: none;
  background: transparent;
  caret-color: var(--brand);
  color: var(--text-1);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
}
.composer textarea::placeholder {
  color: var(--text-4);
}
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 9px;
  color: var(--text-4);
  font-size: 11px;
}
.composer-toolbar .el-button {
  width: 32px;
  height: 32px;
  padding: 0;
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--border-strong);
  color: var(--text-1);
}
/* 发送键为淡蓝（与其它操作按钮统一），禁用时降为中性灰 */
.composer-toolbar .el-button.el-button--primary:not(.is-disabled) {
  background: var(--action);
  border-color: var(--action);
  color: var(--text-on-accent);
  transition: background 0.25s ease, box-shadow 0.25s ease;
}
.composer-toolbar .el-button.el-button--primary:not(.is-disabled):hover {
  background: linear-gradient(
    135deg,
    var(--action-light) 0%,
    var(--action) 55%,
    var(--action-deep) 100%
  );
  border-color: var(--action-light);
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.38);
}
.disclaimer {
  padding-top: 8px;
  color: var(--text-4);
  text-align: center;
  font-size: 11px;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
@media (max-width: 900px) {
  .session-panel {
    width: 220px;
    flex-basis: 220px;
  }
  .chat-header {
    padding: 0 20px;
  }
  .header-actions .el-button {
    display: none;
  }
  .messages {
    padding-left: 24px;
    padding-right: 24px;
  }
  .composer-wrap {
    padding-left: 24px;
    padding-right: 24px;
  }
}
</style>
