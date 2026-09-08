import request from '@/utils/request'
export const listKnowledgeBases = () => request({ url: '/agent/knowledge-bases', method: 'get' })
export const createKnowledgeBase = (data: any) => request({ url: '/agent/knowledge-bases', method: 'post', data })
export const updateKnowledgeBase = (kbId: string, data: any) =>
  request({ url: `/agent/knowledge-bases/${kbId}`, method: 'put', data })
export const deleteKnowledgeBase = (kbId: string) =>
  request({ url: `/agent/knowledge-bases/${kbId}`, method: 'delete' })
export const listDocuments = (kbId: string) =>
  request({ url: `/agent/knowledge-bases/${kbId}/documents`, method: 'get' })
export const uploadDocument = (kbId: string, file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `/agent/knowledge-bases/${kbId}/documents`,
    method: 'post',
    data,
  })
}
export const uploadDocumentVersion = (documentId: string, file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `/agent/documents/${documentId}/versions`,
    method: 'post',
    data,
  })
}
export const reindexDocument = (documentId: string) =>
  request({ url: `/agent/documents/${documentId}/reindex`, method: 'post' })
export const deleteDocument = (documentId: string) =>
  request({ url: `/agent/documents/${documentId}`, method: 'delete' })
