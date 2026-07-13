import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Cpu, LogOut, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { api, getApiError } from '../../lib/api'
import { useAuthStore } from '../auth/auth-store'
import { PageHeader } from '../workspace/workspace-shell'

interface SystemStatus {
  fakeAi: boolean
  chatModel: string
  embeddingModel: string
  embeddingDimension: number
  maxUploadSizeBytes: number
  maxFilesPerBatch: number
  maxRetrievedChunks: number
  maxChatMessageCharacters: number
}

export function SettingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logout = useAuthStore((state) => state.logout)
  const [showDelete, setShowDelete] = useState(false)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const status = useQuery({
    queryKey: ['system-status'],
    queryFn: async () =>
      (await api.get<SystemStatus>('/api/v1/settings/system')).data,
  })
  const deletion = useMutation({
    mutationFn: () =>
      api.delete('/api/v1/account', { data: { password, confirmation } }),
    onSuccess: async () => {
      useAuthStore.getState().clearSession()
      queryClient.clear()
      navigate('/login')
    },
  })

  return (
    <>
      <PageHeader
        eyebrow="ACCOUNT / PLATFORM"
        title="Settings"
        description="Manage this browser session and inspect the non-secret platform configuration that shapes ingestion and retrieval."
      />
      <div className="grid gap-6 xl:grid-cols-[1fr_1.3fr]">
        <section className="rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5">
          <h2 className="font-heading text-lg font-semibold">Session</h2>
          <p className="mt-2 text-sm leading-relaxed text-[var(--text-muted)]">
            Sign out of this browser and revoke its refresh session.
          </p>
          <Button
            className="mt-5"
            onClick={() => void logout().then(() => queryClient.clear())}
            type="button"
            variant="ghost"
          >
            <LogOut className="size-4" />
            Sign out
          </Button>
          <div className="mt-8 border-t border-[var(--border)] pt-6">
            <h3 className="font-heading font-semibold text-red-300">
              Delete account
            </h3>
            <p className="mt-2 text-sm text-[var(--text-muted)]">
              Permanently schedules all documents, chats, and account records
              for deletion.
            </p>
            <Button
              className="mt-4"
              onClick={() => setShowDelete(true)}
              type="button"
              variant="ghost"
            >
              <ShieldAlert className="size-4" />
              Start account deletion
            </Button>
          </div>
        </section>
        <section className="rounded-lg border border-[var(--border)] bg-[var(--surface-panel)] p-5">
          <div className="flex items-center gap-3">
            <Cpu className="size-5 text-[var(--accent-soft)]" />
            <h2 className="font-heading text-lg font-semibold">
              System status
            </h2>
          </div>
          {status.isLoading ? (
            <p className="mt-5 text-sm text-[var(--text-muted)]">
              Loading safe platform status...
            </p>
          ) : status.error ? (
            <p className="error-box mt-5" role="alert">
              {
                getApiError(status.error, 'System status is unavailable.')
                  .message
              }
            </p>
          ) : status.data ? (
            <dl className="mt-5 divide-y divide-[var(--border)]">
              <Status
                label="AI mode"
                value={
                  status.data.fakeAi
                    ? 'Deterministic fake AI'
                    : 'Configured provider'
                }
              />
              <Status label="Chat model" value={status.data.chatModel} />
              <Status
                label="Embedding model"
                value={status.data.embeddingModel}
              />
              <Status
                label="Embedding dimension"
                value={String(status.data.embeddingDimension)}
              />
              <Status
                label="Maximum upload"
                value={formatMegabytes(status.data.maxUploadSizeBytes)}
              />
              <Status
                label="Files per batch"
                value={String(status.data.maxFilesPerBatch)}
              />
              <Status
                label="Retrieved passages"
                value={String(status.data.maxRetrievedChunks)}
              />
              <Status
                label="Message characters"
                value={status.data.maxChatMessageCharacters.toLocaleString()}
              />
            </dl>
          ) : null}
        </section>
      </div>
      {showDelete ? (
        <div
          aria-label="Confirm account deletion"
          aria-modal="true"
          className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4"
          role="dialog"
        >
          <section className="w-full max-w-md rounded-lg border border-red-950 bg-[var(--surface-raised)] p-6">
            <h2 className="font-heading text-xl font-semibold">
              Delete your account?
            </h2>
            <p className="mt-2 text-sm text-[var(--text-muted)]">
              Enter your password and type DELETE to continue.
            </p>
            {deletion.error ? (
              <p className="error-box mt-4">
                {
                  getApiError(deletion.error, 'Account deletion failed.')
                    .message
                }
              </p>
            ) : null}
            <label className="mt-5 block text-xs text-[var(--text-muted)]">
              Password
              <input
                className="field mt-2 w-full"
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                value={password}
              />
            </label>
            <label className="mt-4 block text-xs text-[var(--text-muted)]">
              Confirmation
              <input
                className="field mt-2 w-full"
                onChange={(event) => setConfirmation(event.target.value)}
                value={confirmation}
              />
            </label>
            <div className="mt-6 flex justify-end gap-3">
              <Button
                onClick={() => setShowDelete(false)}
                type="button"
                variant="ghost"
              >
                Cancel
              </Button>
              <Button
                disabled={
                  !password || confirmation !== 'DELETE' || deletion.isPending
                }
                onClick={() => deletion.mutate()}
                type="button"
              >
                Delete my account
              </Button>
            </div>
          </section>
        </div>
      ) : null}
    </>
  )
}

function Status({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[170px_1fr]">
      <dt className="text-xs text-[var(--text-faint)]">{label}</dt>
      <dd className="font-data break-all text-xs text-[var(--text-primary)]">
        {value}
      </dd>
    </div>
  )
}
function formatMegabytes(bytes: number) {
  return `${Math.round(bytes / 1024 / 1024)} MB`
}
