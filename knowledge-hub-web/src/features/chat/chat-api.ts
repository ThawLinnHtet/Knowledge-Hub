import {
  api,
  failApiAuthentication,
  refreshApiAccessToken,
} from '../../lib/api'

export type ScopeType = 'ALL' | 'COLLECTION' | 'DOCUMENTS'

export interface ChatScope {
  type: ScopeType
  collectionId: string | null
  documentIds: string[]
}

export interface ChatSession {
  id: string
  title: string
  scope: ChatScope
  createdAt: string
  updatedAt: string
}

export interface Citation {
  order: number
  documentId: string | null
  chunkId: string | null
  sourceTitle: string
  pageNumber: number | null
  section: string | null
  chunkPosition: number | null
  relevanceScore: number
  sourceDeleted: boolean
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  status: 'PENDING' | 'STREAMING' | 'COMPLETE' | 'FAILED'
  content: string
  scope: ChatScope
  citations: Citation[]
  createdAt: string
}

export type StreamEvent =
  | { event: 'started'; data: { assistantMessageId: string } }
  | { event: 'delta'; data: { messageId: string; text: string } }
  | {
      event: 'completed'
      data: { messageId: string; content: string; evidenceStatus: string }
    }
  | {
      event: 'error'
      data: { code: string; message: string; recoverable: boolean }
    }

export async function listChats() {
  return (await api.get<ChatSession[]>('/api/v1/chats')).data
}

export async function createChat() {
  return (
    await api.post<ChatSession>('/api/v1/chats', {
      title: 'New conversation',
      scope: { type: 'ALL', collectionId: null, documentIds: [] },
    })
  ).data
}

export async function renameChat(id: string, title: string) {
  return (await api.patch<ChatSession>(`/api/v1/chats/${id}`, { title })).data
}

export async function deleteChat(id: string) {
  await api.delete(`/api/v1/chats/${id}`)
}

export async function getMessages(id: string) {
  return (await api.get<ChatMessage[]>(`/api/v1/chats/${id}/messages`)).data
}

export async function streamChat(
  id: string,
  content: string,
  scope: ChatScope,
  accessToken: string,
  onEvent: (event: StreamEvent) => void,
  signal?: AbortSignal,
) {
  let token = accessToken
  let response = await requestStream(id, content, scope, token, signal)
  if (response.status === 401) {
    token = await refreshApiAccessToken()
    response = await requestStream(id, content, scope, token, signal)
    if (response.status === 401) failApiAuthentication()
  }
  if (!response.ok || !response.body) {
    const error = (await response.json().catch(() => null)) as {
      message?: string
    } | null
    throw new Error(error?.message ?? 'The chat stream could not be started.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = false
  let terminalError: Error | null = null
  const handleEvent = (event: StreamEvent) => {
    if (event.event === 'completed' || event.event === 'error') terminal = true
    if (event.event === 'error') terminalError = new Error(event.data.message)
    onEvent(event)
  }
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    let boundary = findFrameBoundary(buffer)
    while (boundary) {
      const frame = buffer.slice(0, boundary.index)
      buffer = buffer.slice(boundary.index + boundary.length)
      parseFrame(frame, handleEvent)
      if (terminal) {
        try {
          await reader.cancel()
        } catch {
          // Preserve the server's terminal error when stream cancellation also fails.
        }
        if (terminalError) throw terminalError
        return
      }
      boundary = findFrameBoundary(buffer)
    }
    if (done) break
  }
  if (buffer.trim()) parseFrame(buffer, handleEvent)
  if (terminalError) throw terminalError
  if (!terminal)
    throw new Error('The chat response was interrupted before completion.')
}

function requestStream(
  id: string,
  content: string,
  scope: ChatScope,
  token: string,
  signal?: AbortSignal,
) {
  return fetch(
    `${api.defaults.baseURL ?? ''}/api/v1/chats/${id}/messages:stream`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content, scope }),
      signal,
    },
  )
}

function parseFrame(frame: string, onEvent: (event: StreamEvent) => void) {
  let name = ''
  const data: string[] = []
  for (const line of frame.split(/\r\n|\r|\n/)) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (!name || !data.length) return
  if (!['started', 'delta', 'completed', 'error'].includes(name)) return
  onEvent({ event: name, data: JSON.parse(data.join('\n')) } as StreamEvent)
}

function findFrameBoundary(buffer: string) {
  const match = /\r\n\r\n|\n\n|\r\r/.exec(buffer)
  return match ? { index: match.index, length: match[0].length } : null
}
