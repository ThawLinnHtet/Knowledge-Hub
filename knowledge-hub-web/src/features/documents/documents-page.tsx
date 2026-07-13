import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, LoaderCircle, Upload, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { getApiError, api } from '../../lib/api'
import { cn } from '../../lib/utils'
import { PageHeader } from '../workspace/workspace-shell'
import {
  getCollections,
  getDocument,
  getDocuments,
  preflightUpload,
  uploadDocuments,
  type DocumentSummary,
  type PreflightItem,
  type UploadResult,
} from '../workspace/workspace-api'

const statuses = ['', 'PENDING', 'PROCESSING', 'READY', 'FAILED']

export function DocumentsPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [files, setFiles] = useState<File[]>([])
  const [collectionId, setCollectionId] = useState('')
  const [preflight, setPreflight] = useState<PreflightItem[] | null>(null)
  const [duplicateChoices, setDuplicateChoices] = useState<
    Record<number, boolean>
  >({})
  const [selectedId, setSelectedId] = useState<string | null>(() =>
    searchParams.get('documentId'),
  )
  const [deleting, setDeleting] = useState(false)
  const [notice, setNotice] = useState('')
  const [uploadResults, setUploadResults] = useState<UploadResult[]>([])
  const [reviewedFiles, setReviewedFiles] = useState<File[]>([])
  const [reviewedCollectionId, setReviewedCollectionId] = useState('')
  const [actionError, setActionError] = useState<unknown>(null)
  const documents = useQuery({
    queryKey: ['documents', status, page],
    queryFn: () => getDocuments(status || undefined, page),
    refetchInterval: (query) =>
      query.state.data?.items.some((item) =>
        ['PENDING', 'PROCESSING'].includes(item.status),
      )
        ? 3_000
        : false,
  })
  const collections = useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  })
  const detail = useQuery({
    queryKey: ['document', selectedId],
    queryFn: () => getDocument(selectedId!),
    enabled: Boolean(selectedId),
  })
  const review = useMutation({
    mutationFn: () => preflightUpload(files, collectionId || undefined),
    onSuccess: (items) => {
      setPreflight(items)
      setReviewedFiles(files)
      setReviewedCollectionId(collectionId)
      setDuplicateChoices({})
    },
  })
  const upload = useMutation({
    mutationFn: () =>
      uploadDocuments(
        reviewedFiles,
        preflight!.map((item, index) => ({
          fileIndex: index,
          decision:
            item.status === 'ACCEPTED'
              ? ('UPLOAD' as const)
              : item.status === 'DUPLICATE' && duplicateChoices[index]
                ? ('UPLOAD_DUPLICATE' as const)
                : ('SKIP' as const),
          ...(item.confirmationToken
            ? { confirmationToken: item.confirmationToken }
            : {}),
        })),
        reviewedCollectionId || undefined,
      ),
    onSuccess: (response) => {
      const count = response.items.filter(
        (item) => item.status === 'UPLOADED',
      ).length
      setNotice(`${count} document${count === 1 ? '' : 's'} uploaded`)
      setUploadResults(response.items)
      setFiles([])
      setPreflight(null)
      setDuplicateChoices({})
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/documents/${id}`),
    onSuccess: () => {
      setSelectedId(null)
      setDeleting(false)
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })

  const error =
    review.error ??
    upload.error ??
    documents.error ??
    detail.error ??
    remove.error ??
    actionError
  const errorMessage = error
    ? getApiError(error, 'The document request failed.')
    : null

  return (
    <>
      <PageHeader
        eyebrow="LIBRARY / DOCUMENTS"
        title="Documents"
        description="Upload source material, follow ingestion, and inspect the evidence available to search and chat."
      />
      {notice ? (
        <p
          className="mb-5 rounded-md border border-emerald-800 bg-emerald-950/30 p-3 text-sm text-emerald-300"
          role="status"
        >
          {notice}
        </p>
      ) : null}
      {uploadResults.length ? (
        <ul aria-label="Upload results" className="mb-5 space-y-2">
          {uploadResults.map((item) => (
            <li
              className="flex justify-between rounded-md border border-[var(--border)] bg-[var(--surface-panel)] p-3 text-xs"
              key={`${item.fileIndex}-${item.filename}`}
            >
              <span>{item.filename}</span>
              <span className="font-data text-[var(--text-muted)]">
                {item.status}
                {item.error ? ` · ${item.error.message}` : ''}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      {errorMessage ? (
        <p
          className="mb-5 rounded-md border border-red-900 bg-red-950/30 p-3 text-sm text-red-300"
          role="alert"
        >
          {errorMessage.message}{' '}
          <span className="font-data">{errorMessage.code}</span>
        </p>
      ) : null}
      <section className="mb-8 rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5">
        <div className="mb-4 flex items-center gap-3">
          <Upload className="size-5 text-[var(--accent-soft)]" />
          <h2 className="font-heading text-lg font-semibold">
            Add source documents
          </h2>
        </div>
        <div className="grid gap-3 lg:grid-cols-[1fr_220px_auto]">
          <label className="flex min-h-11 cursor-pointer items-center rounded-md border border-dashed border-[var(--border-strong)] px-4 text-sm text-[var(--text-muted)] hover:border-[var(--accent-soft)]">
            <span>
              {files.length
                ? `${files.length} selected`
                : 'Choose PDF, DOCX, TXT, or Markdown'}
            </span>
            <input
              accept=".pdf,.docx,.txt,.md,.markdown"
              aria-label="Choose documents"
              className="sr-only"
              disabled={Boolean(preflight) || review.isPending}
              multiple
              onChange={(event) => {
                setFiles(Array.from(event.target.files ?? []))
                setPreflight(null)
                setDuplicateChoices({})
              }}
              type="file"
            />
          </label>
          <select
            aria-label="Upload collection"
            className="field"
            disabled={Boolean(preflight) || review.isPending}
            onChange={(event) => {
              setCollectionId(event.target.value)
              setPreflight(null)
              setDuplicateChoices({})
            }}
            value={collectionId}
          >
            <option value="">Uncategorized</option>
            {collections.data
              ?.filter((item) => !item.uncategorized)
              .map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
          </select>
          <Button
            disabled={!files.length || review.isPending}
            onClick={() => review.mutate()}
            type="button"
          >
            {review.isPending ? 'Checking...' : 'Review upload'}
          </Button>
        </div>
        {preflight ? (
          <UploadReview
            choices={duplicateChoices}
            items={preflight}
            onChoice={(index, value) =>
              setDuplicateChoices((current) => ({ ...current, [index]: value }))
            }
            onUpload={() => upload.mutate()}
            pending={upload.isPending}
          />
        ) : null}
      </section>
      <section>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-heading text-xl font-semibold">Library index</h2>
          <label className="flex items-center gap-2 text-xs text-[var(--text-muted)]">
            Status
            <select
              className="field h-9"
              onChange={(event) => {
                setStatus(event.target.value)
                setPage(0)
              }}
              value={status}
            >
              {statuses.map((item) => (
                <option key={item || 'ALL'} value={item}>
                  {item ? titleCase(item) : 'All'}
                </option>
              ))}
            </select>
          </label>
        </div>
        {documents.isLoading ? (
          <StateMessage icon={LoaderCircle} text="Loading documents..." />
        ) : documents.data?.items.length ? (
          <div className="overflow-hidden rounded-lg border border-[var(--border)]">
            {documents.data.items.map((item) => (
              <DocumentRow
                document={item}
                key={item.id}
                onSelect={setSelectedId}
              />
            ))}
          </div>
        ) : (
          <StateMessage icon={FileText} text="No documents match this view." />
        )}
        {documents.data && documents.data.totalPages > 1 ? (
          <nav
            aria-label="Document pages"
            className="mt-4 flex items-center justify-end gap-3"
          >
            <Button
              disabled={page === 0}
              onClick={() => setPage((value) => value - 1)}
              type="button"
              variant="ghost"
            >
              Previous
            </Button>
            <span className="font-data text-xs text-[var(--text-muted)]">
              Page {page + 1} of {documents.data.totalPages}
            </span>
            <Button
              disabled={page + 1 >= documents.data.totalPages}
              onClick={() => setPage((value) => value + 1)}
              type="button"
              variant="ghost"
            >
              Next
            </Button>
          </nav>
        ) : null}
      </section>
      {selectedId && !deleting ? (
        <DetailPanel
          deleting={deleting}
          detail={detail.data}
          error={detail.error}
          highlightedChunkId={searchParams.get('chunkId')}
          loading={detail.isLoading}
          onClose={() => setSelectedId(null)}
          onDelete={() => setDeleting(true)}
          onDownload={() =>
            void api
              .post<{ url: string }>(
                `/api/v1/documents/${selectedId}/download-url`,
              )
              .then(({ data }) =>
                window.open(data.url, '_blank', 'noopener,noreferrer'),
              )
              .catch(setActionError)
          }
          onRetry={() =>
            void api
              .post(`/api/v1/documents/${selectedId}/retry`)
              .then(() =>
                queryClient.invalidateQueries({ queryKey: ['documents'] }),
              )
              .catch(setActionError)
          }
        />
      ) : null}
      {deleting ? (
        <ConfirmDialog
          description="This removes the original, extracted chunks, and search data. This cannot be undone."
          onCancel={() => setDeleting(false)}
          onConfirm={() => remove.mutate(selectedId!)}
          pending={remove.isPending}
        />
      ) : null}
    </>
  )
}

function UploadReview({
  items,
  choices,
  onChoice,
  onUpload,
  pending,
}: {
  items: PreflightItem[]
  choices: Record<number, boolean>
  onChoice: (index: number, value: boolean) => void
  onUpload: () => void
  pending: boolean
}) {
  const count = items.filter(
    (item, index) =>
      item.status === 'ACCEPTED' ||
      (item.status === 'DUPLICATE' && choices[index]),
  ).length
  return (
    <div className="mt-5 border-t border-[var(--border)] pt-5">
      <div className="space-y-3">
        {items.map((item, index) => (
          <div
            className="rounded-md bg-[var(--surface-raised)] p-4"
            key={`${item.filename}-${index}`}
          >
            <div className="flex justify-between gap-3">
              <span className="text-sm font-semibold">{item.filename}</span>
              <span className="font-data text-[10px] text-[var(--text-faint)]">
                {formatBytes(item.sizeBytes)}
              </span>
            </div>
            {item.status === 'DUPLICATE' ? (
              <fieldset className="mt-3">
                <legend className="mb-2 text-xs text-amber-300">
                  Already in your library
                </legend>
                <label className="mr-5 text-xs">
                  <input
                    checked={Boolean(choices[index])}
                    className="mr-2"
                    name={`duplicate-${index}`}
                    onChange={() => onChoice(index, true)}
                    type="radio"
                  />
                  Upload another copy
                </label>
                <label className="text-xs">
                  <input
                    checked={!choices[index]}
                    className="mr-2"
                    name={`duplicate-${index}`}
                    onChange={() => onChoice(index, false)}
                    type="radio"
                  />
                  Skip duplicate
                </label>
              </fieldset>
            ) : null}
            {item.status === 'REJECTED' ? (
              <p className="mt-2 text-xs text-red-300">{item.error?.message}</p>
            ) : null}
          </div>
        ))}
      </div>
      <Button
        className="mt-4"
        disabled={!count || pending}
        onClick={onUpload}
        type="button"
      >
        {pending
          ? 'Uploading...'
          : `Upload ${count} document${count === 1 ? '' : 's'}`}
      </Button>
    </div>
  )
}

function DocumentRow({
  document,
  onSelect,
}: {
  document: DocumentSummary
  onSelect: (id: string) => void
}) {
  return (
    <button
      aria-label={`View ${document.filename}`}
      className="grid w-full gap-3 border-b border-[var(--border)] bg-[var(--surface-panel)] p-4 text-left last:border-0 hover:bg-[var(--surface-raised)] sm:grid-cols-[1fr_120px_150px_100px] sm:items-center"
      onClick={() => onSelect(document.id)}
      type="button"
    >
      <span>
        <span className="block text-sm font-semibold">{document.filename}</span>
        <span className="mt-1 block text-xs text-[var(--text-faint)]">
          {document.collection.name} · {formatBytes(document.sizeBytes)}
        </span>
      </span>
      <StatusBadge status={document.status} />
      <span className="text-xs text-[var(--text-muted)]">
        {new Date(document.uploadedAt).toLocaleDateString()}
      </span>
      <span className="font-data text-[10px] uppercase text-[var(--text-faint)]">
        .{document.fileExtension}
      </span>
    </button>
  )
}

function DetailPanel({
  detail,
  error,
  highlightedChunkId,
  loading,
  deleting,
  onClose,
  onDelete,
  onDownload,
  onRetry,
}: {
  detail?: Awaited<ReturnType<typeof getDocument>>
  error: unknown
  highlightedChunkId: string | null
  loading: boolean
  deleting: boolean
  onClose: () => void
  onDelete: () => void
  onDownload: () => void
  onRetry: () => void
}) {
  useEffect(() => {
    if (highlightedChunkId && detail) {
      document
        .getElementById(`chunk-${highlightedChunkId}`)
        ?.scrollIntoView?.({ block: 'center' })
    }
  }, [detail, highlightedChunkId])

  return (
    <div
      aria-label="Document detail"
      aria-modal="true"
      className="fixed inset-0 z-50 flex justify-end bg-black/70"
      role="dialog"
    >
      <section className="h-full w-full max-w-xl overflow-y-auto border-l border-[var(--border)] bg-[var(--surface-panel)] p-6 sm:p-8">
        <button
          aria-label="Close document detail"
          className="float-right rounded p-2 hover:bg-[var(--surface-raised)]"
          onClick={onClose}
          type="button"
        >
          <X className="size-5" />
        </button>
        {error ? (
          <p className="error-box" role="alert">
            {getApiError(error, 'Document detail is unavailable.').message}
          </p>
        ) : loading || !detail ? (
          <p>Loading detail...</p>
        ) : (
          <>
            <p className="font-data text-[10px] tracking-widest text-[var(--accent-soft)]">
              DOCUMENT RECORD
            </p>
            <h2 className="font-heading mt-2 pr-10 text-2xl font-semibold">
              {detail.filename}
            </h2>
            <div className="mt-4">
              <StatusBadge status={detail.status} />
            </div>
            {detail.failureMessage ? (
              <div className="mt-6 rounded-md border border-red-900 bg-red-950/30 p-4">
                <p className="font-data text-[10px] text-red-300">
                  {detail.failureCode}
                </p>
                <p className="mt-2 text-sm">{detail.failureMessage}</p>
                {detail.retryable ? (
                  <Button
                    className="mt-4"
                    onClick={onRetry}
                    type="button"
                    variant="ghost"
                  >
                    Retry processing
                  </Button>
                ) : null}
              </div>
            ) : null}
            <h3 className="mt-8 font-heading text-lg font-semibold">
              Extracted passages
            </h3>
            <div className="mt-3 space-y-3">
              {detail.chunks.length ? (
                detail.chunks.map((chunk) => (
                  <blockquote
                    className={cn(
                      'border-l-2 border-[var(--accent)] bg-[var(--surface-raised)] p-4 text-sm leading-relaxed text-[var(--text-muted)]',
                      chunk.id === highlightedChunkId &&
                        'ring-2 ring-[var(--accent-soft)]',
                    )}
                    id={`chunk-${chunk.id}`}
                    key={chunk.id}
                  >
                    {chunk.snippet}
                  </blockquote>
                ))
              ) : (
                <p className="text-sm text-[var(--text-faint)]">
                  No extracted passages yet.
                </p>
              )}
            </div>
            <div className="mt-8 flex flex-wrap gap-3">
              <Button onClick={onDownload} type="button">
                Download original
              </Button>
              <Button
                disabled={deleting}
                onClick={onDelete}
                type="button"
                variant="ghost"
              >
                Delete document
              </Button>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

function ConfirmDialog({
  description,
  pending,
  onCancel,
  onConfirm,
}: {
  description: string
  pending: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div
      aria-label="Confirm permanent document deletion"
      aria-modal="true"
      className="fixed inset-0 z-[60] grid place-items-center bg-black/75 p-4"
      role="dialog"
    >
      <div className="max-w-md rounded-lg border border-[var(--border)] bg-[var(--surface-raised)] p-6">
        <h2 className="font-heading text-xl font-semibold">
          Delete permanently?
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-[var(--text-muted)]">
          {description}
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <Button onClick={onCancel} type="button" variant="ghost">
            Cancel
          </Button>
          <Button disabled={pending} onClick={onConfirm} type="button">
            Delete permanently
          </Button>
        </div>
      </div>
    </div>
  )
}
function StateMessage({
  icon: Icon,
  text,
}: {
  icon: typeof FileText
  text: string
}) {
  return (
    <div className="grid min-h-48 place-items-center rounded-lg border border-dashed border-[var(--border)] text-center text-sm text-[var(--text-muted)]">
      <div>
        <Icon className="mx-auto mb-3 size-6 text-[var(--text-faint)]" />
        <p>{text}</p>
      </div>
    </div>
  )
}
function StatusBadge({ status }: { status: DocumentSummary['status'] }) {
  return (
    <span
      className={cn(
        'inline-flex w-fit rounded-full border px-2.5 py-1 font-data text-[10px]',
        status === 'READY' && 'border-emerald-800 text-emerald-300',
        status === 'FAILED' && 'border-red-900 text-red-300',
        status === 'PROCESSING' && 'border-violet-800 text-violet-300',
        status === 'PENDING' && 'border-amber-800 text-amber-300',
      )}
    >
      {titleCase(status)}
    </span>
  )
}
function titleCase(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
function formatBytes(value: number) {
  if (value >= 1024 * 1024) return `${Math.round(value / 1024 / 1024)} MB`
  if (value >= 1024) return `${Math.round(value / 1024)} KB`
  return `${value} B`
}
