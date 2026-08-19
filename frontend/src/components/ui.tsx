import type { ButtonHTMLAttributes, InputHTMLAttributes, LabelHTMLAttributes, ReactNode } from 'react'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-2xl border border-ink-200 bg-white shadow-sm ${className}`}>
      {children}
    </div>
  )
}

export function Label({ className = '', ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={`mb-1.5 block text-sm font-medium text-ink-700 ${className}`} {...props} />
}

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`w-full rounded-xl border border-ink-200 bg-white px-4 py-2.5 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-100 disabled:bg-ink-50 disabled:text-ink-400 ${className}`}
      {...props}
    />
  )
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost'
  fullWidth?: boolean
  loading?: boolean
}

export function Button({
  variant = 'primary',
  fullWidth = false,
  loading = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonProps) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition focus:outline-none focus:ring-4 disabled:cursor-not-allowed disabled:opacity-60'
  const variants: Record<string, string> = {
    primary: 'bg-brand-600 text-white hover:bg-brand-700 focus:ring-brand-200',
    secondary: 'border border-ink-200 bg-white text-ink-700 hover:bg-ink-50 focus:ring-ink-100',
    ghost: 'text-ink-600 hover:bg-ink-100 focus:ring-ink-100',
  }

  return (
    <button
      className={`${base} ${variants[variant]} ${fullWidth ? 'w-full' : ''} ${className}`}
      disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm font-medium text-danger-600">
      {message}
    </div>
  )
}

export function Badge({ children, tone = 'brand' }: { children: ReactNode; tone?: 'brand' | 'ink' | 'danger' }) {
  const tones: Record<string, string> = {
    brand: 'bg-brand-100 text-brand-700',
    ink: 'bg-ink-100 text-ink-600',
    danger: 'bg-danger-50 text-danger-600',
  }
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {children}
    </span>
  )
}
