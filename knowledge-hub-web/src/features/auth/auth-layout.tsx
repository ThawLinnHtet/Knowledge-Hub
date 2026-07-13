import type { ReactNode } from 'react'
import {
  FileCheck,
  LibraryBig,
  LockKeyhole,
  ScanSearch,
  ShieldCheck,
} from 'lucide-react'

const evidence = [
  { icon: LockKeyhole, label: 'Private source', meta: 'OWNER SCOPED' },
  { icon: ScanSearch, label: 'Grounded answer', meta: 'FRESH RETRIEVAL' },
  { icon: FileCheck, label: 'Traceable citation', meta: 'CHUNK LINKED' },
]

export function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex size-8 items-center justify-center rounded-lg bg-[var(--accent)] sm:size-[34px]">
        <LibraryBig aria-hidden="true" className="size-4 text-white" />
      </span>
      <span className="font-heading text-lg font-semibold sm:text-xl">
        Knowledge Hub
      </span>
    </div>
  )
}

export function AuthLayout({
  children,
  recovery = false,
}: {
  children: ReactNode
  recovery?: boolean
}) {
  return (
    <main className="min-h-screen bg-[var(--surface-dark)]">
      <header className="flex h-16 items-center justify-between border-b border-[var(--border)] px-5 lg:hidden">
        <Brand />
        <ShieldCheck
          aria-label="Private account"
          className="size-[18px] text-[var(--accent-soft)]"
        />
      </header>
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-[1600px] lg:min-h-screen">
        <aside className="hidden w-[42.36%] min-w-[520px] flex-col justify-between border-r border-[#252534] bg-[linear-gradient(135deg,#151222_0%,#0a0a0f_100%)] p-16 lg:flex">
          <Brand />
          {recovery ? <RecoveryStory /> : <EvidenceStory />}
          <p className="font-data text-[10px] tracking-[0.07em] text-[var(--text-faint)]">
            ENCRYPTED IN TRANSIT · USER-OWNED LIBRARIES
          </p>
        </aside>
        <section className="flex flex-1 items-start justify-center px-5 py-9 sm:items-center sm:px-10 lg:py-16">
          <div className="w-full max-w-[420px]">{children}</div>
        </section>
      </div>
    </main>
  )
}

function EvidenceStory() {
  return (
    <div className="max-w-[482px] space-y-5">
      <ShieldCheck
        aria-hidden="true"
        className="size-8 text-[var(--accent-soft)]"
      />
      <h2 className="font-heading text-[38px] font-semibold leading-[1.12]">
        Your documents remain your private knowledge base.
      </h2>
      <p className="leading-relaxed text-[var(--text-muted)]">
        Account-isolated libraries, cited answers, and inspectable source
        passages make research work easier to trust.
      </p>
      <div className="space-y-2.5 border-t border-[var(--border)] pt-4">
        {evidence.map(({ icon: Icon, label, meta }) => (
          <div key={label} className="flex items-center gap-3">
            <span className="flex size-7 items-center justify-center rounded border border-[#3a3458]">
              <Icon
                aria-hidden="true"
                className="size-3.5 text-[var(--accent-soft)]"
              />
            </span>
            <span>
              <span className="block text-[13px] font-semibold">{label}</span>
              <span className="font-data block text-[9px] tracking-[0.07em] text-[var(--text-faint)]">
                {meta}
              </span>
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

function RecoveryStory() {
  return (
    <div className="max-w-[482px] space-y-5">
      <p className="font-data text-[10px] tracking-[0.08em] text-[var(--accent-soft)]">
        RECOVERY PROTOCOL
      </p>
      <h2 className="font-heading text-[38px] font-semibold leading-[1.12]">
        Recover access without exposing your library.
      </h2>
      <p className="leading-relaxed text-[var(--text-muted)]">
        Reset links are short-lived and single-use. Your documents remain
        private throughout recovery.
      </p>
      <ol className="border-y border-[var(--border)] text-[13px] font-semibold">
        {[
          'Request secure link',
          'Verify one-time token',
          'Set a new password',
        ].map((step, index) => (
          <li
            key={step}
            className="flex gap-4 border-b border-[var(--border)] py-3 last:border-0"
          >
            <span className="font-data text-[10px] text-[var(--text-faint)]">
              0{index + 1}
            </span>
            {step}
          </li>
        ))}
      </ol>
    </div>
  )
}
