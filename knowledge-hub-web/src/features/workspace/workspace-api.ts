import { api } from '../../lib/api'

export interface Collection {
  id: string
  name: string
  uncategorized: boolean
  documentCount: number
  createdAt?: string
  updatedAt?: string
}

export interface DocumentSummary {
  id: string
  filename: string
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  collection: { id: string; name: string }
  mediaType: string
  fileExtension: string
  sizeBytes: number
  uploadedAt: string
  failureCode: string | null
  failureMessage: string | null
  retryCount: number
  retryable: boolean
  nextRetryAt: string | null
  processingStartedAt: string | null
  processedAt: string | null
  updatedAt: string
}

export interface DocumentDetail extends DocumentSummary {
  chunks: Array<{
    id: string
    chunkOrder: number
    snippet: string
    pageNumber?: number
    section?: string
  }>
  citations: Array<{
    id: string
    sourceTitle: string
    pageNumber?: number
    section?: string
    relevanceScore: number
    sourceDeleted: boolean
  }>
}

export interface PreflightItem {
  filename: string
  status: 'ACCEPTED' | 'DUPLICATE' | 'REJECTED'
  sizeBytes: number
  confirmationToken: string | null
  error: { code: string; message: string } | null
}

export interface UploadResult {
  fileIndex: number
  filename: string
  status: 'UPLOADED' | 'SKIPPED' | 'REJECTED'
  error?: { code: string; message: string } | null
}

export async function getCollections() {
  return (await api.get<Collection[]>('/api/v1/collections')).data
}

export async function getDocuments(status?: string, page = 0, size = 20) {
  return (
    await api.get<{
      items: DocumentSummary[]
      page: number
      totalPages: number
      totalElements: number
    }>('/api/v1/documents', {
      params: { page, size, ...(status ? { status } : {}) },
    })
  ).data
}

export async function getDocument(id: string) {
  return (await api.get<DocumentDetail>(`/api/v1/documents/${id}`)).data
}

export async function preflightUpload(files: File[], collectionId?: string) {
  const body = new FormData()
  files.forEach((file) => body.append('files', file))
  if (collectionId) body.append('collectionId', collectionId)
  return (
    await api.post<{ items: PreflightItem[] }>(
      '/api/v1/documents/uploads/preflight',
      body,
    )
  ).data.items
}

export async function uploadDocuments(
  files: File[],
  items: Array<{
    fileIndex: number
    decision: 'UPLOAD' | 'UPLOAD_DUPLICATE' | 'SKIP'
    confirmationToken?: string
  }>,
  collectionId?: string,
) {
  const body = new FormData()
  files.forEach((file) => body.append('files', file))
  body.append(
    'manifest',
    new Blob([JSON.stringify({ items })], { type: 'application/json' }),
  )
  if (collectionId) body.append('collectionId', collectionId)
  return (
    await api.post<{ items: UploadResult[] }>('/api/v1/documents/uploads', body)
  ).data
}
