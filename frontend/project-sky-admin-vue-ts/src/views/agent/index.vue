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
        <button
          v-for="item in sessions"
          :key="item.sessionId"
          class="session-item"
          :class="{ active: item.sessionId === sessionId }"
          @click="openSession(item.sessionId)"
        >
          <span class="session-icon"><i class="el-icon-chat-dot-round" /></span>
          <span class="session-copy">
            <strong>{{ item.title || '新会话' }}</strong>
            <small>{{ formatDate(item.updateTime || item.createTime) }}</small>
          </span>
          <i
            class="el-icon-more"
            @click.stop="archiveSession(item.sessionId)"
          />
        </button>
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
            icon="el-icon-delete"
            aria-label="归档当前会话"
            @click="archiveCurrent"
          >
            归档
          </el-button>
        </div>
      </header>

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
            <div
              v-if="message.citations && message.citations.length"
              class="citations"
            >
              <div class="citations-title">参考来源</div>
              <div class="citation-list">
                <button
                  v-for="(source, sourceIndex) in uniqueCitations(message.citations)"
                  :key="source.chunkId"
                  type="button"
                  class="citation-chip"
                  :aria-label="`查看来源 ${sourceIndex + 1}：${source.fileName}`"
                  @click="openCitation(source)"
                >
                  <i class="el-icon-document" />
                  <span class="citation-index">来源 {{ sourceIndex + 1 }}</span>
                  <span class="citation-name">
                    {{ source.fileName
                    }}{{ source.pageNo ? ` · 第${source.pageNo}页` : '' }}
                  </span>
                </button>
              </div>
            </div>
          </div>
          <div v-if="message.role === 'user'" class="avatar user-avatar">
            我
          </div>
        </article>
        <div v-if="running" class="running-line">
          <i class="el-icon-loading" />
          正在思考...
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
              icon="el-icon-top"
              circle
              :disabled="!draft.trim() || running"
              aria-label="发送消息"
              @click="send"
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
} from '@/api/agent'
import { listKnowledgeBases } from '@/api/knowledge'
import { UserModule } from '@/store/modules/user'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  citations?: any[]
}
export default Vue.extend({
  name: 'AgentPage',
  data() {
    return {
      sessions: [] as any[],
      sessionId: '',
      model: '',
      kbId: '',
      kbLocked: false,
      knowledgeBases: [] as any[],
      draft: '',
      messages: [] as ChatMessage[],
      running: false,
      loadingSessions: false,
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
  },
  mounted() {
    this.loadSessions()
    this.loadKnowledgeBases()
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
        this.sessions = res.data?.data?.records || res.data?.data?.list || []
      } finally {
        this.loadingSessions = false
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
      await updateAgentSession({ sessionId: id, status: 2 })
      this.$message.success('会话已归档')
      await this.loadSessions()
      if (id === this.sessionId) {
        this.newChat()
      }
    },
    archiveCurrent() {
      if (this.sessionId) {
        this.archiveSession(this.sessionId)
      }
    },
    ask(text: string) {
      this.draft = text
      this.send()
    },
    formatDate(value: any) {
      return value ? String(value).replace('T', ' ').slice(5, 16) : ''
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
      }
      this.messages.push(assistant)
      this.running = true
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
        const payload = res.data?.data || {}
        this.sessionId = payload.sessionId || this.sessionId
        this.kbLocked = Boolean(this.kbId)
        await this.consumeEvents(payload.taskId, assistant)
        await this.loadSessions()
      } catch (error) {
        assistant.content = '抱歉，请求暂时失败，请稍后重试。'
        this.$message.error('AI 服务请求失败')
      } finally {
        assistant.streaming = false
        this.running = false
      }
    },
    async consumeEvents(taskId: string, assistant: ChatMessage) {
      if (!taskId) {
        return
      }
      const base = process.env.VUE_APP_BASE_API || '/api'
      const response = await fetch(`${base}/agent/tasks/${taskId}/events`, {
        headers: { token: UserModule.token, Accept: 'text/event-stream' },
      })
      if (!response.ok || !response.body) {
        throw new Error('SSE unavailable')
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const chunk = await reader.read()
        if (chunk.done) {
          break
        }
        buffer += decoder.decode(chunk.value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''
        parts.forEach((part: string) => {
          const data = part
            .split('\n')
            .filter((line) => line.indexOf('data:') === 0)
            .map((line) => line.slice(5).trim())
            .join('')
          if (!data) {
            return
          }
          try {
            const event: any = JSON.parse(data)
            const body = event.data || {}
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
              assistant.citations = (body.citations || []).map(
                this.normalizeCitation
              )
            }
            if (event.event === 'task_error') {
              assistant.content =
                body.error_msg || body.message || '任务执行失败'
            }
            this.$nextTick(this.scrollToBottom)
          } catch (_) {
            /* ignore keep-alive frames */
          }
        })
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
  opacity: 0;
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
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
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
  max-width: 760px;
  margin: -12px auto 20px 0;
  color: #9ba5b2;
  font-size: 12px;
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
