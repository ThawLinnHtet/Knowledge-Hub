import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { AuthCacheBoundary } from './auth-cache-boundary'
import { useAuthStore } from './auth-store'

describe('authenticated cache isolation', () => {
  afterEach(() => useAuthStore.getState().clearSession())

  it('removes user-scoped server data when the session becomes anonymous', async () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['documents'], [{ filename: 'private.pdf' }])
    useAuthStore.setState({ accessToken: null, status: 'anonymous' })

    render(
      <QueryClientProvider client={queryClient}>
        <AuthCacheBoundary>Signed out</AuthCacheBoundary>
      </QueryClientProvider>,
    )

    await waitFor(() =>
      expect(queryClient.getQueryData(['documents'])).toBeUndefined(),
    )
  })
})
