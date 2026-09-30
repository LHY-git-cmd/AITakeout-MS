import PublicKnowledgePage from '@/views/publicKnowledge/index.vue'
import * as api from '@/api/publicKnowledge'

jest.mock('@/api/publicKnowledge', () => ({
  listPublicBases: jest.fn(), listPublicReleases: jest.fn(), listPublicDocuments: jest.fn(),
  createPublicBase: jest.fn(), updatePublicBase: jest.fn(), archivePublicBase: jest.fn(), purgePublicBase: jest.fn(), uploadPublicDocument: jest.fn(),
  uploadPublicDocumentVersion: jest.fn(), reindexPublicDocument: jest.fn(),
  reviewPublicDocument: jest.fn(), deletePublicDocument: jest.fn(), createPublicRelease: jest.fn(),
  getPublicRelease: jest.fn(), bindPublicReleaseDocument: jest.fn(), reviewPublicRelease: jest.fn(),
  unbindPublicReleaseDocument: jest.fn(), abandonPublicRelease: jest.fn(),
  publishPublicRelease: jest.fn(), offlinePublicRelease: jest.fn(), rollbackPublicRelease: jest.fn(),
  bindPublicScene: jest.fn(),
}))

const methods = (PublicKnowledgePage as any).options.methods

describe('PublicKnowledgePage workflow', () => {
  beforeEach(() => jest.clearAllMocks())

  it('loads isolated public bases, releases and selected-base documents', async() => {
    const base = { kbId: 'public-kb', name: '用户规则', category: 'GENERAL' }
    ;(api.listPublicBases as jest.Mock).mockResolvedValue({ data: { data: [base] } })
    ;(api.listPublicReleases as jest.Mock).mockResolvedValue({ data: { data: [{ kbId: 'public-kb' }] } })
    ;(api.listPublicDocuments as jest.Mock).mockResolvedValue({ data: { data: [{ documentId: 'doc-1' }] } })
    const context: any = {
      bases: [], releases: [], documents: [], selectedBase: null, loading: false,
      dataOf: methods.dataOf,
      documentRefreshTimer: 0, scheduleDocumentRefresh: jest.fn(),
      selectBase: async(item: any) => methods.selectBase.call(context, item),
    }

    await methods.loadAll.call(context)

    expect(context.selectedBase).toBe(base)
    expect(context.documents).toEqual([{ documentId: 'doc-1' }])
    expect(api.listPublicDocuments).toHaveBeenCalledWith('public-kb')
  })

  it('keeps a visible inline error when upload fails', async() => {
    ;(api.uploadPublicDocument as jest.Mock).mockRejectedValue(new Error('invalid file'))
    const context: any = {
      selectedBase: { kbId: 'public-kb' }, uploadCategory: 'GENERAL',
      uploading: false, uploadError: '', $message: { success: jest.fn() },
      selectBase: jest.fn(),
    }

    await methods.upload.call(context, { file: new File(['x'], 'x.txt') })

    expect(context.uploading).toBe(false)
    expect(context.uploadError).toContain('上传失败')
    expect(context.selectBase).not.toHaveBeenCalled()
  })

  it('publishes through the public release endpoint then reloads state', async() => {
    ;(api.publishPublicRelease as jest.Mock).mockResolvedValue({})
    const context: any = { loadAll: jest.fn(), $message: { success: jest.fn() } }

    await methods.publishRelease.call(context, { releaseId: 'release-1' })

    expect(api.publishPublicRelease).toHaveBeenCalledWith('release-1')
    expect(context.loadAll).toHaveBeenCalled()
    expect(context.$message.success).toHaveBeenCalledWith('发布成功，向量范围已生效')
  })

  it('abandons a draft after destructive-action confirmation', async() => {
    ;(api.abandonPublicRelease as jest.Mock).mockResolvedValue({})
    const context: any = {
      releaseDetail: {}, loadAll: jest.fn(),
      $confirm: jest.fn().mockResolvedValue(true), $message: { success: jest.fn() },
    }

    await methods.abandonRelease.call(context, { releaseId: 'release-1', releaseVersion: 1 })

    expect(api.abandonPublicRelease).toHaveBeenCalledWith('release-1')
    expect(context.releaseDetail).toBeNull()
    expect(context.$confirm).toHaveBeenCalled()
  })

  it('archives a base and reports that user access stopped', async() => {
    ;(api.archivePublicBase as jest.Mock).mockResolvedValue({})
    const context: any = {
      selectedBase: { kbId: 'kb-1' }, saving: false, loadAll: jest.fn(),
      $confirm: jest.fn().mockResolvedValue(true), $message: { success: jest.fn() },
    }

    await methods.archiveBase.call(context)

    expect(api.archivePublicBase).toHaveBeenCalledWith('kb-1')
    expect(context.$message.success).toHaveBeenCalledWith('知识库已撤下，用户端访问已停止')
  })

  it('refreshes documents while an index task is still running', () => {
    jest.useFakeTimers()
    const context: any = {
      selectedBase: { kbId: 'kb-1' }, documents: [{ status: 1 }],
      documentRefreshTimer: 0, selectBase: jest.fn(),
    }

    methods.scheduleDocumentRefresh.call(context)
    jest.advanceTimersByTime(3000)

    expect(context.selectBase).toHaveBeenCalledWith(context.selectedBase)
    jest.useRealTimers()
  })
})
