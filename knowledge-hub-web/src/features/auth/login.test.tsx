import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MockAdapter from 'axios-mock-adapter'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'

import { api } from '../../lib/api'
import { useAuthStore } from './auth-store'
import { AppRoutes } from '../../routes/app-routes'

const http = new MockAdapter(api)

function renderRoute(path = '/login') {
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

describe('login', () => {
  afterEach(() => {
    http.reset()
    useAuthStore.getState().clearSession()
  })

  it('validates credentials, signs in, and enters the protected workspace', async () => {
    const user = userEvent.setup()
    http.onPost('/api/v1/auth/login').reply(200, {
      accessToken: 'access-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    })

    renderRoute()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(http.history.post).toHaveLength(0)

    await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Documents' }),
    ).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBe('access-token')
    expect(http.history.post).toHaveLength(1)
  })

  it('renders the standard backend error message when login fails', async () => {
    const user = userEvent.setup()
    http.onPost('/api/v1/auth/login').reply(401, {
      code: 'INVALID_CREDENTIALS',
      message: 'The email or password is incorrect.',
      requestId: 'request-1',
      fieldErrors: [],
      metadata: {},
    })

    renderRoute()
    await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'incorrect-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(
      await screen.findByText('The email or password is incorrect.'),
    ).toBeInTheDocument()
  })

  it('creates an account and returns to sign in with confirmation', async () => {
    const user = userEvent.setup()
    http.onPost('/api/v1/auth/register').reply(201, {
      id: 'acb4d74a-5fdd-4f5a-87ce-e50bd91078ea',
      email: 'ada@example.com',
    })

    renderRoute('/register')
    await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText('Account created. Sign in to continue.'),
    ).toBeInTheDocument()
    expect(http.history.post).toHaveLength(1)
  })

  it('requests a password reset without revealing account existence', async () => {
    const user = userEvent.setup()
    http.onPost('/api/v1/auth/forgot-password').reply(202)

    renderRoute('/forgot-password')
    await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(
      await screen.findByText(
        'If an account exists for that email, a reset link is on its way.',
      ),
    ).toBeInTheDocument()
  })

  it('resets the password with the route token and returns to sign in', async () => {
    const user = userEvent.setup()
    http.onPost('/api/v1/auth/reset-password').reply(204)

    renderRoute('/reset-password?token=one-time-token')
    await user.type(
      screen.getByLabelText('New password'),
      'correct-horse-battery',
    )
    await user.type(
      screen.getByLabelText('Confirm password'),
      'correct-horse-battery',
    )
    await user.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(
      await screen.findByText(
        'Password changed. Sign in with your new password.',
      ),
    ).toBeInTheDocument()
    expect(JSON.parse(http.history.post[0].data as string)).toMatchObject({
      token: 'one-time-token',
    })
  })

  it('redirects authenticated users away from guest auth routes', async () => {
    useAuthStore.setState({
      accessToken: 'active-token',
      status: 'authenticated',
    })

    renderRoute('/login')

    expect(
      await screen.findByRole('heading', { name: 'Documents' }),
    ).toBeInTheDocument()
  })
})
