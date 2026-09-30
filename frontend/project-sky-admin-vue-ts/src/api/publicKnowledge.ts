import request from '@/utils/request'

const root = '/agent/public-knowledge'

export const listPublicBases = () => request({ url: `${root}/bases`, method: 'get' })
export const createPublicBase = (data: any) => request({ url: `${root}/bases`, method: 'post', data })
export const updatePublicBase = (kbId: string, data: any) =>
  request({ url: `${root}/bases/${kbId}`, method: 'put', data })
export const archivePublicBase = (kbId: string) =>
  request({ url: `${root}/bases/${kbId}/archive`, method: 'post' })
export const purgePublicBase = (kbId: string, confirmationName: string) =>
  request({ url: `${root}/bases/${kbId}/purge`, method: 'post', data: { confirmation_name: confirmationName } })
export const listPublicDocuments = (kbId: string) =>
  request({ url: `${root}/bases/${kbId}/documents`, method: 'get' })
export const uploadPublicDocument = (kbId: string, file: File, category: string) => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', category)
  return request({ url: `${root}/bases/${kbId}/documents`, method: 'post', data })
}
export const uploadPublicDocumentVersion = (documentId: string, file: File, category: string) => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', category)
  return request({ url: `${root}/documents/${documentId}/versions`, method: 'post', data })
}
export const reindexPublicDocument = (documentId: string) =>
  request({ url: `${root}/documents/${documentId}/reindex`, method: 'post' })
export const deletePublicDocument = (documentId: string) =>
  request({ url: `${root}/documents/${documentId}`, method: 'delete' })

export const listPublicReleases = () => request({ url: `${root}/releases`, method: 'get' })
export const getPublicRelease = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}`, method: 'get' })
export const createPublicRelease = (kbId: string) =>
  request({ url: `${root}/releases`, method: 'post', data: { kb_id: kbId, release_version: 0 } })
export const bindPublicReleaseDocument = (releaseId: string, document: any) =>
  request({
    url: `${root}/releases/${releaseId}/documents`, method: 'post',
    data: { document_id: document.documentId, document_version: document.version, category: document.category },
  })
export const unbindPublicReleaseDocument = (releaseId: string, documentId: string, version: number) =>
  request({ url: `${root}/releases/${releaseId}/documents/${documentId}/versions/${version}`, method: 'delete' })
export const abandonPublicRelease = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}/abandon`, method: 'post' })
export const reviewPublicRelease = (releaseId: string, approved: boolean, comment = '') =>
  request({ url: `${root}/releases/${releaseId}/review`, method: 'post', data: { approved, comment } })
export const publishPublicRelease = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}/publish`, method: 'post' })
export const offlinePublicRelease = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}/offline`, method: 'post' })
export const rollbackPublicRelease = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}/rollback`, method: 'post' })
export const bindPublicScene = (releaseId: string) =>
  request({ url: `${root}/releases/${releaseId}/bindings`, method: 'post', data: { scene: 'USER_CHAT' } })
