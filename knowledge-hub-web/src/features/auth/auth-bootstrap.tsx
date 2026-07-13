import { useEffect, useRef, type ReactNode } from 'react'

import { useAuthStore } from './auth-store'

export function AuthBootstrap({ children }: { children: ReactNode }) {
  const initialize = useAuthStore((state) => state.initialize)
  const status = useAuthStore((state) => state.status)
  const started = useRef(false)

  useEffect(() => {
    if (!started.current) {
      started.current = true
      void initialize()
    }
  }, [initialize])

  if (status === 'unknown') {
    return (
      <main
        className="flex min-h-screen items-center justify-center bg-[var(--surface-dark)]"
        aria-live="polite"
      >
        <p className="font-data text-xs tracking-[0.08em] text-[var(--text-muted)]">
          RESTORING SECURE SESSION
        </p>
      </main>
    )
  }
  return children
}
