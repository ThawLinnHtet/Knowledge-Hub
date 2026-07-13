import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../auth/auth-store'
import { AppRoutes } from '../../routes/app-routes'

function renderRoot() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('public landing page', () => {
  afterEach(() => useAuthStore.getState().clearSession())

  it('explains the source-backed workflow and prioritizes account creation', () => {
    useAuthStore.setState({ accessToken: null, status: 'anonymous' })

    renderRoot()

    expect(
      screen.getByRole('heading', {
        name: 'Turn private documents into evidence you can find.',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Create your account' }),
    ).toHaveAttribute('href', '/register')
    expect(screen.getAllByRole('link', { name: 'Sign in' })).not.toHaveLength(0)
    screen
      .getAllByRole('link', { name: 'Sign in' })
      .forEach((link) => expect(link).toHaveAttribute('href', '/login'))
    expect(
      screen.getByText('Every result keeps its evidence attached.'),
    ).toBeInTheDocument()
    expect(screen.getByText('EVIDENCE TRACE / LIVE CHAT')).toBeInTheDocument()
  })

  it('sends authenticated visitors directly to their document library', async () => {
    useAuthStore.setState({
      accessToken: 'active-token',
      status: 'authenticated',
    })

    renderRoot()

    expect(
      await screen.findByRole('heading', { name: 'Documents' }),
    ).toBeInTheDocument()
  })
})
