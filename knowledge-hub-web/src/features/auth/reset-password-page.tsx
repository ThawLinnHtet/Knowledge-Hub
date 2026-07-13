import { useState, type FormEvent } from 'react'
import { ArrowLeft, Lock } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { Button } from '../../components/ui/button'
import { getApiError } from '../../lib/api'
import { resetPassword } from './auth-api'
import { AuthLayout } from './auth-layout'
import { FormField } from './form-field'

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState<{
    password?: string
    confirmation?: string
    form?: string
  }>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const token = searchParams.get('token') ?? ''
    const nextErrors = {
      password:
        password.length >= 12 && password.length <= 128
          ? undefined
          : 'Use 12–128 characters.',
      confirmation:
        password === confirmation ? undefined : 'Passwords do not match.',
      form: token ? undefined : 'This reset link is invalid or incomplete.',
    }
    setErrors(nextErrors)
    if (nextErrors.password || nextErrors.confirmation || nextErrors.form)
      return

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      navigate('/login?reset=1', { replace: true })
    } catch (error) {
      setErrors({
        form: getApiError(
          error,
          'Unable to reset the password. Request a new link.',
        ).message,
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout recovery>
      <form className="space-y-[18px]" noValidate onSubmit={handleSubmit}>
        <div>
          <p className="font-data mb-3 text-[9px] tracking-[0.08em] text-[var(--accent-soft)] lg:hidden">
            RECOVERY / 02
          </p>
          <h1 className="font-heading text-[28px] font-semibold sm:text-[32px]">
            Choose a new password
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--text-muted)]">
            Use 12–128 characters. This reset link can only be used once.
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
          autoComplete="new-password"
          error={errors.password}
          icon={Lock}
          id="new-password"
          label="New password"
          onChange={(event) => setPassword(event.target.value)}
          type="password"
          value={password}
        />
        <FormField
          autoComplete="new-password"
          error={errors.confirmation}
          icon={Lock}
          id="confirm-password"
          label="Confirm password"
          onChange={(event) => setConfirmation(event.target.value)}
          type="password"
          value={confirmation}
        />
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? 'Resetting…' : 'Reset password'}
        </Button>
        <Button asChild className="w-full" variant="ghost">
          <Link to="/login">
            <ArrowLeft aria-hidden="true" className="size-3.5" />
            Return to sign in
          </Link>
        </Button>
      </form>
    </AuthLayout>
  )
}
