import { useEffect, useState } from 'react'
import { DepositWithdrawForm } from '../components/DepositWithdrawForm'
import { Layout } from '../components/Layout'
import { TransferForm } from '../components/TransferForm'
import { Card } from '../components/ui'
import { accountApi, transactionApi, ApiError } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { accountTypeLabel, formatAccountNumber, formatCurrency, formatDate } from '../lib/format'
import type { AccountResponse, TransactionResponse } from '../lib/types'

export function DashboardPage() {
  const { token } = useAuth()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [transactions, setTransactions] = useState<TransactionResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)
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

        const history = await transactionApi.getHistory(token!, acc.id, 0, 8)
        if (cancelled) return
        setTransactions(history.content)
      } catch (err) {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
        } else {
          setError(friendlyErrorMessage(err, 'Veriler yüklenemedi.'))
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

  return (
    <Layout>
      {loading && <p className="text-sm text-ink-500">Yükleniyor...</p>}

      {!loading && notFound && (
        <Card className="p-8 text-center">
          <p className="text-sm text-ink-600">
            Henüz bağlı bir hesabınız bulunmuyor. Hesap açılışı için lütfen şubenizle iletişime geçin.
          </p>
        </Card>
      )}

      {!loading && error && (
        <Card className="p-8 text-center">
          <p className="text-sm text-danger-600">{error}</p>
        </Card>
      )}

      {!loading && account && (
        <div className="space-y-6">
          <div className="rounded-2xl bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white shadow-sm sm:p-8">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-brand-100">{accountTypeLabel(account.accountType)}</p>
                <p className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
                  {formatCurrency(account.balance)}
                </p>
              </div>
              {account.interestRate != null && (
                <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold">
                  %{account.interestRate} faiz
                </span>
              )}
            </div>
            <p className="mt-6 font-mono text-sm tracking-widest text-brand-100">
              {formatAccountNumber(account.accountNumber)}
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Card>
              <div className="border-b border-ink-100 px-5 py-4">
                <h2 className="text-sm font-semibold text-ink-900">Para Transferi</h2>
              </div>
              <TransferForm account={account} onSuccess={() => setRefreshKey((k) => k + 1)} />
            </Card>

            <Card>
              <div className="border-b border-ink-100 px-5 py-4">
                <h2 className="text-sm font-semibold text-ink-900">Para Yatır / Çek</h2>
              </div>
              <DepositWithdrawForm account={account} onSuccess={() => setRefreshKey((k) => k + 1)} />
            </Card>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Card className="p-5">
              <p className="text-xs font-medium uppercase tracking-wide text-ink-400">Günlük Çekim Limiti</p>
              <p className="mt-1.5 text-lg font-semibold text-ink-900">{formatCurrency(account.dailyLimit)}</p>
            </Card>
            <Card className="p-5">
              <p className="text-xs font-medium uppercase tracking-wide text-ink-400">Aylık Çekim Limiti</p>
              <p className="mt-1.5 text-lg font-semibold text-ink-900">{formatCurrency(account.monthlyLimit)}</p>
            </Card>
          </div>

          <Card>
            <div className="border-b border-ink-100 px-5 py-4">
              <h2 className="text-sm font-semibold text-ink-900">Son İşlemler</h2>
            </div>

            {transactions.length === 0 ? (
              <p className="px-5 py-8 text-center text-sm text-ink-400">Henüz işlem yok</p>
            ) : (
              <ul>
                {transactions.map((tx) => (
                  <TransactionRow key={tx.id} transaction={tx} />
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}
    </Layout>
  )
}

function TransactionRow({ transaction }: { transaction: TransactionResponse }) {
  const isCredit = transaction.type === 'DEPOSIT' || transaction.type === 'INTEREST'

  return (
    <li className="flex items-center justify-between border-b border-ink-50 px-5 py-3.5 last:border-0">
      <div className="flex items-center gap-3">
        <span
          className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold ${
            isCredit ? 'bg-brand-100 text-brand-700' : 'bg-ink-100 text-ink-600'
          }`}
        >
          {isCredit ? '↓' : '↑'}
        </span>
        <div>
          <p className="text-sm font-medium text-ink-900">{transactionLabel(transaction.type)}</p>
          <p className="text-xs text-ink-400">{formatDate(transaction.timestamp)}</p>
        </div>
      </div>

      <div className="text-right">
        <p className={`text-sm font-semibold ${isCredit ? 'text-brand-700' : 'text-ink-900'}`}>
          {isCredit ? '+' : '-'}
          {formatCurrency(transaction.amount)}
        </p>
        <p className="text-xs text-ink-400">Bakiye: {formatCurrency(transaction.balanceAfter)}</p>
      </div>
    </li>
  )
}

function transactionLabel(type: TransactionResponse['type']): string {
  switch (type) {
    case 'DEPOSIT':
      return 'Para Yatırma'
    case 'WITHDRAWAL':
      return 'Para Çekme'
    case 'INTEREST':
      return 'Faiz Ödemesi'
    default:
      return type
  }
}
