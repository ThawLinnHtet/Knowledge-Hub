import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MockAdapter from 'axios-mock-adapter'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { api } from '../../lib/api'
import { renderWorkspace } from '../../test/render-workspace'

const http = new MockAdapter(api)
const session = {
  id: 'chat-1',
  title: 'Research questions',
  scope: { type: 'ALL', collectionId: null, documentIds: [] },
  createdAt: '2026-07-12T10:00:00Z',
  updatedAt: '2026-07-12T10:00:00Z',
}

function sseResponse(events: Array<{ event: string; data: unknown }>) {
  const encoder = new TextEncoder()
  const body = new ReadableStream({
    start(controller) {
      events.forEach(({ event, data }) =>
        controller.enqueue(
          encoder.encode(`event:${event}\ndata:${JSON.stringify(data)}\n\n`),
        ),
      )
      controller.close()
    },
  })
  return { ok: true, body, json: async () => ({}) }
}

describe('chat workspace', () => {
  afterEach(() => {
    http.reset()
    vi.unstubAllGlobals()
  })

  it('creates, renames, revisits, and deletes saved sessions', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/chats').reply(200, [session])
    http.onGet('/api/v1/chats/chat-1/messages').reply(200, [])
    http.onPost('/api/v1/chats').reply(201, {
      ...session,
      id: 'chat-2',
      title: 'New conversation',
    })
    http.onGet('/api/v1/chats/chat-2/messages').reply(200, [])
    http.onPatch('/api/v1/chats/chat-1').reply(200, {
      ...session,
      title: 'Architecture review',
    })
    http.onDelete('/api/v1/chats/chat-1').reply(204)

    renderWorkspace('/chat')

    expect(
      await screen.findByRole('heading', { name: 'Chat' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Research questions')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New conversation' }))
    expect(
      await screen.findByRole('button', { name: 'Open New conversation' }),
    ).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: 'Open Research questions' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Rename Research questions' }),
    )
    const title = screen.getByLabelText('Conversation title')
    await user.clear(title)
    await user.type(title, 'Architecture review')
    await user.click(screen.getByRole('button', { name: 'Save title' }))
    await waitFor(() => expect(http.history.patch).toHaveLength(1))

    await user.click(
      screen.getByRole('button', { name: 'Delete Architecture review' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Delete conversation' }),
    )
    await waitFor(() => expect(http.history.delete).toHaveLength(1))
  })

  it('streams a scoped answer and renders persisted source citations', async () => {
    const user = userEvent.setup()
    const finalMessages = [
      {
        id: 'user-message',
        role: 'USER',
        status: 'COMPLETE',
        content: 'What is the retry policy?',
        scope: {
          type: 'COLLECTION',
          collectionId: 'collection-1',
          documentIds: [],
        },
        citations: [],
        createdAt: '2026-07-12T10:01:00Z',
      },
      {
        id: 'assistant-message',
        role: 'ASSISTANT',
        status: 'COMPLETE',
        content: 'Retries use bounded exponential backoff [S1].',
        scope: {
          type: 'COLLECTION',
          collectionId: 'collection-1',
          documentIds: [],
        },
        citations: [
          {
            order: 1,
            documentId: 'document-1',
            chunkId: 'chunk-1',
            sourceTitle: 'operations.md',
            pageNumber: null,
            section: 'Retries',
            chunkPosition: 3,
            relevanceScore: 0.94,
            sourceDeleted: false,
          },
        ],
        createdAt: '2026-07-12T10:01:01Z',
      },
    ]
    http.onGet('/api/v1/chats').reply(200, [session])
    http
      .onGet('/api/v1/chats/chat-1/messages')
      .replyOnce(200, [])
      .onGet('/api/v1/chats/chat-1/messages')
      .reply(200, finalMessages)
    http.onGet('/api/v1/collections').reply(200, [
      {
        id: 'collection-1',
        name: 'Operations',
        uncategorized: false,
        documentCount: 2,
      },
    ])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        { event: 'started', data: { assistantMessageId: 'assistant-message' } },
        {
          event: 'delta',
          data: {
            messageId: 'assistant-message',
            text: 'Retries use bounded ',
          },
        },
        {
          event: 'delta',
          data: {
            messageId: 'assistant-message',
            text: 'exponential backoff [S1].',
          },
        },
        {
          event: 'completed',
          data: {
            messageId: 'assistant-message',
            content: 'Retries use bounded exponential backoff [S1].',
            evidenceStatus: 'SUPPORTED',
          },
        },
      ]),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWorkspace('/chat')
    await screen.findByText('Research questions')
    await waitFor(() =>
      expect(screen.getByLabelText('Knowledge scope')).toBeEnabled(),
    )
    await user.selectOptions(
      screen.getByLabelText('Knowledge scope'),
      'COLLECTION',
    )
    await user.selectOptions(
      screen.getByLabelText('Collection'),
      'collection-1',
    )
    const composer = screen.getByLabelText('Message')
    await user.type(composer, 'First line{Shift>}{Enter}{/Shift}Second line')
    expect(composer).toHaveValue('First line\nSecond line')
    await user.clear(composer)
    await user.type(composer, 'What is the retry policy?{Enter}')

    expect(
      await screen.findByText('Retries use bounded exponential backoff [S1].'),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: /operations.md/i }),
    ).toHaveAttribute(
      'href',
      '/documents?documentId=document-1&chunkId=chunk-1',
    )
    const request = fetchMock.mock.calls[0]
    expect(JSON.parse(request[1].body)).toEqual({
      content: 'What is the retry policy?',
      scope: {
        type: 'COLLECTION',
        collectionId: 'collection-1',
        documentIds: [],
      },
    })
  })

  it('does not allow sending until conversation history is loaded', async () => {
    let releaseMessages!: () => void
    http.onGet('/api/v1/chats').reply(200, [session])
    http.onGet('/api/v1/chats/chat-1/messages').reply(
      () =>
        new Promise<[number, unknown[]]>((resolve) => {
          releaseMessages = () => resolve([200, []])
        }),
    )
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })

    renderWorkspace('/chat')
    await screen.findByText('Research questions')

    expect(screen.getByLabelText('Message')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()

    releaseMessages()
    await waitFor(() => expect(screen.getByLabelText('Message')).toBeEnabled())
  })

  it('allows conversation history to be reloaded after an error', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/chats').reply(200, [session])
    http
      .onGet('/api/v1/chats/chat-1/messages')
      .replyOnce(500)
      .onGet('/api/v1/chats/chat-1/messages')
      .reply(200, [])
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })

    renderWorkspace('/chat')

    await user.click(
      await screen.findByRole('button', {
        name: 'Retry loading conversation',
      }),
    )
    await waitFor(() => expect(screen.getByLabelText('Message')).toBeEnabled())
    expect(
      http.history.get.filter((request) => request.url?.endsWith('/messages')),
    ).toHaveLength(2)
  })

  it('shows recoverable stream failures and deleted historical sources', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/chats').reply(200, [session])
    http.onGet('/api/v1/chats/chat-1/messages').reply(200, [
      {
        id: 'assistant-old',
        role: 'ASSISTANT',
        status: 'COMPLETE',
        content: 'The archived decision is recorded [S1].',
        scope: { type: 'ALL', collectionId: null, documentIds: [] },
        citations: [
          {
            order: 1,
            documentId: null,
            chunkId: null,
            sourceTitle: 'archived.pdf',
            pageNumber: 4,
            section: null,
            chunkPosition: null,
            relevanceScore: 0.8,
            sourceDeleted: true,
          },
        ],
        createdAt: '2026-07-12T10:00:00Z',
      },
    ])
    http.onGet('/api/v1/collections').reply(200, [
      {
        id: 'collection-1',
        name: 'Operations',
        uncategorized: false,
        documentCount: 1,
      },
    ])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        {
          event: 'error',
          data: {
            code: 'PROVIDER_ERROR',
            message: 'The chat response could not be completed.',
            recoverable: true,
          },
        },
      ]),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWorkspace('/chat')
    expect(await screen.findByText(/Source deleted/)).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: /archived.pdf/i }),
    ).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Message'), 'Try this question')
    await user.click(screen.getByRole('button', { name: 'Send message' }))

    expect(
      await screen.findByText('The chat response could not be completed.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        'The turn may already be saved. Review the history before sending a new attempt.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Retry question' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Try this question')).not.toBeInTheDocument()

    await user.selectOptions(
      screen.getByLabelText('Knowledge scope'),
      'COLLECTION',
    )
    await user.selectOptions(
      screen.getByLabelText('Collection'),
      'collection-1',
    )
    await user.click(screen.getByRole('button', { name: 'Retry question' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({
      content: 'Try this question',
      scope: { type: 'ALL', collectionId: null, documentIds: [] },
    })
  })

  it('does not offer retry while the failed stream turn is still active', async () => {
    const user = userEvent.setup()
    const activeMessages = [
      {
        id: 'assistant-active',
        role: 'ASSISTANT',
        status: 'PENDING',
        content: '',
        scope: { type: 'ALL', collectionId: null, documentIds: [] },
        citations: [],
        createdAt: '2026-07-12T10:01:00Z',
      },
    ]
    http.onGet('/api/v1/chats').reply(200, [session])
    http
      .onGet('/api/v1/chats/chat-1/messages')
      .replyOnce(200, [])
      .onGet('/api/v1/chats/chat-1/messages')
      .reply(200, activeMessages)
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        sseResponse([
          {
            event: 'started',
            data: { assistantMessageId: 'assistant-active' },
          },
          {
            event: 'error',
            data: {
              code: 'PROVIDER_ERROR',
              message: 'The chat response could not be completed.',
              recoverable: true,
            },
          },
        ]),
      ),
    )

    renderWorkspace('/chat')
    await screen.findByText('Research questions')
    await waitFor(() => expect(screen.getByLabelText('Message')).toBeEnabled())
    await user.type(screen.getByLabelText('Message'), 'Try this question')
    await user.click(screen.getByRole('button', { name: 'Send message' }))

    expect(
      await screen.findByText('The chat response could not be completed.'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Retry question' }),
      ).not.toBeInTheDocument(),
    )
    expect(screen.getByLabelText('Message')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  })

  it('reconciles a retry that persists before its stream starts', async () => {
    const user = userEvent.setup()
    const failedMessages = [
      {
        id: 'assistant-failed',
        role: 'ASSISTANT',
        status: 'FAILED',
        content: '',
        scope: { type: 'ALL', collectionId: null, documentIds: [] },
        citations: [],
        createdAt: '2026-07-12T10:01:00Z',
      },
    ]
    const retriedMessages = [
      ...failedMessages,
      {
        ...failedMessages[0],
        id: 'assistant-older-uncached',
        status: 'COMPLETE',
        content: 'An older completed answer.',
        createdAt: '2026-07-12T10:01:30Z',
      },
      {
        ...failedMessages[0],
        id: 'assistant-retried',
        status: 'PENDING',
        createdAt: '2026-07-12T10:02:00Z',
      },
    ]
    http.onGet('/api/v1/chats').reply(200, [session])
    http
      .onGet('/api/v1/chats/chat-1/messages')
      .replyOnce(200, [])
      .onGet('/api/v1/chats/chat-1/messages')
      .replyOnce(200, failedMessages)
      .onGet('/api/v1/chats/chat-1/messages')
      .reply(200, retriedMessages)
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        sseResponse([
          {
            event: 'started',
            data: { assistantMessageId: 'assistant-failed' },
          },
          {
            event: 'error',
            data: {
              code: 'PROVIDER_ERROR',
              message: 'The chat response could not be completed.',
              recoverable: true,
            },
          },
        ]),
      )
      .mockResolvedValueOnce(
        sseResponse([
          {
            event: 'error',
            data: {
              code: 'PROVIDER_ERROR',
              message: 'The retry stream was interrupted.',
              recoverable: true,
            },
          },
        ]),
      )
    vi.stubGlobal('fetch', fetchMock)

    renderWorkspace('/chat')
    await screen.findByText('Research questions')
    await waitFor(() => expect(screen.getByLabelText('Message')).toBeEnabled())
    await user.type(screen.getByLabelText('Message'), 'Try this question')
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    await user.click(
      await screen.findByRole('button', { name: 'Retry question' }),
    )

    expect(
      await screen.findByText('The retry stream was interrupted.'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Retry question' }),
      ).not.toBeInTheDocument(),
    )
    expect(screen.getByLabelText('Message')).toBeDisabled()
  })
})
