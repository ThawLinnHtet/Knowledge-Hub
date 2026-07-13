import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Lock, Mail } from 'lucide-react'

import { Button } from '../../components/ui/button'
import { getApiError } from '../../lib/api'
import { AuthLayout } from './auth-layout'
import { FormField } from './form-field'
import { useAuthStore } from './auth-store'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const login = useAuthStore((state) => state.login)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{
    email?: string
    password?: string
    form?: string
  }>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = {
      email: emailPattern.test(email)
        ? undefined
        : 'Enter a valid email address.',
      password:
        password.length >= 12
          ? undefined
          : 'Password must be 12–128 characters.',
    }
    setErrors(nextErrors)
    if (nextErrors.email || nextErrors.password) return

    setSubmitting(true)
    try {
      await login(email, password)
      navigate('/documents', { replace: true })
    } catch (error) {
      const response = getApiError(error, 'Unable to sign in. Try again.')
      setErrors({ form: response.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <form className="space-y-[18px]" noValidate onSubmit={handleSubmit}>
        <div>
          <h1 className="font-heading text-[28px] font-semibold sm:text-[32px]">
            Welcome back
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--text-muted)]">
            Sign in to continue to your private knowledge base.
          </p>
        </div>
        {searchParams.get('registered') === '1' ? (
          <div
            role="status"
            className="rounded-[5px] border border-[#315c42] bg-[#10251a] px-3 py-2.5 text-sm text-[#86efac]"
          >
            Account created. Sign in to continue.
          </div>
        ) : null}
        {searchParams.get('reset') === '1' ? (
          <div
            role="status"
            className="rounded-[5px] border border-[#315c42] bg-[#10251a] px-3 py-2.5 text-sm text-[#86efac]"
          >
            Password changed. Sign in with your new password.
          </div>
        ) : null}
        {errors.form ? (
          <div
            role="alert"
            className="rounded-[5px] border border-[var(--danger)] bg-[#281416] px-3 py-2.5 text-sm text-[var(--danger-text)]"
          >
            {errors.form}
          </div>
        ) : null}
        <FormField
          autoComplete="email"
          error={errors.email}
          icon={Mail}
          id="email"
          label="Email address"
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
          type="email"
          value={email}
        />
        <FormField
          autoComplete="current-password"
          error={errors.password}
          icon={Lock}
          id="password"
          label="Password"
          onChange={(event) => setPassword(event.target.value)}
          type="password"
          value={password}
        />
        <div className="flex justify-end">
          <Link
            className="text-xs font-semibold text-[var(--accent-soft)] hover:text-white"
            to="/forgot-password"
          >
            Forgot password?
          </Link>
        </div>
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
        <p className="text-center text-[13px] text-[var(--text-muted)]">
          New to Knowledge Hub?{' '}
          <Link
            className="font-semibold text-[var(--accent-soft)] hover:text-white"
            to="/register"
          >
            Create an account
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
