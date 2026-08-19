import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/use-auth'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'

export function Layout({ children }: { children: ReactNode }) {
  const { email, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-ink-50">
      <header className="border-b border-ink-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3.5">
          <Logo size={36} withWordmark />

          <div className="flex items-center gap-3">
            <NotificationBell />
            <div className="h-6 w-px bg-ink-200" />
            <div className="flex items-center gap-2.5">
              <span className="hidden text-sm text-ink-600 sm:inline">{email}</span>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-600 transition hover:bg-ink-100"
              >
                Çıkış Yap
              </button>
            </div>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
    </div>
  )
}
