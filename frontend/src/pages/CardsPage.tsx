import { useEffect, useState } from 'react'
import { Layout } from '../components/Layout'
import { Button, Card, ErrorBanner } from '../components/ui'
import { accountApi, cardApi, ApiError } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import type { AccountResponse, CardIssuedResponse, CardResponse } from '../lib/types'

export function CardsPage() {
  const { token } = useAuth()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [cards, setCards] = useState<CardResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [issuing, setIssuing] = useState(false)
  const [revealed, setRevealed] = useState<CardIssuedResponse | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const isInitialLoad = refreshKey === 0

    async function load() {
      if (isInitialLoad) setLoading(true)
      setError(null)
      setNotFound(false)
      try {
        const acc = await accountApi.getMyAccount(token!)
        if (cancelled) return
        setAccount(acc)
        const cardList = await cardApi.list(token!, acc.id)
        if (cancelled) return
        setCards(cardList)
      } catch (err) {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
        } else {
          setError(friendlyErrorMessage(err, 'Kartlar yüklenemedi.'))
        }
      } finally {
        if (!cancelled && isInitialLoad) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [token, refreshKey])

  async function handleIssue() {
    if (!token || !account) return
    setIssuing(true)
    setError(null)
    try {
      const issued = await cardApi.issue(token, account.id)
      setRevealed(issued)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kart çıkarılamadı.'))
    } finally {
      setIssuing(false)
    }
  }

  async function handleToggle(card: CardResponse) {
    if (!token) return
    setError(null)
    try {
      if (card.status === 'ACTIVE') {
        await cardApi.block(token, card.id)
      } else {
        await cardApi.activate(token, card.id)
      }
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'İşlem gerçekleştirilemedi.'))
    }
  }

  return (
    <Layout>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-lg font-bold text-ink-900">Kartlarım</h1>
        {account && (
          <Button onClick={handleIssue} loading={issuing}>
            Yeni Kart Çıkar
          </Button>
        )}
      </div>

      {loading && <p className="text-sm text-ink-500">Yükleniyor...</p>}

      {!loading && notFound && (
        <Card className="p-8 text-center">
          <p className="text-sm text-ink-600">
            Henüz bağlı bir hesabınız bulunmuyor. Hesap açılışı için lütfen şubenizle iletişime geçin.
          </p>
        </Card>
      )}

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} />
        </div>
      )}

      {!loading && account && (
        <>
          {revealed && (
            <div className="mb-6 rounded-2xl border border-warning-500/30 bg-warning-50 p-5">
              <p className="mb-3 text-sm font-semibold text-ink-900">
                Kartınız oluşturuldu — kart numarası ve CVV yalnızca şimdi gösterilecek, not alın.
              </p>
              <div className="flex flex-wrap items-center gap-6 font-mono text-sm text-ink-900">
                <span>{formatCardNumber(revealed.cardNumber)}</span>
                <span>CVV: {revealed.cvv}</span>
                <span>SKT: {formatExpiry(revealed.expiryDate)}</span>
              </div>
              <button
                type="button"
                onClick={() => setRevealed(null)}
                className="mt-3 text-xs font-medium text-ink-500 hover:text-ink-700"
              >
                Kapat
              </button>
            </div>
          )}

          {cards.length === 0 ? (
            <Card className="p-8 text-center">
              <p className="text-sm text-ink-500">Henüz bir kartınız yok.</p>
            </Card>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2">
              {cards.map((card) => (
                <CardTile key={card.id} card={card} onToggle={() => handleToggle(card)} />
              ))}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}

function CardTile({ card, onToggle }: { card: CardResponse; onToggle: () => void }) {
  const isActive = card.status === 'ACTIVE'

  return (
    <div>
      <div
        className={`rounded-2xl p-6 text-white shadow-sm ${
          isActive ? 'bg-gradient-to-br from-brand-600 to-brand-800' : 'bg-ink-400'
        }`}
      >
        <div className="flex items-start justify-between">
          <span className="text-xs font-medium uppercase tracking-wider text-white/70">EverGreen Bank</span>
          <span className="rounded-full bg-white/20 px-2.5 py-0.5 text-xs font-semibold">
            {isActive ? 'Aktif' : 'Bloke'}
          </span>
        </div>
        <p className="mt-8 font-mono text-lg tracking-widest">{card.maskedCardNumber}</p>
        <div className="mt-4 flex items-end justify-between">
          <div>
            <p className="text-[10px] uppercase tracking-wide text-white/60">Kart Sahibi</p>
            <p className="text-sm font-medium">{card.cardHolderName}</p>
          </div>
          <div className="text-right">
            <p className="text-[10px] uppercase tracking-wide text-white/60">SKT</p>
            <p className="text-sm font-medium">{formatExpiry(card.expiryDate)}</p>
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={onToggle}
        className="mt-2 w-full rounded-xl border border-ink-200 bg-white py-2 text-sm font-medium text-ink-700 transition hover:bg-ink-50"
      >
        {isActive ? 'Kartı Bloke Et' : 'Kartı Aktif Et'}
      </button>
    </div>
  )
}

function formatCardNumber(number: string): string {
  return number.replace(/(.{4})/g, '$1 ').trim()
}

function formatExpiry(isoDate: string): string {
  const [year, month] = isoDate.split('-')
  return `${month}/${year.slice(2)}`
}
