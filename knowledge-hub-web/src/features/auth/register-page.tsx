import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Lock, Mail } from 'lucide-react'

import { Button } from '../../components/ui/button'
import { getApiError } from '../../lib/api'
import { register } from './auth-api'
import { AuthLayout } from './auth-layout'
import { FormField } from './form-field'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function RegisterPage() {
  const navigate = useNavigate()
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
        password.length >= 12 && password.length <= 128
          ? undefined
          : 'Use 12–128 characters.',
    }
    setErrors(nextErrors)
    if (nextErrors.email || nextErrors.password) return

    setSubmitting(true)
    try {
      await register(email, password)
      navigate('/login?registered=1', { replace: true })
    } catch (error) {
      setErrors({
        form: getApiError(error, 'Unable to create the account. Try again.')
          .message,
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <form className="space-y-[18px]" noValidate onSubmit={handleSubmit}>
        <div>
          <h1 className="font-heading text-[28px] font-semibold sm:text-[32px]">
            Create your account
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--text-muted)]">
            Create a private library for searchable, source-backed knowledge.
          </p>
        </div>
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
          autoComplete="new-password"
          error={errors.password}
          icon={Lock}
          id="password"
          label="Password"
          onChange={(event) => setPassword(event.target.value)}
          type="password"
          value={password}
        />
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
        <p className="text-center text-[13px] text-[var(--text-muted)]">
          Already have an account?{' '}
          <Link
            className="font-semibold text-[var(--accent-soft)] hover:text-white"
            to="/login"
          >
            Sign in
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
