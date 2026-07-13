import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'

import { LoginPage } from '../features/auth/login-page'
import { RegisterPage } from '../features/auth/register-page'
import { ForgotPasswordPage } from '../features/auth/forgot-password-page'
import { ResetPasswordPage } from '../features/auth/reset-password-page'
import { useAuthStore } from '../features/auth/auth-store'
import { WorkspaceShell } from '../features/workspace/workspace-shell'
import { DocumentsPage } from '../features/documents/documents-page'
import { CollectionsPage } from '../features/collections/collections-page'
import { SearchRoute } from '../features/search/search-page'
import { SettingsPage } from '../features/settings/settings-page'
import { LandingPage } from '../features/landing/landing-page'
import { ChatPage } from '../features/chat/chat-page'

function ProtectedWorkspace() {
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  )
  if (!authenticated) return <Navigate replace to="/login" />
  return (
    <WorkspaceShell>
      <Outlet />
    </WorkspaceShell>
  )
}

function GuestRoute({ children }: { children: ReactNode }) {
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  )
  return authenticated ? <Navigate replace to="/documents" /> : children
}

function RouteFallback() {
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  )
  return <Navigate replace to={authenticated ? '/documents' : '/'} />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route
        element={
          <GuestRoute>
            <LandingPage />
          </GuestRoute>
        }
        path="/"
      />
      <Route
        element={
          <GuestRoute>
            <LoginPage />
          </GuestRoute>
        }
        path="/login"
      />
      <Route
        element={
          <GuestRoute>
            <RegisterPage />
          </GuestRoute>
        }
        path="/register"
      />
      <Route
        element={
          <GuestRoute>
            <ForgotPasswordPage />
          </GuestRoute>
        }
        path="/forgot-password"
      />
      <Route
        element={
          <GuestRoute>
            <ResetPasswordPage />
          </GuestRoute>
        }
        path="/reset-password"
      />
      <Route element={<ProtectedWorkspace />}>
        <Route element={<DocumentsPage />} path="/documents" />
        <Route element={<CollectionsPage />} path="/collections" />
        <Route element={<SearchRoute />} path="/search" />
        <Route element={<ChatPage />} path="/chat" />
        <Route element={<SettingsPage />} path="/settings" />
      </Route>
      <Route element={<RouteFallback />} path="*" />
    </Routes>
  )
}
