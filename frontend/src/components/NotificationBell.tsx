import { useEffect, useRef, useState } from 'react'
import { notificationApi } from '../lib/api'
import { useAuth } from '../lib/use-auth'
import type { NotificationResponse } from '../lib/types'

const POLL_INTERVAL_MS = 20000

export function NotificationBell() {
  const { token } = useAuth()
  const [open, setOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState<NotificationResponse[]>([])
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!token) return
    let cancelled = false

    async function poll() {
      try {
        const count = await notificationApi.unreadCount(token!)
        if (!cancelled) setUnreadCount(count)
      } catch {
        // transient polling errors are not worth surfacing to the user
      }
    }

    poll()
    const interval = setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [token])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  async function togglePanel() {
    const next = !open
    setOpen(next)
    if (next && token) {
      setLoading(true)
      try {
        const page = await notificationApi.list(token, 0, 8)
        setNotifications(page.content)
      } finally {
        setLoading(false)
      }
    }
  }

  async function handleMarkAllRead() {
    if (!token) return
    await notificationApi.markAllAsRead(token)
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
    setUnreadCount(0)
  }

  async function handleMarkRead(id: number) {
    if (!token) return
    await notificationApi.markAsRead(token, id)
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)))
    setUnreadCount((prev) => Math.max(0, prev - 1))
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={togglePanel}
        className="relative flex h-10 w-10 items-center justify-center rounded-full text-ink-600 transition hover:bg-ink-100"
        aria-label="Bildirimler"
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger-500 px-1 text-[10px] font-bold text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 rounded-2xl border border-ink-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-ink-100 px-4 py-3">
            <span className="text-sm font-semibold text-ink-900">Bildirimler</span>
            {notifications.some((n) => !n.read) && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                Tümünü okundu işaretle
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            {loading && <p className="px-4 py-6 text-center text-sm text-ink-400">Yükleniyor...</p>}
            {!loading && notifications.length === 0 && (
              <p className="px-4 py-6 text-center text-sm text-ink-400">Henüz bildirim yok</p>
            )}
            {!loading &&
              notifications.map((n) => (
                <button
                  key={n.id}
                  type="button"
                  onClick={() => !n.read && handleMarkRead(n.id)}
                  className={`block w-full border-b border-ink-50 px-4 py-3 text-left transition last:border-0 hover:bg-ink-50 ${
                    n.read ? '' : 'bg-brand-50/50'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-sm font-semibold text-ink-900">{n.title}</span>
                    {!n.read && <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-brand-500" />}
                  </div>
                  <p className="mt-0.5 text-xs text-ink-500">{n.message}</p>
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  )
}

function BellIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M13.73 21a2 2 0 01-3.46 0"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
