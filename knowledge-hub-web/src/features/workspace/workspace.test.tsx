import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MockAdapter from 'axios-mock-adapter'
import { afterEach, describe, expect, it } from 'vitest'

import { api } from '../../lib/api'
import { renderWorkspace } from '../../test/render-workspace'

const http = new MockAdapter(api)

describe('workspace pages', () => {
  afterEach(() => http.reset())

  it('creates, renames, and deletes collections while protecting Uncategorized', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/collections').reply(200, [
      {
        id: 'uncategorized',
        name: 'Uncategorized',
        uncategorized: true,
        documentCount: 2,
      },
      {
        id: 'research',
        name: 'Research',
        uncategorized: false,
        documentCount: 4,
      },
    ])
    http.onPost('/api/v1/collections').reply(201, {
      id: 'new',
      name: 'Product',
      uncategorized: false,
      documentCount: 0,
    })
    http.onPatch('/api/v1/collections/research').reply(200, {
      id: 'research',
      name: 'Deep research',
      uncategorized: false,
      documentCount: 4,
    })
    http.onDelete('/api/v1/collections/research').reply(204)

    renderWorkspace('/collections')
    expect(
      await screen.findByRole('heading', { name: 'Collections' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Delete Uncategorized' }),
    ).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Collection name'), 'Product')
    await user.click(screen.getByRole('button', { name: 'Create collection' }))
    await waitFor(() => expect(http.history.post).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: 'Rename Research' }))
    const rename = screen.getByLabelText('New collection name')
    await user.clear(rename)
    await user.type(rename, 'Deep research')
    await user.click(screen.getByRole('button', { name: 'Save name' }))
    await waitFor(() => expect(http.history.patch).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: 'Delete Research' }))
    await user.click(screen.getByRole('button', { name: 'Delete collection' }))
    await waitFor(() => expect(http.history.delete).toHaveLength(1))
  })

  it('searches with mode and file filters and renders source metadata', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    http.onGet('/api/v1/search').reply(200, {
      mode: 'HYBRID',
      items: [
        {
          chunkId: 'chunk-1',
          documentId: 'document-1',
          filename: 'architecture.md',
          collection: { id: 'collection-1', name: 'Engineering' },
          fileExtension: 'md',
          uploadedAt: '2026-07-12T10:00:00Z',
          chunkOrder: 2,
          snippet: 'Use bounded hybrid retrieval for grounded answers.',
          section: 'Retrieval',
          score: 0.92,
          matchType: 'HYBRID',
        },
      ],
    })

    renderWorkspace('/search')
    await user.type(
      screen.getByLabelText('Search your library'),
      'grounded answers',
    )
    await user.selectOptions(screen.getByLabelText('Search mode'), 'HYBRID')
    await user.selectOptions(screen.getByLabelText('File type'), 'md')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('architecture.md')).toBeInTheDocument()
    expect(
      screen.getByText('Use bounded hybrid retrieval for grounded answers.'),
    ).toBeInTheDocument()
    const searchRequest = http.history.get.find(
      (request) => request.url === '/api/v1/search',
    )
    expect(searchRequest?.params).toMatchObject({
      q: 'grounded answers',
      mode: 'HYBRID',
      fileExtension: 'md',
    })
  })

  it('shows read-only model and limit status in settings', async () => {
    http.onGet('/api/v1/settings/system').reply(200, {
      fakeAi: true,
      chatModel: 'google/gemini-2.5-flash-lite',
      embeddingModel: 'nvidia/llama-nemotron-embed-vl-1b-v2:free',
      embeddingDimension: 1024,
      maxUploadSizeBytes: 52428800,
      maxFilesPerBatch: 20,
      maxRetrievedChunks: 10,
      maxChatMessageCharacters: 4000,
    })

    renderWorkspace('/settings')

    expect(
      await screen.findByRole('heading', { name: 'Settings' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('google/gemini-2.5-flash-lite'),
    ).toBeInTheDocument()
    expect(screen.getByText('50 MB')).toBeInTheDocument()
    expect(
      screen.queryByRole('textbox', { name: /model/i }),
    ).not.toBeInTheDocument()
  })

  it('submits the backend account deletion confirmation contract', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/settings/system').reply(200, {
      fakeAi: true,
      chatModel: 'chat-model',
      embeddingModel: 'embedding-model',
      embeddingDimension: 1024,
      maxUploadSizeBytes: 52428800,
      maxFilesPerBatch: 20,
      maxRetrievedChunks: 10,
      maxChatMessageCharacters: 4000,
    })
    http.onDelete('/api/v1/account').reply(202)

    renderWorkspace('/settings')
    await user.click(
      await screen.findByRole('button', { name: 'Start account deletion' }),
    )
    await user.type(screen.getByLabelText('Password'), 'correct-password')
    await user.type(screen.getByLabelText('Confirmation'), 'DELETE')
    await user.click(screen.getByRole('button', { name: 'Delete my account' }))

    await waitFor(() => expect(http.history.delete).toHaveLength(1))
    expect(JSON.parse(http.history.delete[0].data as string)).toEqual({
      password: 'correct-password',
      confirmation: 'DELETE',
    })
  })
})
