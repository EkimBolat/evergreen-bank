import { useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/use-auth'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-1.5 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-ink-600 hover:bg-ink-100'
  }`

const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded-lg px-3 py-2.5 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-ink-600 hover:bg-ink-100'
  }`

export function Layout({ children }: { children: ReactNode }) {
  const { email, role, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  function handleLogout() {
    setMenuOpen(false)
    logout()
    navigate('/login', { replace: true })
  }

  const links =
    role === 'ADMIN'
      ? [{ to: '/admin', label: 'Admin' }]
      : [
          { to: '/dashboard', label: 'Panel' },
          { to: '/cards', label: 'Kartlarım' },
          { to: '/nature', label: 'Doğa Puanım' },
          { to: '/settings', label: 'Ayarlar' },
        ]

  return (
    <div className="min-h-screen bg-ink-50">
      <header className="border-b border-ink-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3.5 sm:px-6">
          <div className="flex min-w-0 items-center gap-6">
            <Logo size={32} withWordmark />
            <nav className="hidden items-center gap-1 md:flex">
              {links.map((link) => (
                <NavLink key={link.to} to={link.to} className={navLinkClass}>
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex shrink-0 items-center gap-1 sm:gap-3">
            <NotificationBell />
            <div className="hidden h-6 w-px bg-ink-200 md:block" />
            <div className="hidden items-center gap-2.5 md:flex">
              <span className="text-sm text-ink-600">{email}</span>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-600 transition hover:bg-ink-100"
              >
                Çıkış Yap
              </button>
            </div>
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              className="rounded-lg p-2 text-ink-600 transition hover:bg-ink-100 md:hidden"
              aria-label={menuOpen ? 'Menüyü kapat' : 'Menüyü aç'}
              aria-expanded={menuOpen}
            >
              <MenuIcon open={menuOpen} />
            </button>
          </div>
        </div>

        {menuOpen && (
          <div className="border-t border-ink-100 px-4 py-3 md:hidden">
            <nav className="flex flex-col gap-1">
              {links.map((link) => (
                <NavLink key={link.to} to={link.to} className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                  {link.label}
                </NavLink>
              ))}
            </nav>
            <div className="mt-3 flex items-center justify-between border-t border-ink-100 pt-3">
              <span className="truncate text-sm text-ink-600">{email}</span>
              <button
                type="button"
                onClick={handleLogout}
                className="shrink-0 rounded-lg px-3 py-1.5 text-sm font-medium text-ink-600 transition hover:bg-ink-100"
              >
                Çıkış Yap
              </button>
            </div>
          </div>
        )}
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">{children}</main>
    </div>
  )
}

function MenuIcon({ open }: { open: boolean }) {
  if (open) {
    return (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
    )
  }
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}
