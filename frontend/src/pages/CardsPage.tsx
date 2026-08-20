import { useEffect, useState, type FormEvent } from 'react'
import { Layout } from '../components/Layout'
import { Button, Card, ErrorBanner, Input, Label } from '../components/ui'
import { accountApi, cardApi, creditCardApi, ApiError } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatCurrency } from '../lib/format'
import type {
  AccountResponse,
  CardIssuedResponse,
  CardResponse,
  CardStatus,
  CardType,
  CreditCardStatementResponse,
  CreditCardTransactionResponse,
} from '../lib/types'

type CardAction = 'charge' | 'pay' | 'statements'

const CARD_STATUS_ORDER: Record<CardStatus, number> = { ACTIVE: 0, BLOCKED: 1, CANCELLED: 2 }

export function CardsPage() {
  const { token } = useAuth()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [cards, setCards] = useState<CardResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [revealed, setRevealed] = useState<CardIssuedResponse | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const [issuePanelOpen, setIssuePanelOpen] = useState(false)
  const [issueType, setIssueType] = useState<CardType>('DEBIT')
  const [issueCreditLimit, setIssueCreditLimit] = useState('')
  const [issuing, setIssuing] = useState(false)

  const [activeAction, setActiveAction] = useState<{ cardId: number; action: CardAction } | null>(null)

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

  async function handleIssue(event: FormEvent) {
    event.preventDefault()
    if (!token || !account) return
    setIssuing(true)
    setError(null)
    try {
      const limit = issueType === 'CREDIT' ? Number(issueCreditLimit) : undefined
      const issued = await cardApi.issue(token, account.id, issueType, limit)
      setRevealed(issued)
      setIssuePanelOpen(false)
      setIssueCreditLimit('')
      setIssueType('DEBIT')
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

  async function handleCancel(card: CardResponse) {
    if (!token) return
    if (!window.confirm('Bu kartı kalıcı olarak iptal etmek istediğinize emin misiniz? Bu işlem geri alınamaz.')) {
      return
    }
    setError(null)
    try {
      await cardApi.cancel(token, card.id)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kart iptal edilemedi.'))
    }
  }

  return (
    <Layout>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-lg font-bold text-ink-900">Kartlarım</h1>
        {account && (
          <Button onClick={() => setIssuePanelOpen((v) => !v)}>
            {issuePanelOpen ? 'Vazgeç' : 'Yeni Kart Çıkar'}
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
          {issuePanelOpen && (
            <Card className="mb-6 p-5">
              <form onSubmit={handleIssue} className="space-y-4">
                <div>
                  <Label htmlFor="issueType">Kart Tipi</Label>
                  <div className="flex gap-1 rounded-xl bg-ink-100 p-1">
                    <button
                      type="button"
                      onClick={() => setIssueType('DEBIT')}
                      className={`flex-1 rounded-lg py-1.5 text-sm font-semibold transition ${
                        issueType === 'DEBIT' ? 'bg-white text-brand-700 shadow-sm' : 'text-ink-500'
                      }`}
                    >
                      Banka Kartı
                    </button>
                    <button
                      type="button"
                      onClick={() => setIssueType('CREDIT')}
                      className={`flex-1 rounded-lg py-1.5 text-sm font-semibold transition ${
                        issueType === 'CREDIT' ? 'bg-white text-brand-700 shadow-sm' : 'text-ink-500'
                      }`}
                    >
                      Kredi Kartı
                    </button>
                  </div>
                </div>

                {issueType === 'CREDIT' && (
                  <div>
                    <Label htmlFor="creditLimit">Kredi Limiti</Label>
                    <Input
                      id="creditLimit"
                      type="number"
                      min="1"
                      step="1"
                      placeholder="10000"
                      value={issueCreditLimit}
                      onChange={(e) => setIssueCreditLimit(e.target.value)}
                      required
                    />
                  </div>
                )}

                <Button type="submit" loading={issuing}>
                  Kartı Çıkar
                </Button>
              </form>
            </Card>
          )}

          {revealed && (
            <div className="mb-6 rounded-2xl border border-warning-500/30 bg-warning-50 p-5">
              <p className="mb-3 text-sm font-semibold text-ink-900">
                Kartınız oluşturuldu — kart numarası ve CVV yalnızca şimdi gösterilecek, not alın.
              </p>
              <div className="flex flex-wrap items-center gap-6 font-mono text-sm text-ink-900">
                <span>{formatCardNumber(revealed.cardNumber)}</span>
                <span>CVV: {revealed.cvv}</span>
                <span>SKT: {formatExpiry(revealed.expiryDate)}</span>
                {revealed.cardType === 'CREDIT' && <span>Limit: {formatCurrency(revealed.creditLimit ?? 0)}</span>}
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
              {[...cards]
                .sort((a, b) => CARD_STATUS_ORDER[a.status] - CARD_STATUS_ORDER[b.status])
                .map((card) => (
                <CardTile
                  key={card.id}
                  card={card}
                  onToggle={() => handleToggle(card)}
                  onCancel={() => handleCancel(card)}
                  activeAction={activeAction?.cardId === card.id ? activeAction.action : null}
                  onSelectAction={(action) =>
                    setActiveAction((prev) =>
                      prev?.cardId === card.id && prev.action === action ? null : { cardId: card.id, action },
                    )
                  }
                  onMutated={() => setRefreshKey((k) => k + 1)}
                />
              ))}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}

function CardTile({
  card,
  onToggle,
  onCancel,
  activeAction,
  onSelectAction,
  onMutated,
}: {
  card: CardResponse
  onToggle: () => void
  onCancel: () => void
  activeAction: CardAction | null
  onSelectAction: (action: CardAction) => void
  onMutated: () => void
}) {
  const isActive = card.status === 'ACTIVE'
  const isCancelled = card.status === 'CANCELLED'
  const isCredit = card.cardType === 'CREDIT'

  return (
    <div>
      <div
        className={`rounded-2xl p-6 text-white shadow-sm ${
          isCancelled ? 'bg-ink-300' : isActive ? 'bg-gradient-to-br from-brand-600 to-brand-800' : 'bg-ink-400'
        }`}
      >
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs font-medium uppercase tracking-wider text-white/70">EverGreen Bank</span>
            <span className="ml-2 text-xs font-medium uppercase tracking-wider text-white/50">
              {isCredit ? 'Kredi Kartı' : 'Banka Kartı'}
            </span>
          </div>
          <span className="rounded-full bg-white/20 px-2.5 py-0.5 text-xs font-semibold">
            {isCancelled ? 'İptal Edildi' : isActive ? 'Aktif' : 'Bloke'}
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

        {isCredit && card.creditLimit != null && (
          <div className="mt-4 grid grid-cols-2 gap-3 border-t border-white/20 pt-3 text-xs">
            <div>
              <p className="text-white/60">Borç</p>
              <p className="font-semibold">{formatCurrency(card.currentBalance ?? 0)}</p>
            </div>
            <div>
              <p className="text-white/60">Kullanılabilir Limit</p>
              <p className="font-semibold">{formatCurrency(card.availableCredit ?? 0)}</p>
            </div>
          </div>
        )}
      </div>

      {!isCancelled && (
        <div className="mt-2 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={onToggle}
            className="rounded-xl border border-ink-200 bg-white py-2 text-sm font-medium text-ink-700 transition hover:bg-ink-50"
          >
            {isActive ? 'Bloke Et' : 'Aktif Et'}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-xl border border-danger-500/30 bg-white py-2 text-sm font-medium text-danger-600 transition hover:bg-danger-50"
          >
            İptal Et
          </button>
        </div>
      )}

      {isCredit && !isCancelled && (
        <>
          <div className="mt-2 grid grid-cols-3 gap-2">
            <ActionTab label="Harcama Yap" active={activeAction === 'charge'} onClick={() => onSelectAction('charge')} />
            <ActionTab label="Öde" active={activeAction === 'pay'} onClick={() => onSelectAction('pay')} />
            <ActionTab label="Ekstreler" active={activeAction === 'statements'} onClick={() => onSelectAction('statements')} />
          </div>

          {activeAction === 'charge' && <ChargePanel card={card} onDone={onMutated} />}
          {activeAction === 'pay' && <PayPanel card={card} onDone={onMutated} />}
          {activeAction === 'statements' && <StatementsPanel cardId={card.id} />}
        </>
      )}
    </div>
  )
}

function ActionTab({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-xl border py-1.5 text-xs font-medium transition ${
        active ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-ink-200 bg-white text-ink-600 hover:bg-ink-50'
      }`}
    >
      {label}
    </button>
  )
}

function ChargePanel({ card, onDone }: { card: CardResponse; onDone: () => void }) {
  const { token } = useAuth()
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setSuccess(null)
    setLoading(true)
    try {
      const result = await creditCardApi.charge(token, card.id, Number(amount), description || undefined)
      setSuccess(`${formatCurrency(result.amount)} harcandı.`)
      setAmount('')
      setDescription('')
      onDone()
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Harcama gerçekleştirilemedi.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-2 space-y-2 rounded-xl border border-ink-200 bg-white p-4">
      {error && <ErrorBanner message={error} />}
      {success && <p className="text-xs font-medium text-brand-700">{success}</p>}
      <Input
        type="number"
        min="0.01"
        step="0.01"
        placeholder="Tutar"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        required
      />
      <Input
        placeholder="Açıklama (opsiyonel)"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <Button type="submit" fullWidth loading={loading}>
        Harcama Yap
      </Button>
    </form>
  )
}

function PayPanel({ card, onDone }: { card: CardResponse; onDone: () => void }) {
  const { token } = useAuth()
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setSuccess(null)
    setLoading(true)
    try {
      const result = await creditCardApi.pay(token, card.id, Number(amount))
      setSuccess(`${formatCurrency(result.amount)} ödendi.`)
      setAmount('')
      onDone()
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Ödeme gerçekleştirilemedi.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-2 space-y-2 rounded-xl border border-ink-200 bg-white p-4">
      {error && <ErrorBanner message={error} />}
      {success && <p className="text-xs font-medium text-brand-700">{success}</p>}
      <p className="text-xs text-ink-500">Ödeme, bağlı hesabınızdan düşülecektir.</p>
      <Input
        type="number"
        min="0.01"
        step="0.01"
        placeholder="Tutar"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        required
      />
      <Button type="submit" fullWidth loading={loading}>
        Kartı Öde
      </Button>
    </form>
  )
}

function StatementsPanel({ cardId }: { cardId: number }) {
  const { token } = useAuth()
  const [statements, setStatements] = useState<CreditCardStatementResponse[] | null>(null)
  const [transactions, setTransactions] = useState<CreditCardTransactionResponse[] | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!token) return
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const [statementList, transactionList] = await Promise.all([
          creditCardApi.getStatements(token!, cardId),
          creditCardApi.getTransactions(token!, cardId),
        ])
        if (!cancelled) {
          setStatements(statementList)
          setTransactions(transactionList)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [token, cardId])

  return (
    <div className="mt-2 rounded-xl border border-ink-200 bg-white p-4 text-sm">
      {loading && <p className="text-xs text-ink-400">Yükleniyor...</p>}

      {!loading && (
        <>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-400">Ekstreler</p>
          {statements && statements.length === 0 ? (
            <p className="text-xs text-ink-400">Henüz ekstre oluşturulmadı.</p>
          ) : (
            <ul className="mb-3 space-y-1.5">
              {statements?.map((s) => (
                <li key={s.id} className="flex items-center justify-between text-xs">
                  <span className="text-ink-500">
                    {s.statementDate} · Son ödeme {s.dueDate}
                  </span>
                  <span className="font-medium text-ink-900">
                    {formatCurrency(s.statementBalance)} {s.paidInFull ? '✓' : `(min ${formatCurrency(s.minimumPayment)})`}
                  </span>
                </li>
              ))}
            </ul>
          )}

          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-400">Kart İşlemleri</p>
          {transactions && transactions.length === 0 ? (
            <p className="text-xs text-ink-400">Henüz işlem yok.</p>
          ) : (
            <ul className="space-y-1.5">
              {transactions?.map((tx) => (
                <li key={tx.id} className="flex items-center justify-between text-xs">
                  <span className="text-ink-500">{transactionLabel(tx.type)}{tx.description ? ` — ${tx.description}` : ''}</span>
                  <span className={`font-medium ${tx.type === 'PAYMENT' ? 'text-brand-700' : 'text-ink-900'}`}>
                    {tx.type === 'PAYMENT' ? '-' : '+'}
                    {formatCurrency(tx.amount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}

function transactionLabel(type: CreditCardTransactionResponse['type']): string {
  switch (type) {
    case 'PURCHASE':
      return 'Harcama'
    case 'PAYMENT':
      return 'Ödeme'
    case 'INTEREST_CHARGE':
      return 'Gecikme Faizi'
    default:
      return type
  }
}

function formatCardNumber(number: string): string {
  return number.replace(/(.{4})/g, '$1 ').trim()
}

function formatExpiry(isoDate: string): string {
  const [year, month] = isoDate.split('-')
  return `${month}/${year.slice(2)}`
}
