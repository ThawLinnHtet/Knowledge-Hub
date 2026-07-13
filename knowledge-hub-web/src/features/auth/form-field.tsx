import type { ComponentType, InputHTMLAttributes } from 'react'

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  icon: ComponentType<{
    className?: string
    'aria-hidden'?: boolean | 'true' | 'false'
  }>
}

export function FormField({
  label,
  error,
  icon: Icon,
  id,
  ...props
}: FormFieldProps) {
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className="space-y-1.5">
      <label className="block text-[13px] font-semibold" htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <Icon
          aria-hidden="true"
          className={`absolute left-3.5 top-1/2 size-4 -translate-y-1/2 ${error ? 'text-[var(--danger)]' : 'text-[var(--text-faint)]'}`}
        />
        <input
          {...props}
          id={id}
          aria-describedby={errorId}
          aria-invalid={Boolean(error)}
          className={`h-[46px] w-full rounded-[5px] border bg-[#111119] pl-10 pr-3 text-sm outline-none transition placeholder:text-[var(--text-faint)] focus:ring-2 focus:ring-[var(--accent-soft)]/35 ${error ? 'border-[var(--danger)]' : 'border-[var(--border-strong)] focus:border-[var(--accent-soft)]'}`}
        />
      </div>
      {error ? (
        <p id={errorId} className="text-xs text-[var(--danger-text)]">
          {error}
        </p>
      ) : null}
    </div>
  )
}
