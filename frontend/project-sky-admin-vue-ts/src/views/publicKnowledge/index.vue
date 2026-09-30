<template>
  <section class="public-kb-page">
    <header class="page-header">
      <div>
        <h1>用户公共知识库</h1>
        <p>上传、审核并发布用户端 AI 可引用的公共文档</p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="baseDialog = true">新建知识库</el-button>
    </header>

    <div class="workspace">
      <aside class="base-list" aria-label="公共知识库列表">
        <button
          v-for="base in bases" :key="base.kbId"
          :class="{ active: selectedBase && selectedBase.kbId === base.kbId }"
          @click="selectBase(base)"
        >
          <i class="el-icon-collection" aria-hidden="true" />
          <span><strong>{{ base.name }}</strong><small>{{ base.category }} · {{ base.description || '暂无描述' }}</small></span>
          <el-tag size="mini" :type="baseStatusType(base)">{{ baseStatusText(base) }}</el-tag>
        </button>
        <el-empty v-if="!bases.length" description="暂无公共知识库" :image-size="72" />
      </aside>

      <main class="content-panel">
        <template v-if="selectedBase">
          <div class="base-summary">
            <div><h2>{{ selectedBase.name }}</h2><span>{{ selectedBase.category }} · {{ documents.length }} 个文档版本</span></div>
            <div class="summary-actions">
              <el-button v-if="!baseRetired" icon="el-icon-setting" @click="toggleBase">{{ selectedBase.status === 1 ? '停用上传' : '恢复上传' }}</el-button>
              <el-button v-if="canGovern && !baseRetired" type="danger" plain icon="el-icon-remove-outline" :loading="saving" @click="archiveBase">撤下知识库</el-button>
              <el-button v-if="canGovern && ['ARCHIVED', 'PURGE_FAILED'].includes(selectedBase.lifecycleStatus)" type="danger" icon="el-icon-delete" :loading="saving" @click="purgeBase">{{ selectedBase.lifecycleStatus === 'PURGE_FAILED' ? '重试永久清除' : '永久清除' }}</el-button>
            </div>
          </div>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="文档与索引" name="documents">
              <div class="tab-toolbar">
                <el-select v-model="uploadCategory" size="small" aria-label="上传文档分类">
                  <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
                </el-select>
                <el-upload action="" :show-file-list="false" :http-request="upload" :disabled="uploading || selectedBase.status !== 1">
                  <el-button type="primary" size="small" icon="el-icon-upload2" :loading="uploading" :disabled="selectedBase.status !== 1">上传文档</el-button>
                </el-upload>
                <span class="hint">支持 PDF、DOCX、TXT、Markdown，上传后自动索引</span>
              </div>
              <el-alert v-if="uploadError" :title="uploadError" type="error" show-icon :closable="false" />
              <el-table v-loading="loading" :data="documents" class="data-table">
                <el-table-column prop="fileName" label="文件" min-width="190" show-overflow-tooltip />
                <el-table-column prop="version" label="版本" width="72"><template slot-scope="s">v{{ s.row.version }}</template></el-table-column>
                <el-table-column prop="category" label="分类" width="110" />
                <el-table-column label="索引状态" width="110">
                  <template slot-scope="s"><el-tag size="small" :type="indexType(s.row.status)">{{ indexText(s.row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="chunkCount" label="分块" width="72" />
                <el-table-column prop="errorMsg" label="处理信息" min-width="150" show-overflow-tooltip />
                <el-table-column label="操作" min-width="290" fixed="right">
                  <template slot-scope="s">
                    <el-upload class="inline-upload" action="" :show-file-list="false" :http-request="o => uploadVersion(s.row, o)">
                      <el-button type="text">新版本</el-button>
                    </el-upload>
                    <el-button type="text" @click="reindex(s.row)">重建索引</el-button>
                    <el-button type="text" class="danger" @click="removeDocument(s.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="发布与回滚" name="releases">
              <div class="tab-toolbar">
                <el-button type="primary" size="small" icon="el-icon-plus" :disabled="baseRetired" @click="createRelease">新建发布版本</el-button>
                <span class="hint">发布版本只能追加已完成索引的文档，版本审核通过后才能发布</span>
              </div>
              <el-table :data="currentReleases" class="data-table" @row-click="loadReleaseDetail">
                <el-table-column prop="releaseVersion" label="版本" width="80"><template slot-scope="s">R{{ s.row.releaseVersion }}</template></el-table-column>
                <el-table-column prop="releaseId" label="发布 ID" min-width="220" show-overflow-tooltip />
                <el-table-column label="状态" width="110"><template slot-scope="s"><el-tag size="small" :type="releaseType(s.row.status)">{{ releaseText(s.row.status) }}</el-tag></template></el-table-column>
                <el-table-column prop="createdAt" label="创建时间" min-width="155" />
                <el-table-column label="操作" min-width="330" fixed="right">
                  <template slot-scope="s">
                    <el-button v-if="s.row.status === 'DRAFT'" type="text" @click.stop="openBindDialog(s.row)">添加文档</el-button>
                    <el-button v-if="s.row.status === 'DRAFT'" type="text" class="danger" @click.stop="abandonRelease(s.row)">废弃草稿</el-button>
                    <el-button v-if="canGovern && s.row.status === 'DRAFT'" type="text" @click.stop="reviewRelease(s.row, true)">审核通过</el-button>
                    <el-button v-if="canGovern && s.row.status === 'APPROVED'" type="text" @click.stop="publishRelease(s.row)">发布</el-button>
                    <el-button v-if="canGovern && s.row.status === 'PUBLISHED'" type="text" @click.stop="bindScene(s.row)">绑定用户会话</el-button>
                    <el-button v-if="canGovern && s.row.status === 'PUBLISHED'" type="text" class="danger" @click.stop="offlineRelease(s.row)">下线</el-button>
                    <el-button v-if="canGovern && s.row.status === 'OFFLINE'" type="text" @click.stop="rollbackRelease(s.row)">回滚上线</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <section v-if="releaseDetail" class="release-detail">
                <h3>版本详情</h3>
                <p>已绑定 {{ releaseDetail.documents.length }} 个文档版本，审核记录 {{ releaseDetail.reviews.length }} 条。</p>
                <el-tag
                  v-for="doc in releaseDetail.documents" :key="`${doc.documentId}-${doc.documentVersion}`" size="small"
                  :closable="releaseDetail.release.status === 'DRAFT'"
                  @close="unbindDocument(releaseDetail.release, doc)"
                >{{ doc.documentId }} · v{{ doc.documentVersion }}</el-tag>
              </section>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else description="选择一个公共知识库开始维护" />
      </main>
    </div>

    <el-dialog title="新建公共知识库" :visible.sync="baseDialog" width="460px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="baseForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="baseForm.category"><el-option v-for="item in categories" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="baseForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="baseDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="createBase">创建</el-button></span>
    </el-dialog>

    <el-dialog title="添加已完成索引的文档版本" :visible.sync="bindDialog" width="560px">
      <el-select v-model="bindDocumentId" filterable placeholder="请选择文档版本" class="full-width">
        <el-option v-for="doc in publishableDocuments" :key="`${doc.documentId}-${doc.version}`" :label="`${doc.fileName} · v${doc.version} · ${doc.category}`" :value="`${doc.documentId}|${doc.version}`" />
      </el-select>
      <span slot="footer"><el-button @click="bindDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="bindDocument">添加</el-button></span>
    </el-dialog>
  </section>
</template>

<script lang="ts">
import Vue from 'vue'
import * as api from '@/api/publicKnowledge'
import { UserModule } from '@/store/modules/user'

export default Vue.extend({
  name: 'PublicKnowledgePage',
  data() {
    return {
      bases: [] as any[], selectedBase: null as any, documents: [] as any[], releases: [] as any[],
      releaseDetail: null as any, selectedRelease: null as any, activeTab: 'documents',
      loading: false, saving: false, uploading: false, uploadError: '',
      documentRefreshTimer: 0 as number,
      baseDialog: false, bindDialog: false, bindDocumentId: '', uploadCategory: 'GENERAL',
      categories: ['GENERAL', 'ORDER', 'AFTER_SALE', 'ACCOUNT', 'DELIVERY'],
      baseForm: { name: '', description: '', category: 'GENERAL' },
    }
  },
  computed: {
    canGovern(): boolean { return UserModule.roles.some(role => String(role).toUpperCase() === 'SUPER_ADMIN') },
    baseRetired(): boolean { return ['ARCHIVED', 'PURGE_PENDING', 'PURGE_FAILED', 'PURGED'].includes(this.selectedBase?.lifecycleStatus) },
    currentReleases(): any[] { return this.selectedBase ? this.releases.filter((x: any) => x.kbId === this.selectedBase.kbId) : [] },
    publishableDocuments(): any[] { return this.documents.filter((x: any) => x.status === 3) },
  },
  mounted() { this.loadAll() },
  beforeDestroy() { window.clearTimeout(this.documentRefreshTimer) },
  methods: {
    dataOf(res: any) { return res.data?.data || [] },
    async loadAll() {
      const [baseRes, releaseRes]: any[] = await Promise.all([api.listPublicBases(), api.listPublicReleases()])
      this.bases = this.dataOf(baseRes); this.releases = this.dataOf(releaseRes)
      if (this.selectedBase) this.selectedBase = this.bases.find((x: any) => x.kbId === this.selectedBase.kbId) || null
      if (!this.selectedBase && this.bases.length) await this.selectBase(this.bases[0])
    },
    async selectBase(base: any) {
      window.clearTimeout(this.documentRefreshTimer)
      this.selectedBase = base; this.uploadCategory = base.category || 'GENERAL'; this.loading = true
      try { const res: any = await api.listPublicDocuments(base.kbId); this.documents = this.dataOf(res); this.scheduleDocumentRefresh() } finally { this.loading = false }
    },
    scheduleDocumentRefresh() {
      window.clearTimeout(this.documentRefreshTimer)
      if (!this.selectedBase || !this.documents.some((document: any) => [0, 1, 2].includes(document.status))) return
      this.documentRefreshTimer = window.setTimeout(() => this.selectBase(this.selectedBase), 3000)
    },
    async createBase() {
      if (!this.baseForm.name.trim()) return this.$message.warning('请输入知识库名称')
      this.saving = true
      try { await api.createPublicBase(this.baseForm); this.baseDialog = false; this.baseForm = { name: '', description: '', category: 'GENERAL' }; await this.loadAll(); this.$message.success('公共知识库已创建') } finally { this.saving = false }
    },
    async toggleBase() {
      await api.updatePublicBase(this.selectedBase.kbId, { ...this.selectedBase, status: this.selectedBase.status === 1 ? 2 : 1 })
      await this.loadAll()
    },
    async archiveBase() {
      await this.$confirm('撤下后将立即停用用户会话绑定，用户端不再检索此知识库。历史发布记录仍会保留。', '撤下知识库', { type: 'warning', confirmButtonText: '确认撤下' })
      this.saving = true
      try { await api.archivePublicBase(this.selectedBase.kbId); await this.loadAll(); this.$message.success('知识库已撤下，用户端访问已停止') } finally { this.saving = false }
    },
    async purgeBase() {
      const result: any = await this.$prompt(`此操作会永久删除文档文件和向量。请输入知识库名称“${this.selectedBase.name}”确认。`, '永久清除知识库', { type: 'warning', confirmButtonText: '永久清除', inputPlaceholder: this.selectedBase.name })
      this.saving = true
      try { await api.purgePublicBase(this.selectedBase.kbId, result.value); await this.loadAll(); this.$message.success('永久清理任务已提交，完成前将显示“清理中”') } finally { this.saving = false }
    },
    async upload(option: any) {
      this.uploading = true; this.uploadError = ''
      try { await api.uploadPublicDocument(this.selectedBase.kbId, option.file, this.uploadCategory); this.$message.success('上传成功，索引任务已提交'); await this.selectBase(this.selectedBase) }
      catch (e) { this.uploadError = '上传失败，请检查文件格式、大小或重复内容' } finally { this.uploading = false }
    },
    async uploadVersion(row: any, option: any) {
      this.uploading = true
      try { await api.uploadPublicDocumentVersion(row.documentId, option.file, row.category); this.$message.success('新版本已上传'); await this.selectBase(this.selectedBase) }
      finally { this.uploading = false }
    },
    async reindex(row: any) { await api.reindexPublicDocument(row.documentId); this.$message.success('索引任务已重新提交'); await this.selectBase(this.selectedBase) },
    async removeDocument(row: any) { await this.$confirm(`删除“${row.fileName}”的全部版本？系统会自动解除草稿引用；已审核或历史发布版本引用的文档仍受保护。`, '删除文档', { type: 'warning' }); await api.deletePublicDocument(row.documentId); this.releaseDetail = null; await this.selectBase(this.selectedBase); this.$message.success('文档已删除') },
    async createRelease() { await api.createPublicRelease(this.selectedBase.kbId); await this.loadAll(); this.activeTab = 'releases'; this.$message.success('发布草稿已创建') },
    openBindDialog(row: any) { this.selectedRelease = row; this.bindDocumentId = ''; this.bindDialog = true },
    async bindDocument() {
      if (!this.bindDocumentId) return this.$message.warning('请选择文档版本')
      const [id, version] = this.bindDocumentId.split('|'); const doc = this.documents.find((x: any) => x.documentId === id && x.version === Number(version))
      this.saving = true
      try { await api.bindPublicReleaseDocument(this.selectedRelease.releaseId, doc); this.bindDialog = false; await this.loadReleaseDetail(this.selectedRelease); this.$message.success('文档版本已添加') } finally { this.saving = false }
    },
    async unbindDocument(release: any, doc: any) {
      await this.$confirm(`从草稿 R${release.releaseVersion} 中移除该文档版本？`, '移除草稿文档', { type: 'warning' })
      await api.unbindPublicReleaseDocument(release.releaseId, doc.documentId, doc.documentVersion)
      await this.loadReleaseDetail(release)
      this.$message.success('文档版本已从草稿移除')
    },
    async abandonRelease(row: any) {
      await this.$confirm(`废弃草稿 R${row.releaseVersion}？草稿中的文档引用将被解除，此操作不能恢复。`, '废弃发布草稿', { type: 'warning', confirmButtonText: '确认废弃' })
      await api.abandonPublicRelease(row.releaseId)
      this.releaseDetail = null
      await this.loadAll()
      this.$message.success('发布草稿已废弃')
    },
    async loadReleaseDetail(row: any) { const res: any = await api.getPublicRelease(row.releaseId); this.releaseDetail = res.data?.data || null },
    async reviewRelease(row: any, approved: boolean) { await api.reviewPublicRelease(row.releaseId, approved); await this.loadAll(); this.$message.success('发布版本审核通过') },
    async publishRelease(row: any) { await api.publishPublicRelease(row.releaseId); await this.loadAll(); this.$message.success('发布成功，向量范围已生效') },
    async bindScene(row: any) { await api.bindPublicScene(row.releaseId); this.$message.success('已绑定用户会话场景') },
    async offlineRelease(row: any) { await this.$confirm('下线后用户端将立即停止召回该版本，是否继续？', '下线发布版本', { type: 'warning' }); await api.offlinePublicRelease(row.releaseId); await this.loadAll() },
    async rollbackRelease(row: any) { await api.rollbackPublicRelease(row.releaseId); await this.loadAll(); this.$message.success('版本已重新上线') },
    indexText(v: number) { return ['待索引', '解析中', '向量化', '可用', '失败', '待确认', '已删除'][v] || '未知' },
    indexType(v: number) { return v === 3 ? 'success' : v === 4 ? 'danger' : v < 3 || v === 5 ? 'warning' : 'info' },
    releaseText(v: string) { return ({ DRAFT: '草稿', APPROVED: '已审核', PUBLISHED: '已发布', OFFLINE: '已下线', ABANDONED: '已废弃' } as any)[v] || v },
    releaseType(v: string) { return v === 'PUBLISHED' ? 'success' : v === 'OFFLINE' ? 'info' : v === 'APPROVED' ? 'warning' : '' },
    baseStatusText(base: any) { return ({ ARCHIVED: '已撤下', PURGE_PENDING: '清理中', PURGE_FAILED: '清理失败' } as any)[base.lifecycleStatus] || (base.status === 1 ? '启用' : '停用') },
    baseStatusType(base: any) { return base.lifecycleStatus === 'PURGE_PENDING' ? 'warning' : ['ARCHIVED', 'PURGE_FAILED'].includes(base.lifecycleStatus) ? 'danger' : base.status === 1 ? 'success' : 'info' },
  },
})
</script>

<style scoped lang="scss">
.public-kb-page { height: calc(100vh - 84px); color: var(--text-2); }
.page-header { min-height: 86px; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid var(--field-border); background: var(--surface-card); }
.page-header h1 { margin: 0; font-size: 22px; color: var(--text-on-accent); }
.page-header p { margin: 5px 0 0; color: var(--text-3); font-size: 13px; }
.workspace { height: calc(100% - 87px); display: grid; grid-template-columns: 280px minmax(0, 1fr); }
.base-list { padding: 14px; overflow: auto; border-right: 1px solid var(--field-border); background: var(--surface-card); }
.base-list button { width: 100%; min-height: 68px; padding: 10px; display: flex; align-items: center; gap: 10px; border: 1px solid transparent; background: transparent; text-align: left; cursor: pointer; }
.base-list button:hover, .base-list button.active { background: var(--surface-hover); border-color: var(--border-strong); }
.base-list span { flex: 1; min-width: 0; }
.base-list strong, .base-list small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.base-list strong { color: var(--text-1); } .base-list small { margin-top: 5px; color: var(--text-3); }
.content-panel { min-width: 0; padding: 20px 24px; overflow: auto; }
.base-summary, .tab-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.base-summary { margin-bottom: 10px; } .base-summary h2 { display: inline; margin: 0 10px 0 0; color: var(--text-1); font-size: 19px; } .base-summary span, .hint { color: var(--text-3); font-size: 12px; }
.summary-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.tab-toolbar { justify-content: flex-start; margin-bottom: 14px; flex-wrap: wrap; }
.data-table { width: 100%; } .inline-upload { display: inline-block; margin: 0 10px; } .danger { color: #dc2626; }
.release-detail { margin-top: 18px; padding: 16px; border: 1px solid var(--field-border); background: var(--surface-card); }
.release-detail h3 { margin: 0 0 8px; color: var(--text-1); } .release-detail p { margin: 0 0 10px; color: var(--text-3); } .release-detail .el-tag { margin: 0 8px 8px 0; }
.full-width { width: 100%; }
@media (max-width: 1024px) { .workspace { grid-template-columns: 230px minmax(0, 1fr); } .content-panel { padding: 16px; } }
@media (max-width: 768px) { .public-kb-page { height: auto; min-height: calc(100vh - 84px); } .page-header { padding: 14px 16px; align-items: flex-start; } .workspace { height: auto; grid-template-columns: 1fr; } .base-list { max-height: 190px; border-right: 0; border-bottom: 1px solid var(--field-border); } .base-summary { align-items: flex-start; } }
@media (max-width: 420px) { .page-header { flex-direction: column; } .content-panel { padding: 12px; } .base-summary { flex-direction: column; } .summary-actions { justify-content: flex-start; } }
</style>
