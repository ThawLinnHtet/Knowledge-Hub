import {
  BookOpenText,
  FolderKanban,
  LibraryBig,
  Menu,
  MessageSquareText,
  Search,
  Settings,
  X,
} from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'

import { cn } from '../../lib/utils'

const navigation = [
  { to: '/documents', label: 'Documents', icon: BookOpenText },
  { to: '/collections', label: 'Collections', icon: FolderKanban },
  { to: '/search', label: 'Search', icon: Search },
  { to: '/chat', label: 'Chat', icon: MessageSquareText },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function WorkspaceShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  function quickSearch(event: FormEvent) {
    event.preventDefault()
    const value = query.trim()
    if (value) navigate(`/search?q=${encodeURIComponent(value)}`)
  }

  return (
    <div className="min-h-screen bg-[var(--surface-dark)] text-[var(--text-primary)]">
      <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-[var(--border)] bg-[color:var(--surface-dark)]/95 px-4 backdrop-blur md:pl-[260px]">
        <button
          aria-label={open ? 'Close navigation' : 'Open navigation'}
          className="rounded-md p-2 text-[var(--text-muted)] hover:bg-[var(--surface-raised)] md:hidden"
          onClick={() => setOpen((value) => !value)}
          type="button"
        >
          {open ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
        <form
          className="mx-auto flex w-full max-w-xl"
          onSubmit={quickSearch}
          role="search"
        >
          <label className="sr-only" htmlFor="quick-search">
            Global quick search
          </label>
          <div className="relative w-full">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--text-faint)]" />
            <input
              className="h-10 w-full rounded-md border border-[var(--border)] bg-[var(--surface-panel)] pl-10 pr-4 text-sm outline-none placeholder:text-[var(--text-faint)] focus:border-[var(--accent-soft)]"
              id="quick-search"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search every ready document..."
              value={query}
            />
          </div>
        </form>
      </header>
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-[260px] border-r border-[var(--border)] bg-[var(--surface-panel)] p-5 transition-transform md:translate-x-0',
          open ? 'translate-x-0' : 'hidden -translate-x-full md:block',
        )}
      >
        <div className="mb-9 flex items-center gap-3 px-2">
          <span className="flex size-9 items-center justify-center rounded-lg bg-[var(--accent)]">
            <LibraryBig className="size-4" />
          </span>
          <div>
            <p className="font-heading font-semibold">Knowledge Hub</p>
            <p className="font-data text-[9px] tracking-[0.12em] text-[var(--text-faint)]">
              PRIVATE LIBRARY
            </p>
          </div>
        </div>
        <nav aria-label="Workspace" className="space-y-1">
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink
              className={({ isActive }) =>
                cn(
                  'flex h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-[var(--text-muted)] hover:bg-[var(--surface-raised)] hover:text-white',
                  isActive && 'bg-[var(--surface-raised)] text-white',
                )
              }
              key={to}
              onClick={() => setOpen(false)}
              to={to}
            >
              <Icon className="size-[18px]" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="absolute bottom-6 left-5 right-5 border-t border-[var(--border)] pt-4">
          <p className="font-data text-[9px] tracking-[0.1em] text-[var(--text-faint)]">
            OWNER-SCOPED · SOURCE-BACKED
          </p>
        </div>
      </aside>
      {open ? (
        <button
          aria-label="Close navigation overlay"
          className="fixed inset-0 z-30 bg-black/60 md:hidden"
          onClick={() => setOpen(false)}
          type="button"
        />
      ) : null}
      <main className="px-4 py-7 sm:px-7 md:ml-[260px] lg:px-10 lg:py-9">
        {children}
      </main>
    </div>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <header className="mb-8 flex flex-col justify-between gap-4 border-b border-[var(--border)] pb-6 sm:flex-row sm:items-end">
      <div>
        <p className="font-data mb-2 text-[10px] tracking-[0.14em] text-[var(--accent-soft)]">
          {eyebrow}
        </p>
        <h1 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
          {title}
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-[var(--text-muted)]">
          {description}
        </p>
      </div>
      {action}
    </header>
  )
}
