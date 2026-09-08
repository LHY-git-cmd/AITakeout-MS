<template>
  <section class="knowledge-page">
    <header class="page-header">
      <div>
        <h1>知识库</h1>
        <p>维护 AI 回答所使用的企业文档</p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="dialogVisible = true">新建知识库</el-button>
    </header>
    <div class="workspace">
      <aside class="base-list">
        <button
          v-for="item in bases"
          :key="item.kbId"
          :class="{ active: selected && selected.kbId === item.kbId }"
          @click="selectBase(item)"
        >
          <i class="el-icon-collection" />
          <span>
            <strong>{{ item.name }}</strong>
            <small>{{ item.description || '暂无描述' }}</small>
          </span>
          <el-tag size="mini" :type="item.status === 1 ? 'success' : 'info'">
            {{ item.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </button>
        <el-empty v-if="!bases.length" description="暂无知识库" :image-size="72" />
      </aside>
      <main class="document-panel">
        <template v-if="selected">
          <div class="panel-toolbar">
            <div>
              <h2>{{ selected.name }}</h2>
              <span>{{ documents.length }} 个文档</span>
            </div>
            <div>
              <el-upload action="" :show-file-list="false" :http-request="upload">
                <el-button icon="el-icon-upload2">上传文档</el-button>
              </el-upload>
              <el-button icon="el-icon-setting" @click="toggleBase">
                {{ selected.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button type="danger" plain icon="el-icon-delete" @click="removeBase">删除</el-button>
            </div>
          </div>
          <el-table v-loading="loading" :data="documents" height="calc(100vh - 260px)">
            <el-table-column prop="fileName" label="文件" min-width="220">
              <template slot-scope="scope">
                <div class="file-name">
                  <i class="el-icon-document" />
                  <span>{{ scope.row.fileName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="chunkCount" label="分块" width="90" />
            <el-table-column label="状态" width="110">
              <template slot-scope="scope">
                <el-tag size="small" :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMsg" label="处理信息" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="250" fixed="right">
              <template slot-scope="scope">
                <el-upload
                  class="inline-upload"
                  action=""
                  :show-file-list="false"
                  :http-request="(option) => uploadVersion(scope.row, option)"
                >
                  <el-button type="text" icon="el-icon-upload2">新版本</el-button>
                </el-upload>
                <el-button type="text" icon="el-icon-refresh" @click="reindex(scope.row)">重建</el-button>
                <el-button type="text" class="danger" icon="el-icon-delete" @click="removeDocument(scope.row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="选择一个知识库查看文档" />
      </main>
    </div>
    <el-dialog title="新建知识库" :visible.sync="dialogVisible" width="460px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </span>
    </el-dialog>
  </section>
</template>
<script lang="ts">
import Vue from 'vue'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  listDocuments,
  uploadDocument,
  uploadDocumentVersion,
  reindexDocument,
  deleteDocument,
} from '@/api/knowledge'
export default Vue.extend({
  name: 'KnowledgePage',
  data() {
    return {
      bases: [] as any[],
      selected: null as any,
      documents: [] as any[],
      loading: false,
      saving: false,
      dialogVisible: false,
      form: { name: '', description: '' },
    }
  },
  mounted() {
    this.loadBases()
  },
  methods: {
    async loadBases() {
      const res: any = await listKnowledgeBases()
      this.bases = res.data?.data || []
      if (this.selected) {
        this.selected = this.bases.find((x: any) => x.kbId === this.selected.kbId) || null
      } else if (this.bases.length) {
        this.selectBase(this.bases[0])
      }
    },
    async selectBase(item: any) {
      this.selected = item
      this.loading = true
      try {
        const res: any = await listDocuments(item.kbId)
        this.documents = res.data?.data || []
      } finally {
        this.loading = false
      }
    },
    async create() {
      if (!this.form.name.trim()) {
        return this.$message.warning('请输入知识库名称')
      }
      this.saving = true
      try {
        await createKnowledgeBase(this.form)
        this.dialogVisible = false
        this.form = { name: '', description: '' }
        await this.loadBases()
        this.$message.success('知识库已创建')
      } finally {
        this.saving = false
      }
    },
    async upload(option: any) {
      try {
        await uploadDocument(this.selected.kbId, option.file)
        this.$message.success('文件已上传，正在建立索引')
        await this.selectBase(this.selected)
      } catch (e) {
        this.$message.error('上传失败，请检查文件格式或是否重复')
      }
    },
    async uploadVersion(row: any, option: any) {
      try {
        await uploadDocumentVersion(row.documentId, option.file)
        this.$message.success('新版本已上传，索引完成后自动切换')
        await this.selectBase(this.selected)
      } catch (e) {
        this.$message.error('新版本上传失败，请检查文件格式或内容是否重复')
      }
    },
    async toggleBase() {
      await updateKnowledgeBase(this.selected.kbId, {
        name: this.selected.name,
        description: this.selected.description,
        status: this.selected.status === 1 ? 2 : 1,
      })
      await this.loadBases()
    },
    async removeBase() {
      await this.$confirm('删除后该知识库将停止召回，是否继续？', '删除知识库', { type: 'warning' })
      await deleteKnowledgeBase(this.selected.kbId)
      this.selected = null
      await this.loadBases()
    },
    async reindex(row: any) {
      await reindexDocument(row.documentId)
      this.$message.success('已提交重建任务')
      await this.selectBase(this.selected)
    },
    async removeDocument(row: any) {
      await this.$confirm(`删除“${row.fileName}”？`, '删除文档', {
        type: 'warning',
      })
      await deleteDocument(row.documentId)
      await this.selectBase(this.selected)
    },
    statusText(v: number) {
      return ['已上传', '解析中', '向量化', '可用', '失败', '停用', '已删除'][v] || '未知'
    },
    statusType(v: number) {
      return v === 3 ? 'success' : v === 4 ? 'danger' : v < 3 ? 'warning' : 'info'
    },
  },
})
</script>
<style scoped lang="scss">
.knowledge-page {
  height: calc(100vh - 84px);
  background: #f8fafc;
  color: #1e293b;
}
.page-header {
  height: 86px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.page-header h1 {
  margin: 0;
  font-size: 22px;
}
.page-header p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
}
.workspace {
  height: calc(100% - 87px);
  display: grid;
  grid-template-columns: 290px 1fr;
}
.base-list {
  padding: 14px;
  border-right: 1px solid #e2e8f0;
  background: #fff;
  overflow: auto;
}
.base-list button {
  width: 100%;
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.base-list button:hover,
.base-list button.active {
  background: #f1f5f9;
  border-color: #e2e8f0;
}
.base-list span {
  min-width: 0;
  flex: 1;
}
.base-list strong,
.base-list small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.base-list small {
  margin-top: 5px;
  color: #64748b;
}
.document-panel {
  min-width: 0;
  padding: 20px 24px;
}
.panel-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.panel-toolbar h2 {
  display: inline;
  margin: 0 10px 0 0;
  font-size: 18px;
}
.panel-toolbar span {
  color: #64748b;
  font-size: 12px;
}
.panel-toolbar > div:last-child {
  display: flex;
  gap: 8px;
}
.file-name {
  display: flex;
  align-items: center;
  gap: 9px;
}
.inline-upload {
  display: inline-block;
  margin-right: 10px;
}
.danger {
  color: #dc2626;
}
@media (max-width: 800px) {
  .workspace {
    grid-template-columns: 1fr;
  }
  .base-list {
    max-height: 180px;
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
  }
  .document-panel {
    padding: 14px;
  }
  .panel-toolbar {
    align-items: flex-start;
    gap: 12px;
  }
  .panel-toolbar > div:last-child {
    flex-wrap: wrap;
  }
}
</style>
