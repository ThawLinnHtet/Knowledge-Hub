import MockAdapter from 'axios-mock-adapter'
import { afterEach, describe, expect, it } from 'vitest'

import { api, resumeAuthRefresh, suspendAuthRefresh } from '../../lib/api'
import { useAuthStore } from './auth-store'

const http = new MockAdapter(api)

describe('auth session', () => {
  afterEach(() => {
    http.reset()
    useAuthStore.getState().clearSession()
  })

  it('refreshes a cookie-backed session and logs out with CSRF protection', async () => {
    http.onGet('/api/v1/auth/csrf').reply(200, {
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
    })
    http.onPost('/api/v1/auth/refresh').reply((config) => {
      expect(config.headers?.['X-XSRF-TOKEN']).toBe('csrf-token')
      return [
        200,
        {
          accessToken: 'refreshed-token',
          tokenType: 'Bearer',
          expiresInSeconds: 900,
        },
      ]
    })
    http.onPost('/api/v1/auth/logout').reply((config) => {
      expect(config.headers?.['X-XSRF-TOKEN']).toBe('csrf-token')
      return [204]
    })

    await useAuthStore.getState().initialize()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'refreshed-token',
      status: 'authenticated',
    })

    await useAuthStore.getState().logout()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      status: 'anonymous',
    })
    expect(http.history.get).toHaveLength(2)
    expect(http.history.post.map((request) => request.url)).toEqual([
      '/api/v1/auth/refresh',
      '/api/v1/auth/logout',
    ])
  })

  it('refreshes once and retries a protected request after access-token expiry', async () => {
    http
      .onGet('/api/v1/documents')
      .replyOnce(401)
      .onGet('/api/v1/documents')
      .reply(200, [])
    http.onGet('/api/v1/auth/csrf').reply(200, {
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
    })
    http.onPost('/api/v1/auth/refresh').reply(200, {
      accessToken: 'rotated-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    })

    const response = await api.get('/api/v1/documents')

    expect(response.status).toBe(200)
    expect(
      http.history.get.filter((request) => request.url === '/api/v1/documents'),
    ).toHaveLength(2)
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'rotated-token',
      status: 'authenticated',
    })
  })

  it('keeps the local session when server logout fails', async () => {
    useAuthStore.setState({
      accessToken: 'active-token',
      status: 'authenticated',
    })
    http.onGet('/api/v1/auth/csrf').reply(200, {
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
    })
    http.onPost('/api/v1/auth/logout').reply(503)

    await expect(useAuthStore.getState().logout()).rejects.toBeDefined()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'active-token',
      status: 'authenticated',
    })
  })

  it('clears the session when the retried request is still unauthorized', async () => {
    useAuthStore.setState({
      accessToken: 'expired-token',
      status: 'authenticated',
    })
    http.onGet('/api/v1/documents').reply(401)
    http.onGet('/api/v1/auth/csrf').reply(200, {
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
    })
    http.onPost('/api/v1/auth/refresh').reply(200, {
      accessToken: 'rotated-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    })

    await expect(api.get('/api/v1/documents')).rejects.toBeDefined()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      status: 'anonymous',
    })
  })

  it('keeps the refreshed session when the retried request fails for another reason', async () => {
    useAuthStore.setState({
      accessToken: 'expired-token',
      status: 'authenticated',
    })
    http
      .onGet('/api/v1/documents')
      .replyOnce(401)
      .onGet('/api/v1/documents')
      .reply(500)
    http.onGet('/api/v1/auth/csrf').reply(200, {
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
    })
    http.onPost('/api/v1/auth/refresh').reply(200, {
      accessToken: 'rotated-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    })

    await expect(api.get('/api/v1/documents')).rejects.toBeDefined()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'rotated-token',
      status: 'authenticated',
    })
  })

  it('does not clear the session for a request rejected while logout suspends refresh', async () => {
    useAuthStore.setState({
      accessToken: 'active-token',
      status: 'authenticated',
    })
    http.onGet('/api/v1/documents').reply(401)
    await suspendAuthRefresh()

    try {
      await expect(api.get('/api/v1/documents')).rejects.toBeDefined()
      expect(useAuthStore.getState()).toMatchObject({
        accessToken: 'active-token',
        status: 'authenticated',
      })
    } finally {
      resumeAuthRefresh()
    }
  })
})
