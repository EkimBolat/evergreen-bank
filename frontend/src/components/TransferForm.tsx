import { useState, type FormEvent } from 'react'
import { Button, ErrorBanner, Input, Label } from './ui'
import { accountApi, transferApi, ApiError } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatCurrency } from '../lib/format'
import type { AccountResponse } from '../lib/types'

export function TransferForm({ account, onSuccess }: { account: AccountResponse; onSuccess: () => void }) {
  const { token } = useAuth()
  const [recipientAccountNumber, setRecipientAccountNumber] = useState('')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSuccess(null)

    const normalizedNumber = recipientAccountNumber.replace(/\s+/g, '').toUpperCase()
    if (normalizedNumber === account.accountNumber) {
      setError('Kendi hesabınıza transfer yapamazsınız.')
      return
    }

    if (!token) return
    setLoading(true)
    try {
      const recipient = await accountApi.getByAccountNumber(token, normalizedNumber)
      const result = await transferApi.send(token, {
        fromAccountId: account.id,
        toAccountId: recipient.id,
        amount: Number(amount),
      })
      setSuccess(`${formatCurrency(result.amount)} başarıyla gönderildi.`)
      setRecipientAccountNumber('')
      setAmount('')
      onSuccess()
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError('Bu hesap numarasıyla eşleşen bir hesap bulunamadı.')
      } else {
        setError(friendlyErrorMessage(err, 'Transfer gerçekleştirilemedi.'))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 p-5">
      {error && <ErrorBanner message={error} />}
      {success && (
        <div className="rounded-xl border border-brand-500/20 bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700">
          {success}
        </div>
      )}

      <div>
        <Label htmlFor="recipient">Alıcı Hesap Numarası</Label>
        <Input
          id="recipient"
          placeholder="TR1234567890"
          value={recipientAccountNumber}
          onChange={(e) => setRecipientAccountNumber(e.target.value)}
          required
        />
      </div>

      <div>
        <Label htmlFor="amount">Tutar</Label>
        <Input
          id="amount"
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
        Gönder
      </Button>
    </form>
  )
}
