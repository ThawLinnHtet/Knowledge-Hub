import { useState, type FormEvent } from 'react'
import { ArrowLeft, Mail, TimerReset } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { getApiError } from '../../lib/api'
import { requestPasswordReset } from './auth-api'
import { AuthLayout } from './auth-layout'
import { FormField } from './form-field'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string>()
  const [sent, setSent] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!emailPattern.test(email)) {
      setError('Enter a valid email address.')
      return
    }
    setSubmitting(true)
    try {
      await requestPasswordReset(email)
      setSent(true)
      setError(undefined)
    } catch (requestError) {
      setError(
        getApiError(requestError, 'Unable to request a reset link. Try again.')
          .message,
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout recovery>
      <form className="space-y-[18px]" noValidate onSubmit={handleSubmit}>
        <div>
          <p className="font-data mb-3 text-[9px] tracking-[0.08em] text-[var(--accent-soft)] lg:hidden">
            RECOVERY / 01
          </p>
          <h1 className="font-heading text-[28px] font-semibold sm:text-[32px]">
            Reset your password
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--text-muted)]">
            Enter your account email. We’ll send a secure reset link if the
            account exists.
          </p>
        </div>
        <div className="flex gap-2.5 rounded-[5px] border border-[var(--border)] bg-[var(--surface-panel)] p-3 text-xs leading-5 text-[var(--text-muted)] lg:hidden">
          <TimerReset
            aria-hidden="true"
            className="mt-0.5 size-4 shrink-0 text-[var(--accent-soft)]"
          />
          Reset links are short-lived and single-use.
        </div>
        {sent ? (
          <div
            role="status"
            className="rounded-[5px] border border-[#315c42] bg-[#10251a] px-3 py-2.5 text-sm text-[#86efac]"
          >
            If an account exists for that email, a reset link is on its way.
          </div>
        ) : (
          <FormField
            autoComplete="email"
            error={error}
            icon={Mail}
            id="email"
            label="Email address"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            type="email"
            value={email}
          />
        )}
        {!sent ? (
          <Button className="w-full" disabled={submitting} type="submit">
            {submitting ? 'Sending…' : 'Send reset link'}
          </Button>
        ) : null}
        <Button asChild className="w-full" variant="ghost">
          <Link to="/login">
            <ArrowLeft aria-hidden="true" className="size-3.5" />
            Back to sign in
          </Link>
        </Button>
      </form>
    </AuthLayout>
  )
}
