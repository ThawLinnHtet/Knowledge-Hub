import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MockAdapter from 'axios-mock-adapter'
import { afterEach, describe, expect, it } from 'vitest'

import { api } from '../../lib/api'
import { renderWorkspace } from '../../test/render-workspace'

const http = new MockAdapter(api)

const document = {
  id: 'document-1',
  filename: 'research-notes.pdf',
  status: 'FAILED',
  collection: { id: 'collection-1', name: 'Research' },
  mediaType: 'application/pdf',
  fileExtension: 'pdf',
  sizeBytes: 2048,
  uploadedAt: '2026-07-12T10:00:00Z',
  failureCode: 'EXTRACTION_FAILED',
  failureMessage: 'The document text could not be extracted.',
  retryCount: 1,
  retryable: true,
  nextRetryAt: null,
  processingStartedAt: null,
  processedAt: null,
  updatedAt: '2026-07-12T10:01:00Z',
}

describe('documents workspace', () => {
  afterEach(() => http.reset())

  it('shows the dashboard navigation, document status, detail diagnostics, and deletion', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/documents').reply(200, {
      items: [document],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    http.onGet('/api/v1/collections').reply(200, [])
    http.onGet('/api/v1/documents/document-1').reply(200, {
      ...document,
      chunks: [{ id: 'chunk-1', chunkOrder: 0, snippet: 'A useful passage.' }],
      citations: [],
    })
    http.onDelete('/api/v1/documents/document-1').reply(204)

    renderWorkspace()

    expect(
      await screen.findByRole('heading', { name: 'Documents' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Workspace' }),
    ).toHaveTextContent('Collections')
    expect(await screen.findByText('research-notes.pdf')).toBeInTheDocument()
    expect(screen.getAllByText('Failed')).not.toHaveLength(0)

    await user.click(
      screen.getByRole('button', { name: 'View research-notes.pdf' }),
    )
    expect(
      await screen.findByText('The document text could not be extracted.'),
    ).toBeInTheDocument()
    expect(screen.getByText('A useful passage.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete document' }))
    await user.click(screen.getByRole('button', { name: 'Delete permanently' }))
    await waitFor(() => expect(http.history.delete).toHaveLength(1))
  })

  it('asks for a duplicate decision and uploads the selected batch manifest', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    http.onGet('/api/v1/collections').reply(200, [])
    http.onPost('/api/v1/documents/uploads/preflight').reply(200, {
      items: [
        {
          filename: 'notes.txt',
          status: 'DUPLICATE',
          sizeBytes: 5,
          detectedMediaType: 'text/plain',
          sha256Hash: 'hash',
          confirmationToken: 'duplicate-token',
          error: null,
        },
      ],
    })
    http.onPost('/api/v1/documents/uploads').reply(200, {
      items: [{ fileIndex: 0, filename: 'notes.txt', status: 'UPLOADED' }],
    })

    renderWorkspace()
    const input = await screen.findByLabelText('Choose documents')
    await user.upload(
      input,
      new File(['notes'], 'notes.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Review upload' }))

    expect(
      await screen.findByText('Already in your library'),
    ).toBeInTheDocument()
    await user.click(screen.getByLabelText('Upload another copy'))
    await user.click(screen.getByRole('button', { name: 'Upload 1 document' }))

    expect(await screen.findByText('1 document uploaded')).toBeInTheDocument()
    expect(screen.getByLabelText('Choose documents')).not.toBe(input)
    const request = http.history.post.find(
      (item) => item.url === '/api/v1/documents/uploads',
    )
    const manifestPart = (request?.data as FormData).get('manifest') as File
    const manifestText = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onerror = () => reject(reader.error)
      reader.onload = () => resolve(String(reader.result))
      reader.readAsText(manifestPart)
    })
    const manifest = JSON.parse(manifestText)
    expect(manifest.items).toEqual([
      {
        fileIndex: 0,
        decision: 'UPLOAD_DUPLICATE',
        confirmationToken: 'duplicate-token',
      },
    ])
  })

  it('can start over when every reviewed duplicate is skipped', async () => {
    const user = userEvent.setup()
    http.onGet('/api/v1/documents').reply(200, {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    http.onGet('/api/v1/collections').reply(200, [])
    http.onPost('/api/v1/documents/uploads/preflight').reply(200, {
      items: [
        {
          filename: 'notes.txt',
          status: 'DUPLICATE',
          sizeBytes: 5,
          detectedMediaType: 'text/plain',
          sha256Hash: 'hash',
          confirmationToken: 'duplicate-token',
          error: null,
        },
      ],
    })

    renderWorkspace()
    const input = await screen.findByLabelText('Choose documents')
    await user.upload(
      input,
      new File(['notes'], 'notes.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Review upload' }))

    expect(
      await screen.findByText('Already in your library'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Upload 0 documents' }),
    ).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Start over' }))

    expect(
      screen.queryByText('Already in your library'),
    ).not.toBeInTheDocument()
    expect(screen.getByLabelText('Choose documents')).toBeEnabled()
  })
})
