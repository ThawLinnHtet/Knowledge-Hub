import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { useAuthStore } from '../features/auth/auth-store'
import { AppRoutes } from '../routes/app-routes'

export function renderWorkspace(path = '/documents') {
  useAuthStore.setState({
    accessToken: 'test-access-token',
    status: 'authenticated',
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
