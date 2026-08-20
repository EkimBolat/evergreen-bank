import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Button, ErrorBanner, Input, Label } from './ui'
import { accountApi, scheduledTransferApi, ApiError } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatCurrency, formatAccountNumber } from '../lib/format'
import type { AccountResponse, Frequency, ScheduledTransferResponse } from '../lib/types'

export function ScheduledTransferPanel({ account }: { account: AccountResponse }) {
  const { token } = useAuth()
  const [scheduled, setScheduled] = useState<ScheduledTransferResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const isInitialLoadRef = useRef(true)

  const [recipientAccountNumber, setRecipientAccountNumber] = useState('')
  const [amount, setAmount] = useState('')
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState<number | null>(null)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const showSpinner = isInitialLoadRef.current

    async function load() {
      if (showSpinner) setLoading(true)
      setListError(null)
      try {
        const result = await scheduledTransferApi.list(token!)
        if (!cancelled) setScheduled(result)
      } catch (err) {
        if (!cancelled) setListError(friendlyErrorMessage(err, 'Otomatik transferler yüklenemedi.'))
      } finally {
        if (!cancelled) {
          setLoading(false)
          isInitialLoadRef.current = false
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [token, refreshKey])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)

    const normalizedNumber = recipientAccountNumber.replace(/\s+/g, '').toUpperCase()
    if (normalizedNumber === account.accountNumber) {
      setFormError('Kendi hesabınıza otomatik transfer planlayamazsınız.')
      return
    }

    if (!token) return
    setSubmitting(true)
    try {
      const recipient = await accountApi.getByAccountNumber(token, normalizedNumber)
      await scheduledTransferApi.create(token, {
        fromAccountId: account.id,
        toAccountId: recipient.id,
        amount: Number(amount),
        frequency,
      })
      setRecipientAccountNumber('')
      setAmount('')
      setFrequency('MONTHLY')
      setRefreshKey((k) => k + 1)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setFormError('Bu hesap numarasıyla eşleşen bir hesap bulunamadı.')
      } else {
        setFormError(friendlyErrorMessage(err, 'Otomatik transfer planlanamadı.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(id: number) {
    if (!token) return
    setCancellingId(id)
    try {
      await scheduledTransferApi.cancel(token, id)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setListError(friendlyErrorMessage(err, 'Otomatik transfer iptal edilemedi.'))
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <div>
      <form onSubmit={handleSubmit} className="space-y-4 border-b border-ink-100 p-5">
        {formError && <ErrorBanner message={formError} />}

        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <Label htmlFor="scheduled-recipient">Alıcı Hesap Numarası</Label>
            <Input
              id="scheduled-recipient"
              placeholder="TR1234567890"
              value={recipientAccountNumber}
              onChange={(e) => setRecipientAccountNumber(e.target.value)}
              required
            />
          </div>

          <div>
            <Label htmlFor="scheduled-amount">Tutar</Label>
            <Input
              id="scheduled-amount"
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0,00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </div>

          <div>
            <Label htmlFor="scheduled-frequency">Sıklık</Label>
            <select
              id="scheduled-frequency"
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as Frequency)}
              className="w-full rounded-xl border border-ink-200 bg-white px-4 py-2.5 text-sm text-ink-900 outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
            >
              <option value="DAILY">Günlük</option>
              <option value="WEEKLY">Haftalık</option>
              <option value="MONTHLY">Aylık</option>
            </select>
          </div>
        </div>

        <Button type="submit" loading={submitting}>
          Otomatik Transfer Planla
        </Button>
      </form>

      {listError && (
        <div className="p-5 pb-0">
          <ErrorBanner message={listError} />
        </div>
      )}

      {loading ? (
        <p className="px-5 py-8 text-center text-sm text-ink-400">Yükleniyor...</p>
      ) : scheduled.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-ink-400">Henüz planlanmış bir otomatik transfer yok.</p>
      ) : (
        <ul>
          {scheduled.map((item) => (
            <li
              key={item.id}
              className="flex items-center justify-between border-b border-ink-50 px-5 py-3.5 last:border-0"
            >
              <div>
                <p className="text-sm font-medium text-ink-900">
                  {formatAccountNumber(item.toAccountNumber)}
                </p>
                <p className="text-xs text-ink-400">
                  {frequencyLabel(item.frequency)} · Sonraki: {item.nextExecutionDate}
                  {!item.active && ' · İptal edildi'}
                </p>
              </div>

              <div className="flex items-center gap-3">
                <p className="text-sm font-semibold text-ink-900">{formatCurrency(item.amount)}</p>
                {item.active && (
                  <button
                    type="button"
                    onClick={() => handleCancel(item.id)}
                    disabled={cancellingId === item.id}
                    className="text-xs font-medium text-danger-600 hover:text-danger-700 disabled:opacity-50"
                  >
                    {cancellingId === item.id ? 'İptal ediliyor...' : 'İptal Et'}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function frequencyLabel(frequency: Frequency): string {
  switch (frequency) {
    case 'DAILY':
      return 'Günlük'
    case 'WEEKLY':
      return 'Haftalık'
    case 'MONTHLY':
      return 'Aylık'
    default:
      return frequency
  }
}
