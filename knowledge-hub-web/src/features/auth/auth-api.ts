import { api, setApiAccessToken } from '../../lib/api'

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export async function login(email: string, password: string) {
  const { data } = await api.post<TokenResponse>('/api/v1/auth/login', {
    email,
    password,
  })
  setApiAccessToken(data.accessToken)
  return data
}

export async function register(email: string, password: string) {
  await api.post('/api/v1/auth/register', { email, password })
}

async function csrfHeaders() {
  const { data } = await api.get<{ token: string; headerName: string }>(
    '/api/v1/auth/csrf',
  )
  return { [data.headerName]: data.token }
}

export async function refreshSession() {
  const { data } = await api.post<TokenResponse>(
    '/api/v1/auth/refresh',
    undefined,
    {
      headers: await csrfHeaders(),
    },
  )
  setApiAccessToken(data.accessToken)
  return data
}

export async function logout() {
  await api.post('/api/v1/auth/logout', undefined, {
    headers: await csrfHeaders(),
  })
}

export async function requestPasswordReset(email: string) {
  await api.post('/api/v1/auth/forgot-password', { email })
}

export async function resetPassword(token: string, password: string) {
  await api.post('/api/v1/auth/reset-password', { token, password })
}

export function clearApiSession() {
  setApiAccessToken(null)
}
