import {
  ArrowRight,
  Check,
  FileText,
  LibraryBig,
  LockKeyhole,
  MessageSquareQuote,
  ScanSearch,
} from 'lucide-react'
import { Link } from 'react-router-dom'

const workflow = [
  {
    label: 'Collect',
    title: 'Bring the documents that matter.',
    description:
      'Upload PDF, DOCX, text, and Markdown files. Keep research, policy, and project knowledge in focused collections.',
    icon: FileText,
  },
  {
    label: 'Retrieve',
    title: 'Search for language and meaning.',
    description:
      'Hybrid retrieval combines exact keyword matches with semantic similarity across every ready passage.',
    icon: ScanSearch,
  },
  {
    label: 'Inspect',
    title: 'Open the passage behind every result.',
    description:
      'Search results point back to specific documents, sections, pages, and extracted passages.',
    icon: MessageSquareQuote,
  },
]

export function LandingPage() {
  return (
    <main className="min-h-screen overflow-hidden bg-[var(--surface-dark)] text-[var(--text-primary)]">
      <LandingHeader />
      <section className="relative border-b border-[var(--border)]">
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-25 [background-image:linear-gradient(var(--border)_1px,transparent_1px),linear-gradient(90deg,var(--border)_1px,transparent_1px)] [background-size:56px_56px] [mask-image:linear-gradient(to_bottom,black,transparent_88%)]"
        />
        <div className="relative mx-auto grid min-h-[calc(100vh-72px)] max-w-[1440px] items-center gap-14 px-5 py-20 sm:px-8 lg:grid-cols-[0.92fr_1.08fr] lg:px-14 lg:py-24">
          <div className="max-w-2xl">
            <p className="font-data mb-6 flex items-center gap-3 text-[10px] tracking-[0.16em] text-[var(--accent-soft)]">
              <span className="h-px w-8 bg-[var(--accent)]" />
              PRIVATE DOCUMENT INTELLIGENCE
            </p>
            <h1 className="font-heading text-[clamp(2.8rem,6.2vw,6rem)] font-semibold leading-[0.96] tracking-[-0.055em]">
              Turn private documents into evidence you can{' '}
              <span className="text-[var(--accent-soft)]">find.</span>
            </h1>
            <p className="mt-7 max-w-xl text-base leading-8 text-[var(--text-muted)] sm:text-lg">
              Organize source material, search by meaning and exact language,
              then inspect the passage behind every result.
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <Link
                className="inline-flex h-12 items-center justify-center gap-2 rounded-[5px] bg-[var(--accent)] px-6 text-sm font-semibold text-white transition hover:bg-[#7b6cff] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent-soft)]"
                to="/register"
              >
                Create your account <ArrowRight className="size-4" />
              </Link>
              <Link
                className="inline-flex h-12 items-center justify-center rounded-[5px] border border-[var(--border-strong)] px-6 text-sm font-semibold text-[var(--text-muted)] transition hover:border-[var(--accent-soft)] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent-soft)]"
                to="/login"
              >
                Sign in
              </Link>
            </div>
            <div className="mt-9 flex flex-wrap gap-x-6 gap-y-2 text-xs text-[var(--text-muted)]">
              <span className="flex items-center gap-2">
                <Check className="size-3.5 text-[var(--accent-soft)]" />
                Owner-scoped library
              </span>
              <span className="flex items-center gap-2">
                <Check className="size-3.5 text-[var(--accent-soft)]" />
                Private originals
              </span>
              <span className="flex items-center gap-2">
                <Check className="size-3.5 text-[var(--accent-soft)]" />
                Inspectable citations
              </span>
            </div>
          </div>
          <EvidencePanel />
        </div>
      </section>

      <section
        className="mx-auto max-w-[1440px] px-5 py-24 sm:px-8 lg:px-14 lg:py-32"
        id="workflow"
      >
        <div className="grid gap-8 border-b border-[var(--border)] pb-12 lg:grid-cols-2 lg:items-end">
          <div>
            <p className="font-data text-[10px] tracking-[0.16em] text-[var(--accent-soft)]">
              ONE TRACEABLE WORKFLOW
            </p>
            <h2 className="font-heading mt-4 max-w-2xl text-3xl font-semibold tracking-tight sm:text-5xl">
              Every result keeps its evidence attached.
            </h2>
          </div>
          <p className="max-w-xl text-sm leading-7 text-[var(--text-muted)] lg:justify-self-end">
            Knowledge Hub retrieves only from ready sources in your selected
            scope. Source-backed chat uses that same fresh retrieval and says
            when the available evidence is insufficient.
          </p>
        </div>
        <ol className="grid lg:grid-cols-3">
          {workflow.map(({ label, title, description, icon: Icon }, index) => (
            <li
              className="border-b border-[var(--border)] py-9 lg:border-b-0 lg:border-r lg:px-8 lg:first:pl-0 lg:last:border-r-0 lg:last:pr-0"
              key={label}
            >
              <div className="flex items-center justify-between">
                <span className="font-data text-[10px] tracking-[0.14em] text-[var(--text-faint)]">
                  0{index + 1} / {label.toUpperCase()}
                </span>
                <Icon className="size-5 text-[var(--accent-soft)]" />
              </div>
              <h3 className="font-heading mt-8 text-xl font-semibold">
                {title}
              </h3>
              <p className="mt-3 text-sm leading-7 text-[var(--text-muted)]">
                {description}
              </p>
            </li>
          ))}
        </ol>
      </section>

      <section className="border-y border-[var(--border)] bg-[var(--surface-panel)]">
        <div className="mx-auto grid max-w-[1440px] gap-10 px-5 py-16 sm:px-8 md:grid-cols-[0.8fr_1.2fr] md:items-center lg:px-14">
          <div className="flex items-center gap-4">
            <span className="flex size-12 items-center justify-center rounded-full border border-[#3a3458] text-[var(--accent-soft)]">
              <LockKeyhole className="size-5" />
            </span>
            <div>
              <p className="font-data text-[10px] tracking-[0.14em] text-[var(--text-faint)]">
                PRIVACY MODEL
              </p>
              <h2 className="font-heading mt-1 text-2xl font-semibold">
                Your library remains yours.
              </h2>
            </div>
          </div>
          <div className="grid gap-4 text-sm leading-7 text-[var(--text-muted)] sm:grid-cols-2">
            <p>
              Every document, collection, search, and conversation is scoped to
              the authenticated owner.
            </p>
            <p>
              Original files remain in private object storage and are shared
              only through short-lived authorized download links.
            </p>
            <p>
              When real AI mode is enabled, extracted text and retrieved
              passages may be processed by the deployment's configured AI
              provider.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-5 py-24 text-center sm:px-8 lg:py-32">
        <p className="font-data text-[10px] tracking-[0.16em] text-[var(--accent-soft)]">
          BUILD A LIBRARY YOU CAN SEARCH
        </p>
        <h2 className="font-heading mt-5 text-4xl font-semibold tracking-tight sm:text-6xl">
          Start with the documents already doing the work.
        </h2>
        <p className="mx-auto mt-5 max-w-xl text-sm leading-7 text-[var(--text-muted)]">
          Create a private account, upload your first sources, and make every
          search result traceable back to what you know.
        </p>
        <Link
          className="mt-8 inline-flex h-12 items-center justify-center gap-2 rounded-[5px] bg-white px-6 text-sm font-semibold text-[#101016] transition hover:bg-[#dcdce7] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent-soft)]"
          to="/register"
        >
          Start your private library <ArrowRight className="size-4" />
        </Link>
      </section>

      <footer className="border-t border-[var(--border)] px-5 py-7 sm:px-8">
        <div className="mx-auto flex max-w-[1440px] flex-col gap-3 text-xs text-[var(--text-faint)] sm:flex-row sm:items-center sm:justify-between">
          <BrandMark />
          <p className="font-data text-[9px] tracking-[0.12em]">
            PRIVATE SOURCES · GROUNDED ANSWERS
          </p>
        </div>
      </footer>
    </main>
  )
}

function LandingHeader() {
  return (
    <header className="relative z-20 h-[72px] border-b border-[var(--border)] bg-[color:var(--surface-dark)]/90 backdrop-blur">
      <div className="mx-auto flex h-full max-w-[1440px] items-center justify-between px-5 sm:px-8 lg:px-14">
        <BrandMark />
        <nav
          aria-label="Public navigation"
          className="flex items-center gap-3 sm:gap-6"
        >
          <a
            className="hidden text-sm text-[var(--text-muted)] hover:text-white sm:block"
            href="#workflow"
          >
            How it works
          </a>
          <Link
            className="text-sm font-semibold text-[var(--text-muted)] hover:text-white"
            to="/login"
          >
            Sign in
          </Link>
          <Link
            className="inline-flex h-9 items-center rounded-[5px] border border-[var(--border-strong)] px-3 text-xs font-semibold hover:border-[var(--accent-soft)] sm:px-4"
            to="/register"
          >
            Create account
          </Link>
        </nav>
      </div>
    </header>
  )
}

function BrandMark() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex size-8 items-center justify-center rounded-lg bg-[var(--accent)]">
        <LibraryBig aria-hidden="true" className="size-4 text-white" />
      </span>
      <span className="font-heading text-lg font-semibold max-[380px]:hidden">
        Knowledge Hub
      </span>
    </div>
  )
}

function EvidencePanel() {
  return (
    <div className="relative mx-auto w-full max-w-[670px] lg:justify-self-end">
      <div
        aria-hidden="true"
        className="absolute -inset-8 bg-[radial-gradient(circle_at_center,rgba(109,93,252,0.16),transparent_68%)]"
      />
      <div className="relative border border-[var(--border-strong)] bg-[#0d0d14] shadow-2xl shadow-black/40">
        <div className="flex h-11 items-center justify-between border-b border-[var(--border)] px-4">
          <span className="font-data text-[9px] tracking-[0.14em] text-[var(--text-faint)]">
            EVIDENCE TRACE / LIVE CHAT
          </span>
          <span className="flex items-center gap-2 font-data text-[9px] text-emerald-300">
            <span className="size-1.5 rounded-full bg-emerald-400" />
            GROUNDED
          </span>
        </div>
        <div className="grid min-h-[430px] md:grid-cols-[0.72fr_1.28fr]">
          <div className="border-b border-[var(--border)] p-4 md:border-b-0 md:border-r">
            <p className="font-data mb-4 text-[9px] tracking-wider text-[var(--text-faint)]">
              RETRIEVED PASSAGES
            </p>
            <Source active label="Product brief.pdf" meta="PAGE 12 · 0.94" />
            <Source label="Research notes.md" meta="SECTION 4 · 0.88" />
            <Source label="Decision log.docx" meta="CHUNK 08 · 0.81" />
          </div>
          <div className="p-5 sm:p-7">
            <p className="font-data text-[10px] tracking-wider text-[var(--text-faint)]">
              QUESTION
            </p>
            <p className="mt-2 text-sm font-medium">
              Why was hybrid retrieval selected?
            </p>
            <div className="my-6 h-px bg-[var(--border)]" />
            <p className="font-data text-[10px] tracking-wider text-[var(--accent-soft)]">
              ANSWER / SOURCE-BACKED
            </p>
            <p className="mt-3 text-sm leading-7 text-[var(--text-muted)]">
              Hybrid retrieval was selected to preserve exact terminology while
              finding conceptually related passages. Keyword and semantic
              candidates are combined only after ownership, readiness, and scope
              filters are applied.
            </p>
            <div className="mt-6 flex flex-wrap gap-2">
              <span className="citation-chip">Product brief · p.12</span>
              <span className="citation-chip">Research notes · §4</span>
            </div>
          </div>
        </div>
        <div className="flex min-h-12 items-center justify-between border-t border-[var(--border)] px-4 text-[10px] text-[var(--text-faint)]">
          <span>Answer limited to retrieved evidence</span>
          <span className="font-data">2 CITATIONS</span>
        </div>
      </div>
    </div>
  )
}

function Source({
  label,
  meta,
  active = false,
}: {
  label: string
  meta: string
  active?: boolean
}) {
  return (
    <div
      className={`mb-2 border p-3 ${active ? 'border-[#4b417e] bg-[#19162a]' : 'border-[var(--border)] bg-[var(--surface-panel)]'}`}
    >
      <div className="flex items-center gap-2">
        <FileText
          className={`size-3.5 ${active ? 'text-[var(--accent-soft)]' : 'text-[var(--text-faint)]'}`}
        />
        <span className="truncate text-xs font-medium">{label}</span>
      </div>
      <p className="font-data mt-2 text-[8px] tracking-wider text-[var(--text-faint)]">
        {meta}
      </p>
    </div>
  )
}
