import { useState, type FormEvent } from 'react'
import { Button, ErrorBanner, Input, Label } from './ui'
import { transactionApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatCurrency } from '../lib/format'
import type { AccountResponse } from '../lib/types'

type Mode = 'DEPOSIT' | 'WITHDRAWAL'

export function DepositWithdrawForm({ account, onSuccess }: { account: AccountResponse; onSuccess: () => void }) {
  const { token } = useAuth()
  const [mode, setMode] = useState<Mode>('DEPOSIT')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  function selectMode(next: Mode) {
    setMode(next)
    setError(null)
    setSuccess(null)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSuccess(null)

    if (!token) return
    setLoading(true)
    try {
      const numericAmount = Number(amount)
      const result =
        mode === 'DEPOSIT'
          ? await transactionApi.deposit(token, account.id, numericAmount)
          : await transactionApi.withdraw(token, account.id, numericAmount)

      setSuccess(
        mode === 'DEPOSIT'
          ? `${formatCurrency(result.amount)} hesabınıza yatırıldı.`
          : `${formatCurrency(result.amount)} hesabınızdan çekildi.`,
      )
      setAmount('')
      onSuccess()
    } catch (err) {
      setError(friendlyErrorMessage(err, 'İşlem gerçekleştirilemedi.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-5">
      <div className="mb-4 flex gap-1 rounded-xl bg-ink-100 p-1">
        <button
          type="button"
          onClick={() => selectMode('DEPOSIT')}
          className={`flex-1 rounded-lg py-1.5 text-sm font-semibold transition ${
            mode === 'DEPOSIT' ? 'bg-white text-brand-700 shadow-sm' : 'text-ink-500'
          }`}
        >
          Para Yatır
        </button>
        <button
          type="button"
          onClick={() => selectMode('WITHDRAWAL')}
          className={`flex-1 rounded-lg py-1.5 text-sm font-semibold transition ${
            mode === 'WITHDRAWAL' ? 'bg-white text-brand-700 shadow-sm' : 'text-ink-500'
          }`}
        >
          Para Çek
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBanner message={error} />}
        {success && (
          <div className="rounded-xl border border-brand-500/20 bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700">
            {success}
          </div>
        )}

        <div>
          <Label htmlFor="txAmount">Tutar</Label>
          <Input
            id="txAmount"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="0,00"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
        </div>

        <Button type="submit" fullWidth loading={loading}>
          {mode === 'DEPOSIT' ? 'Yatır' : 'Çek'}
        </Button>
      </form>
    </div>
  )
}
