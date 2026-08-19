import type { ReactNode } from 'react'
import { Logo } from './Logo'

export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-brand-50 to-ink-50 px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <Logo size={104} />
          <h1 className="mt-4 text-xl font-bold text-ink-900">{title}</h1>
          <p className="mt-1 text-sm text-ink-500">{subtitle}</p>
        </div>

        <div className="rounded-2xl border border-ink-200 bg-white p-6 shadow-sm sm:p-8">{children}</div>
      </div>
    </div>
  )
}
