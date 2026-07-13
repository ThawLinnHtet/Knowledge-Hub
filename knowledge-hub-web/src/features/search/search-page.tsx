import { useQuery } from '@tanstack/react-query'
import { FileSearch, Search } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { api, getApiError } from '../../lib/api'
import { getCollections, getDocuments } from '../workspace/workspace-api'
import { PageHeader } from '../workspace/workspace-shell'

interface SearchItem {
  chunkId: string
  documentId: string
  filename: string
  collection: { id: string; name: string }
  fileExtension: string
  uploadedAt: string
  snippet: string
  pageNumber?: number
  section?: string
  score: number
  matchType: string
}

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') ?? '')
  const [mode, setMode] = useState(params.get('mode') ?? 'HYBRID')
  const [fileExtension, setFileExtension] = useState(
    params.get('fileExtension') ?? '',
  )
  const [collectionId, setCollectionId] = useState(
    params.get('collectionId') ?? '',
  )
  const [uploadedFrom, setUploadedFrom] = useState(
    toLocalDate(params.get('uploadedFrom')),
  )
  const [uploadedTo, setUploadedTo] = useState(
    toLocalDate(params.get('uploadedTo')),
  )
  const [documentIds, setDocumentIds] = useState(params.getAll('documentId'))
  const [documentPage, setDocumentPage] = useState(0)
  const collections = useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  })
  const documents = useQuery({
    queryKey: ['documents', 'READY', documentPage],
    queryFn: () => getDocuments('READY', documentPage, 100),
  })
  const activeQuery = params.get('q') ?? ''
  const results = useQuery({
    queryKey: ['search', params.toString()],
    queryFn: async () =>
      (
        await api.get<{ items: SearchItem[] }>('/api/v1/search', {
          params: {
            q: activeQuery,
            mode: params.get('mode') ?? 'HYBRID',
            ...(params.get('fileExtension')
              ? { fileExtension: params.get('fileExtension') }
              : {}),
            ...(params.get('collectionId')
              ? { collectionId: params.get('collectionId') }
              : {}),
            ...(params.get('uploadedFrom')
              ? { uploadedFrom: params.get('uploadedFrom') }
              : {}),
            ...(params.get('uploadedTo')
              ? { uploadedTo: params.get('uploadedTo') }
              : {}),
            ...(params.getAll('documentId').length
              ? { documentId: params.getAll('documentId') }
              : {}),
          },
          paramsSerializer: { indexes: null },
        })
      ).data.items,
    enabled: Boolean(activeQuery.trim()),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const next = new URLSearchParams({ q: query.trim(), mode })
    if (fileExtension) next.set('fileExtension', fileExtension)
    if (collectionId) next.set('collectionId', collectionId)
    if (uploadedFrom)
      next.set('uploadedFrom', localBoundary(uploadedFrom, false))
    if (uploadedTo) next.set('uploadedTo', localBoundary(uploadedTo, true))
    documentIds.forEach((id) => next.append('documentId', id))
    if (query.trim()) setParams(next)
  }

  return (
    <>
      <PageHeader
        eyebrow="RETRIEVAL / HYBRID INDEX"
        title="Search"
        description="Find exact terms and semantically related passages across every ready source in your private library."
      />
      <form
        className="rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5"
        onSubmit={submit}
      >
        <label className="block">
          <span className="mb-2 block text-xs font-medium text-[var(--text-muted)]">
            Search your library
          </span>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--text-faint)]" />
            <input
              aria-label="Search your library"
              className="field w-full pl-10"
              maxLength={500}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Ask with a phrase, topic, or exact term"
              value={query}
            />
          </div>
        </label>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <Filter label="Search mode" onChange={setMode} value={mode}>
            <option value="HYBRID">Hybrid</option>
            <option value="KEYWORD">Keyword</option>
            <option value="SEMANTIC">Semantic</option>
          </Filter>
          <Filter
            label="File type"
            onChange={setFileExtension}
            value={fileExtension}
          >
            <option value="">All types</option>
            <option value="pdf">PDF</option>
            <option value="docx">DOCX</option>
            <option value="txt">TXT</option>
            <option value="md">Markdown</option>
          </Filter>
          <Filter
            label="Collection"
            onChange={setCollectionId}
            value={collectionId}
          >
            <option value="">All collections</option>
            {collections.data?.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Filter>
        </div>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <DateFilter
            label="Uploaded from"
            onChange={setUploadedFrom}
            value={uploadedFrom}
          />
          <DateFilter
            label="Uploaded to"
            onChange={setUploadedTo}
            value={uploadedTo}
          />
        </div>
        <label className="mt-3 block">
          <span className="mb-2 block text-xs text-[var(--text-muted)]">
            Specific documents
          </span>
          <select
            aria-label="Specific documents"
            className="field h-auto min-h-11 w-full py-2"
            multiple
            onChange={(event) => {
              const visibleIds = new Set(
                documents.data?.items.map((item) => item.id),
              )
              const retained = documentIds.filter((id) => !visibleIds.has(id))
              const selected = Array.from(
                event.target.selectedOptions,
                (option) => option.value,
              )
              setDocumentIds([...retained, ...selected])
            }}
            value={documentIds}
          >
            {documents.data?.items.map((item) => (
              <option key={item.id} value={item.id}>
                {item.filename}
              </option>
            ))}
          </select>
          <span className="mt-1 block text-[10px] text-[var(--text-faint)]">
            Leave empty for every ready document. {documentIds.length} selected.
          </span>
          {documents.data && documents.data.totalPages > 1 ? (
            <span className="mt-2 flex items-center gap-2">
              <Button
                disabled={documentPage === 0}
                onClick={() => setDocumentPage((value) => value - 1)}
                type="button"
                variant="ghost"
              >
                Previous documents
              </Button>
              <span className="font-data text-[10px] text-[var(--text-faint)]">
                {documentPage + 1} / {documents.data.totalPages}
              </span>
              <Button
                disabled={documentPage + 1 >= documents.data.totalPages}
                onClick={() => setDocumentPage((value) => value + 1)}
                type="button"
                variant="ghost"
              >
                Next documents
              </Button>
            </span>
          ) : null}
        </label>
        <Button className="mt-4" disabled={!query.trim()} type="submit">
          Search
        </Button>
      </form>
      <section aria-live="polite" className="mt-8">
        {results.error ? (
          <p className="error-box" role="alert">
            {getApiError(results.error, 'Search failed.').message}
          </p>
        ) : null}
        {results.isFetching ? (
          <p className="text-sm text-[var(--text-muted)]">
            Searching keyword and vector indexes...
          </p>
        ) : null}
        {!activeQuery ? (
          <Empty text="Enter a query to search your ready documents." />
        ) : !results.isFetching && results.data?.length === 0 ? (
          <Empty text="No passages matched this search. Try a broader query or fewer filters." />
        ) : (
          <div className="space-y-4">
            {results.data?.map((item) => (
              <article
                className="rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5"
                key={item.chunkId}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <Link
                    className="font-heading font-semibold hover:text-[var(--accent-soft)]"
                    to={`/documents?documentId=${item.documentId}`}
                  >
                    {item.filename}
                  </Link>
                  <span className="font-data text-[10px] text-[var(--accent-soft)]">
                    {Math.round(item.score * 100)}% · {item.matchType}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-7 text-[var(--text-muted)]">
                  {item.snippet}
                </p>
                <p className="font-data mt-4 text-[10px] uppercase tracking-wider text-[var(--text-faint)]">
                  {item.collection.name}
                  {item.section ? ` / ${item.section}` : ''}
                  {item.pageNumber ? ` / Page ${item.pageNumber}` : ''}
                </p>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  )
}

export function SearchRoute() {
  const [params] = useSearchParams()
  return <SearchPage key={params.toString()} />
}

function Filter({
  label,
  value,
  onChange,
  children,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  children: React.ReactNode
}) {
  return (
    <label>
      <span className="mb-2 block text-xs text-[var(--text-muted)]">
        {label}
      </span>
      <select
        aria-label={label}
        className="field w-full"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        {children}
      </select>
    </label>
  )
}
function DateFilter({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label>
      <span className="mb-2 block text-xs text-[var(--text-muted)]">
        {label}
      </span>
      <input
        aria-label={label}
        className="field w-full"
        onChange={(event) => onChange(event.target.value)}
        type="date"
        value={value}
      />
    </label>
  )
}
function localBoundary(date: string, endOfDay: boolean) {
  const value = new Date(`${date}T00:00:00`)
  if (endOfDay) value.setDate(value.getDate() + 1)
  if (endOfDay) value.setMilliseconds(-1)
  return value.toISOString()
}
function toLocalDate(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
function Empty({ text }: { text: string }) {
  return (
    <div className="grid min-h-52 place-items-center rounded-lg border border-dashed border-[var(--border)] text-center">
      <div>
        <FileSearch className="mx-auto mb-3 size-7 text-[var(--text-faint)]" />
        <p className="text-sm text-[var(--text-muted)]">{text}</p>
      </div>
    </div>
  )
}
