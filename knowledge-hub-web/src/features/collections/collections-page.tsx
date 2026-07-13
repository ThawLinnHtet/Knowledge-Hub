import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Folder, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { Button } from '../../components/ui/button'
import { api, getApiError } from '../../lib/api'
import { getCollections, type Collection } from '../workspace/workspace-api'
import { PageHeader } from '../workspace/workspace-shell'

export function CollectionsPage() {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [renaming, setRenaming] = useState<Collection | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [deleting, setDeleting] = useState<Collection | null>(null)
  const collections = useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  })
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['collections'] })
  const create = useMutation({
    mutationFn: () => api.post('/api/v1/collections', { name }),
    onSuccess: () => {
      setName('')
      void refresh()
    },
  })
  const rename = useMutation({
    mutationFn: () =>
      api.patch(`/api/v1/collections/${renaming!.id}`, { name: renameValue }),
    onSuccess: () => {
      setRenaming(null)
      void refresh()
    },
  })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/v1/collections/${deleting!.id}`),
    onSuccess: () => {
      setDeleting(null)
      void refresh()
    },
  })
  const error =
    collections.error ?? create.error ?? rename.error ?? remove.error

  function submit(event: FormEvent) {
    event.preventDefault()
    if (name.trim()) create.mutate()
  }
  function beginRename(collection: Collection) {
    setRenaming(collection)
    setRenameValue(collection.name)
  }

  return (
    <>
      <PageHeader
        eyebrow="LIBRARY / ORGANIZE"
        title="Collections"
        description="Create focused shelves for related source material. Removing a shelf safely returns its documents to Uncategorized."
      />
      {error ? (
        <p className="mb-5 error-box" role="alert">
          {getApiError(error, 'The collection request failed.').message}
        </p>
      ) : null}
      <form
        className="mb-8 flex flex-col gap-3 rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5 sm:flex-row"
        onSubmit={submit}
      >
        <label className="flex-1">
          <span className="mb-2 block text-xs font-medium text-[var(--text-muted)]">
            Collection name
          </span>
          <input
            aria-label="Collection name"
            className="field w-full"
            maxLength={160}
            onChange={(event) => setName(event.target.value)}
            placeholder="e.g. Product research"
            value={name}
          />
        </label>
        <Button
          className="sm:self-end"
          disabled={!name.trim() || create.isPending}
          type="submit"
        >
          <Plus className="size-4" />
          Create collection
        </Button>
      </form>
      {collections.isLoading ? (
        <p className="text-sm text-[var(--text-muted)]">
          Loading collections...
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {collections.data?.map((collection) => (
            <article
              className="group rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5"
              key={collection.id}
            >
              <div className="flex items-start justify-between gap-3">
                <span className="flex size-10 items-center justify-center rounded-md bg-[var(--surface-raised)] text-[var(--accent-soft)]">
                  <Folder className="size-5" />
                </span>
                <div className="flex gap-1">
                  {!collection.uncategorized ? (
                    <>
                      <button
                        aria-label={`Rename ${collection.name}`}
                        className="icon-button"
                        onClick={() => beginRename(collection)}
                        type="button"
                      >
                        <Pencil className="size-4" />
                      </button>
                      <button
                        aria-label={`Delete ${collection.name}`}
                        className="icon-button"
                        onClick={() => setDeleting(collection)}
                        type="button"
                      >
                        <Trash2 className="size-4" />
                      </button>
                    </>
                  ) : null}
                </div>
              </div>
              <h2 className="font-heading mt-5 text-lg font-semibold">
                {collection.name}
              </h2>
              <p className="mt-1 font-data text-[10px] uppercase tracking-wider text-[var(--text-faint)]">
                {collection.documentCount} document
                {collection.documentCount === 1 ? '' : 's'}
              </p>
              {collection.uncategorized ? (
                <p className="mt-4 text-xs text-[var(--text-muted)]">
                  Protected fallback collection
                </p>
              ) : null}
            </article>
          ))}
        </div>
      )}
      {renaming ? (
        <Dialog title={`Rename ${renaming.name}`}>
          <label className="text-xs text-[var(--text-muted)]">
            New collection name
            <input
              aria-label="New collection name"
              autoFocus
              className="field mt-2 w-full"
              onChange={(event) => setRenameValue(event.target.value)}
              value={renameValue}
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
              disabled={!renameValue.trim() || rename.isPending}
              onClick={() => rename.mutate()}
              type="button"
            >
              Save name
            </Button>
          </div>
        </Dialog>
      ) : null}
      {deleting ? (
        <Dialog title={`Delete ${deleting.name}?`}>
          <p className="text-sm leading-relaxed text-[var(--text-muted)]">
            Its {deleting.documentCount} documents will move to Uncategorized.
            No documents will be deleted.
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
              Delete collection
            </Button>
          </div>
        </Dialog>
      ) : null}
    </>
  )
}

function Dialog({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <div
      aria-label={title}
      aria-modal="true"
      className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4"
      role="dialog"
    >
      <section className="w-full max-w-md rounded-lg border border-[var(--border)] bg-[var(--surface-raised)] p-6">
        <h2 className="font-heading mb-5 text-xl font-semibold">{title}</h2>
        {children}
      </section>
    </div>
  )
}
