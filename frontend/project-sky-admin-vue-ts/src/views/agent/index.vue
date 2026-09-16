<template>
  <section class="agent-page">
    <aside class="session-panel">
      <div class="panel-heading">
        <div>
          <div class="eyebrow">SKY INTELLIGENCE</div>
          <h1>AI 助手</h1>
        </div>
        <el-button
          class="new-chat"
          type="primary"
          icon="el-icon-plus"
          circle
          aria-label="新建会话"
          @click="newChat"
        />
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
      <div class="panel-footer">
        <span class="status-dot" />
        Agent 服务在线
      </div>
    </aside>

    <main class="chat-panel">
      <header class="chat-header">
        <div>
          <span class="header-mark"><i class="el-icon-magic-stick" /></span>
          <span class="header-title">{{ currentTitle }}</span>
          <el-tag size="mini" type="success">在线</el-tag>
        </div>
        <div class="header-actions">
          <el-select
            v-model="kbId"
            size="small"
            class="kb-select"
            clearable
            placeholder="不使用知识库"
            :disabled="kbLocked"
          >
            <el-option
              v-for="item in knowledgeBases"
              :key="item.kbId"
              :label="item.name"
              :value="item.kbId"
            />
          </el-select>
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
          <h2>你好，我是 Sky AI</h2>
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
              {{ message.role === 'user' ? '我' : 'Sky AI' }}
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
              role="group"
              aria-label="AI 操作确认"
            >
              <div class="confirmation-heading">
                <i class="el-icon-warning-outline" aria-hidden="true" />
                <strong>需要你的确认</strong>
              </div>
              <p>{{ message.confirmation.summary }}</p>
              <small>请在 5 分钟内确认；确认后将立即执行。</small>
              <div class="confirmation-actions">
                <el-button
                  size="small"
                  :disabled="message.confirmation.processing || message.confirmation.decided"
                  @click="decideTool(message, false)"
                >拒绝</el-button>
                <el-button
                  type="danger"
                  size="small"
                  :loading="message.confirmation.processing"
                  :disabled="message.confirmation.decided"
                  @click="decideTool(message, true)"
                >确认执行</el-button>
              </div>
              <div v-if="message.confirmation.decision" class="confirmation-result" role="status">
                {{ message.confirmation.decision }}
              </div>
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
            placeholder="给 Sky AI 发送消息..."
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer-toolbar">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              type="primary"
              circle
              :disabled="cancelling || (!draft.trim() && !running)"
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

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  citations?: any[]
  confirmation?: any
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
  },
  beforeDestroy() {
    // 页面销毁只释放浏览器端 SSE，不把本地清理误当作后台任务取消。
    this.pageDestroyed = true
    if (this.streamController) {
      this.streamController.abort()
    }
  },
  methods: {
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
          this.applyStreamFailure(assistant)
        }
      } finally {
        assistant.streaming = false
        this.running = false
        this.cancelling = false
        this.currentTaskId = ''
        this.streamController = null
      }
    },
    applyStreamFailure(assistant: ChatMessage) {
      if (assistant.content) {
        const message = '连接恢复失败，已保留当前内容，可重新进入会话查看。'
        if (!assistant.content.includes(message)) {
          assistant.content = `${assistant.content}\n\n${message}`
        }
        this.$message.error('AI 服务连接恢复失败')
        return
      }
      assistant.content = '抱歉，请求暂时失败，请稍后重试。'
      this.$message.error('AI 服务请求失败')
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
      const body: any = event.data || {}
      this.streamStatus = '正在思考...'
      const text =
        body.content ||
        body.delta ||
        body.token ||
        body.text ||
        (event.event === 'task_end' ? body.result : '') ||
        (typeof body === 'string' ? body : '')
      if (text && !(event.event === 'task_end' && assistant.content)) {
        assistant.content += text
      }
      if (event.event === 'task_end') {
        assistant.citations = (body.citations || []).map(this.normalizeCitation)
      }
      if (event.event === 'tool_confirmation_required') {
        assistant.confirmation = {
          id: body.confirmation_id,
          summary: body.summary || '执行业务状态修改',
          expiresAt: body.expires_at,
          processing: false,
          decided: false,
          decision: '',
        }
      }
      if (event.event === 'task_error') {
        const errorMessage = body.error_msg || body.message || '任务执行失败'
        assistant.content = assistant.content
          ? `${assistant.content}\n\n（${errorMessage}）`
          : errorMessage
      }
      if (event.event === 'task_cancelled') {
        assistant.content = assistant.content
          ? `${assistant.content}\n\n（已停止生成）`
          : '已停止生成。'
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
        const value: any = error
        const message = value && value.response && value.response.data
          ? value.response.data.msg
          : ''
        confirmation.decision = message || '确认状态提交失败，请重试。'
        if (message && (message.indexOf('已过期') >= 0 || message.indexOf('已处理') >= 0)) {
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
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #f5c2c7;
  border-radius: 10px;
  background: #fff8f8;
  color: #442326;
}

.confirmation-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b42318;
}

.tool-confirmation p {
  margin: 10px 0 4px;
  line-height: 1.6;
}

.tool-confirmation small {
  color: #667085;
}

.confirmation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.confirmation-actions ::v-deep .el-button {
  min-width: 88px;
  min-height: 44px;
}

.confirmation-result {
  margin-top: 10px;
  color: #475467;
  font-size: 13px;
}

.agent-page {
  height: calc(100vh - 84px);
  min-height: 620px;
  display: flex;
  margin: 0;
  background: #f7f8fa;
  color: #1f2937;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    sans-serif;
}
.session-panel {
  width: 276px;
  flex: 0 0 276px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e8ebf0;
  padding: 26px 18px 18px;
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 8px 20px;
}
.eyebrow {
  color: #97a1b2;
  font-size: 10px;
  letter-spacing: 1.4px;
  font-weight: 700;
}
.panel-heading h1 {
  margin: 7px 0 0;
  font-size: 23px;
  color: #202633;
}
.new-chat {
  background: #1f2937;
  border-color: #1f2937;
}
.new-chat-wide {
  width: 100%;
  background: #f1f4f8;
  border: 0;
  color: #344054;
  margin-bottom: 24px;
}
.session-label {
  color: #98a1af;
  font-size: 12px;
  margin: 0 8px 10px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
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
  color: #657083;
}
.session-item:hover,
.session-item.active {
  background: #f2f5f8;
  color: #1f2937;
}
.session-icon {
  width: 28px;
  color: #9ba5b3;
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
  color: #a3abb7;
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
  color: #a4acb8;
  font-size: 12px;
}
.session-menu {
  position: absolute;
  top: 42px;
  right: 8px;
  z-index: 5;
  width: 112px;
  padding: 5px;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  background: #fff;
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
  color: #526174;
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}
.session-menu button:hover {
  background: #f2f5f8;
  color: #3477ca;
}
.panel-footer {
  border-top: 1px solid #eef0f3;
  padding: 16px 8px 0;
  color: #8a95a4;
  font-size: 12px;
}
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: #26b57a;
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
  border-left: 1px solid #e3e8ee;
  background: #fff;
  box-shadow: -8px 0 24px rgba(28, 40, 58, 0.08);
}
.archive-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef0f3;
}
.archive-heading strong,
.archive-heading small {
  display: block;
}
.archive-heading strong {
  color: #202633;
  font-size: 17px;
}
.archive-heading small {
  margin-top: 5px;
  color: #9aa3b0;
  font-size: 11px;
}
.archive-heading button,
.archive-delete {
  border: 0;
  background: transparent;
  color: #8995a4;
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
  border-bottom: 1px solid #f0f2f5;
}
.archive-open {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #526174;
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
.archive-open small { margin-top: 5px; color: #a3abb7; font-size: 11px; }
.archive-open:hover { color: #3477ca; }
.archive-delete { min-width: 32px; min-height: 32px; }
.archive-delete:hover { color: #d9534f; }
.chat-header {
  height: 66px;
  flex: 0 0 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 38px;
  background: #fff;
  border-bottom: 1px solid #e8ebf0;
}
.header-mark {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  border-radius: 8px;
  background: #eef5ff;
  color: #3378d5;
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
  background: #eaf2ff;
  color: #3e82d9;
  font-size: 25px;
}
.welcome-state h2 {
  margin: 18px 0 8px;
  color: #202633;
  font-size: 25px;
  font-weight: 600;
}
.welcome-state p {
  margin: 0 0 27px;
  color: #8993a2;
  font-size: 14px;
}
.suggestions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}
.suggestions button {
  border: 1px solid #e1e6ed;
  border-radius: 7px;
  background: #fff;
  color: #5e6a7a;
  padding: 11px 13px;
  cursor: pointer;
  font-size: 12px;
}
.suggestions button:hover {
  border-color: #a7c6ef;
  color: #3477ca;
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
  color: #9aa3b0;
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
  background: #fff;
  border: 1px solid #e7ebf0;
  color: #364152;
}
.user-bubble {
  background: #202b3c;
  color: #fff;
}
.citations {
  margin-top: 9px;
}
.citations-title {
  margin-bottom: 6px;
  color: #7b8797;
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
  background: #eef5ff;
  color: #3477ca;
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
  color: #fff;
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
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  background: #fff;
  color: #425268;
  font: inherit;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease,
    box-shadow 0.16s ease;
}
.citation-chip:hover {
  border-color: #b9cee8;
  background: #f5f9ff;
}
.citation-chip:focus-visible {
  outline: none;
  border-color: #6d9bd3;
  box-shadow: 0 0 0 3px rgba(72, 132, 208, 0.16);
}
.citation-chip i {
  flex: 0 0 auto;
  color: #4f87cf;
  font-size: 14px;
}
.citation-index {
  flex: 0 0 auto;
  color: #4f87cf;
  font-weight: 600;
}
.citation-name {
  overflow: hidden;
  color: #66758a;
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
  background: #eaf2ff;
  color: #3f82d8;
}
.user-avatar {
  background: #dbe3ed;
  color: #506072;
}
.typing-caret {
  display: inline-block;
  width: 2px;
  height: 15px;
  margin-left: 3px;
  vertical-align: -2px;
  background: #4585d1;
  animation: blink 1s step-end infinite;
}
.running-line {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 760px;
  margin: -12px auto 20px 0;
  color: #9ba5b2;
  font-size: 12px;
}
.stop-button {
  min-width: 88px;
  min-height: 44px;
  margin-left: 4px;
}
.running-line i {
  margin-right: 6px;
  color: #4f87cf;
}
.composer-wrap {
  padding: 0 clamp(28px, 8vw, 130px) 21px;
  background: #f7f8fa;
}
.composer {
  max-width: 760px;
  margin: 0 auto;
  padding: 13px 15px 10px;
  border: 1px solid #dce2e9;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(28, 40, 58, 0.04);
}
.composer:focus-within {
  border-color: #8bb4e8;
  box-shadow: 0 0 0 3px rgba(72, 132, 208, 0.1);
}
.composer textarea {
  display: block;
  width: 100%;
  resize: none;
  border: 0;
  outline: none;
  color: #253143;
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
}
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 9px;
  color: #a1a9b5;
  font-size: 11px;
}
.composer-toolbar .el-button {
  width: 32px;
  height: 32px;
  padding: 0;
  background: #202b3c;
  border-color: #202b3c;
}
.disclaimer {
  padding-top: 8px;
  color: #a6adb8;
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
