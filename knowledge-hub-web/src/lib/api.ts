import axios, { type InternalAxiosRequestConfig } from 'axios'

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
let accessToken: string | null = null
let refreshHandler: (() => Promise<string>) | null = null
let authFailureHandler: (() => void) | null = null
let activeRefresh: Promise<string> | null = null
let refreshSuspended = false

export interface ApiErrorResponse {
  code: string
  message: string
  requestId: string
  fieldErrors: Array<{ field: string; message: string }>
  metadata: Record<string, unknown>
}

export const api = axios.create({ baseURL, withCredentials: true })

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

api.interceptors.response.use(undefined, async (error: unknown) => {
  if (
    !axios.isAxiosError(error) ||
    error.response?.status !== 401 ||
    !error.config
  ) {
    return Promise.reject(error)
  }
  const request = error.config as InternalAxiosRequestConfig & {
    _authRetry?: boolean
  }
  if (request.url?.startsWith('/api/v1/auth/')) {
    return Promise.reject(error)
  }
  if (refreshSuspended) {
    return Promise.reject(error)
  }
  if (request._authRetry) {
    authFailureHandler?.()
    return Promise.reject(error)
  }
  request._authRetry = true
  try {
    await refreshApiAccessToken()
  } catch (refreshError) {
    authFailureHandler?.()
    return Promise.reject(refreshError)
  }
  return api.request(request)
})

export function refreshApiAccessToken() {
  if (refreshSuspended) {
    return Promise.reject(new Error('Session refresh is suspended.'))
  }
  activeRefresh ??=
    refreshHandler?.().finally(() => {
      activeRefresh = null
    }) ?? Promise.reject(new Error('Session refresh is not configured.'))
  return activeRefresh
}

export function failApiAuthentication() {
  authFailureHandler?.()
}

export function setApiAccessToken(token: string | null) {
  accessToken = token
}

export function configureAuthRefresh(
  refresh: () => Promise<string>,
  onFailure: () => void,
) {
  refreshHandler = refresh
  authFailureHandler = onFailure
}

export async function suspendAuthRefresh() {
  refreshSuspended = true
  try {
    await activeRefresh
  } catch {
    // Logout still needs to revoke the latest cookie after a failed refresh.
  }
}

export function resumeAuthRefresh() {
  refreshSuspended = false
}

export function getApiError(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as ApiErrorResponse | undefined
    if (response?.message) return response
  }
  return {
    code: 'REQUEST_FAILED',
    message: fallback,
    requestId: '',
    fieldErrors: [],
    metadata: {},
  }
}
