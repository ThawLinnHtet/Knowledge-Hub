import { useQueryClient } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'

import { useAuthStore } from './auth-store'

export function AuthCacheBoundary({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const status = useAuthStore((state) => state.status)

  useEffect(() => {
    if (status === 'anonymous') queryClient.clear()
  }, [queryClient, status])

  return children
}
