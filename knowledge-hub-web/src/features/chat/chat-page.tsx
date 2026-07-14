import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bot,
  FileText,
  MessageSquarePlus,
  Pencil,
  Send,
  Trash2,
} from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { getApiError } from '../../lib/api'
import { useAuthStore } from '../auth/auth-store'
import { getCollections, getDocuments } from '../workspace/workspace-api'
import {
  createChat,
  deleteChat,
  getMessages,
  listChats,
  renameChat,
  streamChat,
  type ChatMessage,
  type ChatScope,
  type ChatSession,
  type Citation,
} from './chat-api'

export function ChatPage() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [renaming, setRenaming] = useState<ChatSession | null>(null)
  const [deleting, setDeleting] = useState<ChatSession | null>(null)
  const [title, setTitle] = useState('')
  const sessions = useQuery({ queryKey: ['chats'], queryFn: listChats })
  const activeId = selectedId ?? sessions.data?.[0]?.id ?? null
  const active = sessions.data?.find((item) => item.id === activeId)

  const create = useMutation({
    mutationFn: createChat,
    onSuccess: (created) => {
      queryClient.setQueryData<ChatSession[]>(['chats'], (current = []) => [
        created,
        ...current,
      ])
      setSelectedId(created.id)
    },
  })
  const rename = useMutation({
    mutationFn: () => renameChat(renaming!.id, title),
    onSuccess: (updated) => {
      queryClient.setQueryData<ChatSession[]>(['chats'], (current = []) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setRenaming(null)
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteChat(deleting!.id),
    onSuccess: () => {
      const deletedId = deleting!.id
      queryClient.setQueryData<ChatSession[]>(['chats'], (current = []) =>
        current.filter((item) => item.id !== deletedId),
      )
      if (selectedId === deletedId) setSelectedId(null)
      queryClient.removeQueries({ queryKey: ['chat-messages', deletedId] })
      setDeleting(null)
    },
  })
  const error = sessions.error ?? create.error ?? rename.error ?? remove.error

  return (
    <section className="-mx-4 -my-7 min-h-[calc(100vh-4rem)] sm:-mx-7 md:my-[-2.25rem] lg:-mx-10">
      <header className="flex min-h-24 flex-col justify-between gap-4 border-b border-[var(--border)] px-5 py-5 sm:flex-row sm:items-center lg:px-7">
        <div>
          <p className="font-data text-[10px] tracking-[0.14em] text-[var(--accent-soft)]">
            GROUNDED CONVERSATIONS
          </p>
          <h1 className="font-heading mt-1 text-3xl font-semibold">Chat</h1>
        </div>
        <Button
          disabled={create.isPending}
          onClick={() => create.mutate()}
          type="button"
        >
          <MessageSquarePlus className="size-4" /> New conversation
        </Button>
      </header>
      {error ? (
        <p className="error-box m-5" role="alert">
          {getApiError(error, 'The conversation request failed.').message}
        </p>
      ) : null}
      <div className="grid min-h-[calc(100vh-10rem)] lg:grid-cols-[250px_minmax(0,1fr)]">
        <aside className="border-b border-[var(--border)] bg-[var(--surface-panel)] p-3 lg:border-b-0 lg:border-r">
          <p className="font-data px-2 py-3 text-[9px] tracking-[0.14em] text-[var(--text-faint)]">
            SAVED SESSIONS
          </p>
          <div className="flex gap-2 overflow-x-auto pb-2 lg:block lg:space-y-1 lg:overflow-visible">
            {sessions.isLoading ? (
              <p className="px-2 text-xs text-[var(--text-muted)]">
                Loading sessions...
              </p>
            ) : sessions.data?.length ? (
              sessions.data.map((item) => (
                <div
                  className={`group flex min-w-56 items-center rounded-md border lg:min-w-0 ${activeId === item.id ? 'border-[#49415f] bg-[var(--surface-raised)]' : 'border-transparent'}`}
                  key={item.id}
                >
                  <button
                    aria-label={`Open ${item.title}`}
                    className="min-w-0 flex-1 px-3 py-3 text-left"
                    onClick={() => setSelectedId(item.id)}
                    type="button"
                  >
                    <span className="block truncate text-xs font-semibold">
                      {item.title}
                    </span>
                    <span className="font-data mt-1 block text-[8px] text-[var(--text-faint)]">
                      {scopeLabel(item.scope)}
                    </span>
                  </button>
                  <button
                    aria-label={`Rename ${item.title}`}
                    className="icon-button size-8 shrink-0"
                    onClick={() => {
                      setRenaming(item)
                      setTitle(item.title)
                    }}
                    type="button"
                  >
                    <Pencil className="size-3.5" />
                  </button>
                  <button
                    aria-label={`Delete ${item.title}`}
                    className="icon-button mr-1 size-8 shrink-0"
                    onClick={() => setDeleting(item)}
                    type="button"
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                </div>
              ))
            ) : (
              <p className="px-2 text-xs leading-5 text-[var(--text-muted)]">
                Start a conversation to save grounded questions and answers.
              </p>
            )}
          </div>
        </aside>
        {active ? (
          <Conversation key={active.id} session={active} />
        ) : (
          <div className="grid min-h-96 place-items-center p-8 text-center">
            <div>
              <Bot className="mx-auto size-8 text-[var(--accent-soft)]" />
              <h2 className="font-heading mt-4 text-xl font-semibold">
                Ask from your sources
              </h2>
              <p className="mt-2 max-w-sm text-sm leading-6 text-[var(--text-muted)]">
                Create a conversation, choose its knowledge scope, and ask a
                question grounded in ready documents.
              </p>
            </div>
          </div>
        )}
      </div>
      {renaming ? (
        <Dialog label={`Rename ${renaming.title}`}>
          <h2 className="font-heading text-xl font-semibold">
            Rename conversation
          </h2>
          <label className="mt-5 block text-xs text-[var(--text-muted)]">
            Conversation title
            <input
              aria-label="Conversation title"
              className="field mt-2 w-full"
              maxLength={255}
              onChange={(event) => setTitle(event.target.value)}
              value={title}
            />
          </label>
          <div className="mt-6 flex justify-end gap-3">
            <Button
              onClick={() => setRenaming(null)}
              type="button"
              variant="ghost"
            >
              Cancel
            </Button>
            <Button
              disabled={!title.trim() || rename.isPending}
              onClick={() => rename.mutate()}
              type="button"
            >
              Save title
            </Button>
          </div>
        </Dialog>
      ) : null}
      {deleting ? (
        <Dialog label={`Delete ${deleting.title}`}>
          <h2 className="font-heading text-xl font-semibold">
            Delete conversation?
          </h2>
          <p className="mt-3 text-sm leading-6 text-[var(--text-muted)]">
            Messages and saved citations in this conversation will be
            permanently removed.
          </p>
          <div className="mt-6 flex justify-end gap-3">
            <Button
              onClick={() => setDeleting(null)}
              type="button"
              variant="ghost"
            >
              Cancel
            </Button>
            <Button
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
              type="button"
            >
              Delete conversation
            </Button>
          </div>
        </Dialog>
      ) : null}
    </section>
  )
}

function Conversation({ session }: { session: ChatSession }) {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore((state) => state.accessToken)
  const [scope, setScope] = useState<ChatScope>(session.scope)
  const [message, setMessage] = useState('')
  const [streamed, setStreamed] = useState('')
  const [pendingQuestion, setPendingQuestion] = useState('')
  const [pendingScope, setPendingScope] = useState<ChatScope>(session.scope)
  const [failedTurn, setFailedTurn] = useState<{
    question: string
    scope: ChatScope
    assistantMessageId: string | null
  } | null>(null)
  const [streamError, setStreamError] = useState('')
  const [sending, setSending] = useState(false)
  const [documentPage, setDocumentPage] = useState(0)
  const streamController = useRef<AbortController | null>(null)
  const messages = useQuery({
    queryKey: ['chat-messages', session.id],
    queryFn: () => getMessages(session.id),
    refetchInterval: (query) =>
      query.state.data?.some((item) =>
        ['PENDING', 'STREAMING'].includes(item.status),
      )
        ? 1_000
        : false,
  })
  const collections = useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  })
  const documents = useQuery({
    queryKey: ['documents', 'READY', 'chat-scope', documentPage],
    queryFn: () => getDocuments('READY', documentPage, 100),
  })
  const conversationActive = Boolean(
    messages.data?.some((item) =>
      ['PENDING', 'STREAMING'].includes(item.status),
    ),
  )
  const messagesReady = messages.isSuccess

  useEffect(
    () => () => {
      streamController.current?.abort()
    },
    [],
  )

  const failedTurnRecovered = Boolean(
    failedTurn?.assistantMessageId &&
    messages.data?.some(
      (item) =>
        item.id === failedTurn.assistantMessageId && item.status === 'COMPLETE',
    ),
  )
  const failedTurnActive = Boolean(
    failedTurn?.assistantMessageId &&
    messages.data?.some(
      (item) =>
        item.id === failedTurn.assistantMessageId &&
        ['PENDING', 'STREAMING'].includes(item.status),
    ),
  )
  const visibleStreamError = failedTurnRecovered ? '' : streamError
  const retryableFailedTurn =
    failedTurnRecovered || failedTurnActive ? null : failedTurn

  async function send(
    content: string,
    requestedScope = scope,
    existingAssistantMessageId: string | null = null,
  ) {
    if (
      !accessToken ||
      !content.trim() ||
      sending ||
      conversationActive ||
      !messagesReady ||
      !validScope(requestedScope)
    )
      return
    const question = content.trim()
    let startedAssistantMessageId: string | null = null
    const baselineAssistantIds = new Set(
      messages.data
        ?.filter((item) => item.role === 'ASSISTANT')
        .map((item) => item.id) ?? [],
    )
    setMessage('')
    setPendingQuestion(question)
    setPendingScope(requestedScope)
    setFailedTurn(null)
    setStreamed('')
    setStreamError('')
    setSending(true)
    const controller = new AbortController()
    streamController.current = controller
    try {
      await streamChat(
        session.id,
        question,
        requestedScope,
        accessToken,
        (event) => {
          if (event.event === 'started')
            startedAssistantMessageId = event.data.assistantMessageId
          if (event.event === 'delta')
            setStreamed((current) => current + event.data.text)
          if (event.event === 'completed') setStreamed(event.data.content)
          if (event.event === 'error') setStreamError(event.data.message)
        },
        controller.signal,
      )
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['chat-messages', session.id],
        }),
        queryClient.invalidateQueries({ queryKey: ['chats'] }),
      ])
      setPendingQuestion('')
      setStreamed('')
    } catch (error) {
      setStreamError(
        error instanceof Error
          ? error.message
          : 'The chat response was interrupted.',
      )
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['chat-messages', session.id],
        }),
        queryClient.invalidateQueries({ queryKey: ['chats'] }),
      ])
      const refreshedMessages = queryClient.getQueryData<ChatMessage[]>([
        'chat-messages',
        session.id,
      ])
      const persistedAssistantMessageId = [...(refreshedMessages ?? [])]
        .reverse()
        .find(
          (item) =>
            item.role === 'ASSISTANT' &&
            item.id !== existingAssistantMessageId &&
            !baselineAssistantIds.has(item.id),
        )?.id
      setFailedTurn({
        question,
        scope: requestedScope,
        assistantMessageId:
          startedAssistantMessageId ??
          persistedAssistantMessageId ??
          existingAssistantMessageId,
      })
      setPendingQuestion('')
      setStreamed('')
    } finally {
      if (streamController.current === controller)
        streamController.current = null
      setSending(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void send(message)
  }

  return (
    <div className="flex min-h-[620px] min-w-0 flex-col">
      <ScopeControls
        collections={collections.data ?? []}
        documentPage={documentPage}
        documents={documents.data?.items ?? []}
        disabled={sending || conversationActive || !messagesReady}
        onChange={setScope}
        onDocumentPageChange={setDocumentPage}
        scope={scope}
        totalDocumentPages={documents.data?.totalPages ?? 0}
      />
      <div
        aria-live="polite"
        className="flex-1 space-y-7 overflow-y-auto px-5 py-7 sm:px-8 lg:px-12"
      >
        {messages.isLoading ? (
          <p className="text-sm text-[var(--text-muted)]">
            Loading conversation...
          </p>
        ) : messages.error ? (
          <div className="error-box" role="alert">
            <p>
              {
                getApiError(
                  messages.error,
                  'Conversation history is unavailable.',
                ).message
              }
            </p>
            <Button
              className="mt-3"
              disabled={messages.isFetching}
              onClick={() => void messages.refetch()}
              type="button"
              variant="ghost"
            >
              Retry loading conversation
            </Button>
          </div>
        ) : messages.data?.length ? (
          messages.data.map((item) => <Message key={item.id} message={item} />)
        ) : !pendingQuestion ? (
          <div className="grid min-h-64 place-items-center text-center">
            <div>
              <Bot className="mx-auto size-7 text-[var(--accent-soft)]" />
              <h2 className="font-heading mt-4 text-lg font-semibold">
                Question the selected evidence
              </h2>
              <p className="mt-2 max-w-md text-sm leading-6 text-[var(--text-muted)]">
                Each answer retrieves fresh passages. If support is weak,
                Knowledge Hub will say so instead of filling the gap.
              </p>
            </div>
          </div>
        ) : null}
        {pendingQuestion ? (
          <Message
            message={{
              id: 'pending-user',
              role: 'USER',
              status: 'COMPLETE',
              content: pendingQuestion,
              scope: pendingScope,
              citations: [],
              createdAt: new Date().toISOString(),
            }}
          />
        ) : null}
        {streamed || sending ? (
          <Message
            message={{
              id: 'streaming-assistant',
              role: 'ASSISTANT',
              status: 'STREAMING',
              content: streamed,
              scope: pendingScope,
              citations: [],
              createdAt: new Date().toISOString(),
            }}
            streaming={sending}
          />
        ) : null}
        {visibleStreamError ? (
          <div
            className="rounded-md border border-red-900 bg-red-950/25 p-4"
            role="alert"
          >
            <p className="text-sm text-red-200">{visibleStreamError}</p>
            <p className="mt-2 text-xs text-[var(--text-muted)]">
              The turn may already be saved. Review the history before sending a
              new attempt.
            </p>
            {retryableFailedTurn ? (
              <Button
                className="mt-3"
                disabled={sending || !messagesReady}
                onClick={() => {
                  setScope(retryableFailedTurn.scope)
                  void send(
                    retryableFailedTurn.question,
                    retryableFailedTurn.scope,
                    retryableFailedTurn.assistantMessageId,
                  )
                }}
                type="button"
                variant="ghost"
              >
                Retry question
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
      <form
        className="border-t border-[var(--border)] bg-[var(--surface-panel)] p-4 sm:p-5"
        onSubmit={submit}
      >
        <label className="sr-only" htmlFor="chat-message">
          Message
        </label>
        <div className="flex items-end gap-3">
          <textarea
            aria-label="Message"
            className="min-h-12 max-h-36 flex-1 resize-y rounded-md border border-[var(--border-strong)] bg-[var(--surface-dark)] px-4 py-3 text-sm leading-6 outline-none placeholder:text-[var(--text-faint)] focus:border-[var(--accent-soft)]"
            disabled={sending || conversationActive || !messagesReady}
            id="chat-message"
            maxLength={4000}
            onChange={(event) => setMessage(event.target.value)}
            onKeyDown={(event) => {
              if (
                event.key === 'Enter' &&
                !event.shiftKey &&
                !event.nativeEvent.isComposing
              ) {
                event.preventDefault()
                event.currentTarget.form?.requestSubmit()
              }
            }}
            placeholder="Ask a question grounded in this scope..."
            value={message}
          />
          <Button
            aria-label="Send message"
            className="size-12 px-0"
            disabled={
              !message.trim() ||
              sending ||
              conversationActive ||
              !messagesReady ||
              !validScope(scope)
            }
            type="submit"
          >
            <Send className="size-4" />
          </Button>
        </div>
        <p className="font-data mt-2 text-[9px] text-[var(--text-faint)]">
          Enter to send · Shift+Enter for a new line · {message.length}/4000
        </p>
      </form>
    </div>
  )
}

function ScopeControls({
  scope,
  collections,
  documents,
  documentPage,
  totalDocumentPages,
  disabled,
  onChange,
  onDocumentPageChange,
}: {
  scope: ChatScope
  collections: Array<{ id: string; name: string }>
  documents: Array<{ id: string; filename: string }>
  documentPage: number
  totalDocumentPages: number
  disabled: boolean
  onChange: (scope: ChatScope) => void
  onDocumentPageChange: (page: number) => void
}) {
  return (
    <div className="grid gap-3 border-b border-[var(--border)] bg-[#0d0d14] px-5 py-4 sm:grid-cols-[180px_1fr] sm:px-8">
      <label className="text-[10px] text-[var(--text-muted)]">
        Knowledge scope
        <select
          aria-label="Knowledge scope"
          className="field mt-1 w-full"
          disabled={disabled}
          onChange={(event) =>
            onChange({
              type: event.target.value as ChatScope['type'],
              collectionId: null,
              documentIds: [],
            })
          }
          value={scope.type}
        >
          <option value="ALL">All ready documents</option>
          <option value="COLLECTION">One collection</option>
          <option value="DOCUMENTS">Selected documents</option>
        </select>
      </label>
      {scope.type === 'COLLECTION' ? (
        <label className="text-[10px] text-[var(--text-muted)]">
          Collection
          <select
            aria-label="Collection"
            className="field mt-1 w-full"
            disabled={disabled}
            onChange={(event) =>
              onChange({ ...scope, collectionId: event.target.value || null })
            }
            value={scope.collectionId ?? ''}
          >
            <option value="">Choose a collection</option>
            {collections.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      ) : scope.type === 'DOCUMENTS' ? (
        <div className="text-[10px] text-[var(--text-muted)]">
          <label>
            Documents
            <select
              aria-label="Documents"
              className="field mt-1 h-20 w-full py-2"
              disabled={disabled}
              multiple
              onChange={(event) => {
                const visibleIds = new Set(documents.map((item) => item.id))
                const retained = scope.documentIds.filter(
                  (id) => !visibleIds.has(id),
                )
                const selected = Array.from(
                  event.target.selectedOptions,
                  (option) => option.value,
                )
                onChange({
                  ...scope,
                  documentIds: [...retained, ...selected].slice(0, 50),
                })
              }}
              value={scope.documentIds}
            >
              {documents.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.filename}
                </option>
              ))}
            </select>
          </label>
          <span className="mt-1 flex items-center justify-between gap-2">
            <span>{scope.documentIds.length}/50 selected</span>
            {totalDocumentPages > 1 ? (
              <span className="flex items-center gap-1">
                <button
                  className="rounded px-2 py-1 hover:bg-[var(--surface-raised)] disabled:opacity-40"
                  disabled={documentPage === 0 || disabled}
                  onClick={() => onDocumentPageChange(documentPage - 1)}
                  type="button"
                >
                  Previous
                </button>
                <span className="font-data">
                  {documentPage + 1}/{totalDocumentPages}
                </span>
                <button
                  className="rounded px-2 py-1 hover:bg-[var(--surface-raised)] disabled:opacity-40"
                  disabled={documentPage + 1 >= totalDocumentPages || disabled}
                  onClick={() => onDocumentPageChange(documentPage + 1)}
                  type="button"
                >
                  Next
                </button>
              </span>
            ) : null}
          </span>
        </div>
      ) : (
        <div className="self-end pb-2 text-xs text-[var(--text-faint)]">
          Retrieval considers every ready document you own.
        </div>
      )}
    </div>
  )
}

function Message({
  message,
  streaming = false,
}: {
  message: ChatMessage
  streaming?: boolean
}) {
  const assistant = message.role === 'ASSISTANT'
  const content =
    message.status === 'FAILED' && !message.content
      ? 'This response could not be completed.'
      : ['PENDING', 'STREAMING'].includes(message.status) && !message.content
        ? 'Completing response...'
        : message.content
  return (
    <article className={assistant ? 'max-w-3xl' : 'ml-auto max-w-2xl'}>
      <p className="font-data mb-2 text-[9px] tracking-[0.12em] text-[var(--text-faint)]">
        {assistant ? 'KNOWLEDGE HUB' : 'YOU'}
      </p>
      <div
        className={
          assistant
            ? 'border-l-2 border-[var(--accent)] pl-5'
            : 'rounded-md bg-[var(--surface-raised)] px-4 py-3'
        }
      >
        <p className="whitespace-pre-wrap text-sm leading-7 text-[var(--text-primary)]">
          {content}
          {streaming || ['PENDING', 'STREAMING'].includes(message.status) ? (
            <span
              aria-label="Streaming response"
              className="ml-1 inline-block h-4 w-1 animate-pulse bg-[var(--accent-soft)]"
            />
          ) : null}
        </p>
        {message.citations.length ? (
          <ul aria-label="Sources" className="mt-4 flex flex-wrap gap-2">
            {message.citations.map((citation) => (
              <li key={`${message.id}-${citation.order}`}>
                <CitationChip citation={citation} />
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </article>
  )
}

function CitationChip({ citation }: { citation: Citation }) {
  const location = citation.pageNumber
    ? `p. ${citation.pageNumber}`
    : citation.section
      ? citation.section
      : citation.chunkPosition != null
        ? `passage ${citation.chunkPosition + 1}`
        : 'source'
  if (citation.sourceDeleted || !citation.documentId)
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-[var(--border)] px-3 py-1.5 text-[10px] text-[var(--text-faint)]">
        <Trash2 className="size-3" />
        {citation.sourceTitle} · Source deleted
      </span>
    )
  return (
    <Link
      className="inline-flex items-center gap-2 rounded-full border border-[#3a3458] bg-[#161321] px-3 py-1.5 font-data text-[10px] text-[var(--accent-soft)] hover:border-[var(--accent-soft)]"
      to={`/documents?documentId=${citation.documentId}&chunkId=${citation.chunkId ?? ''}`}
    >
      <FileText className="size-3" />
      {citation.sourceTitle} · {location}
    </Link>
  )
}

function Dialog({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div
      aria-label={label}
      aria-modal="true"
      className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4"
      role="dialog"
    >
      <section className="w-full max-w-md rounded-lg border border-[var(--border)] bg-[var(--surface-raised)] p-6">
        {children}
      </section>
    </div>
  )
}

function validScope(scope: ChatScope) {
  return (
    scope.type === 'ALL' ||
    (scope.type === 'COLLECTION' && Boolean(scope.collectionId)) ||
    (scope.type === 'DOCUMENTS' && scope.documentIds.length > 0)
  )
}

function scopeLabel(scope: ChatScope) {
  if (scope.type === 'COLLECTION') return 'COLLECTION SCOPE'
  if (scope.type === 'DOCUMENTS') return `${scope.documentIds.length} DOCUMENTS`
  return 'ALL READY DOCUMENTS'
}
