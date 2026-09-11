import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, ErrorBanner, Input, Label } from '../components/ui'
import { authApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('Şifreler eşleşmiyor.')
      return
    }

    setLoading(true)
    try {
      await authApi.resetPassword({ token, newPassword })
      setDone(true)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Şifre sıfırlanamadı.'))
    } finally {
      setLoading(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Geçersiz link" subtitle="Sıfırlama kodu bulunamadı">
        <p className="text-center text-sm text-ink-500">
          <Link to="/forgot-password" className="font-semibold text-brand-600 hover:text-brand-700">
            Yeniden sıfırlama linki oluşturun
          </Link>
        </p>
      </AuthLayout>
    )
  }

  if (done) {
    return (
      <AuthLayout title="Şifreniz sıfırlandı" subtitle="Artık yeni şifrenizle giriş yapabilirsiniz">
        <Link to="/login">
          <Button fullWidth>Giriş Yap</Button>
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Yeni şifre belirleyin" subtitle="Hesabınız için yeni bir şifre girin">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBanner message={error} />}

        <div>
          <Label htmlFor="newPassword">Yeni Şifre</Label>
          <Input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
        </div>

        <div>
          <Label htmlFor="confirmPassword">Yeni Şifre (Tekrar)</Label>
          <Input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
        </div>

        <Button type="submit" fullWidth loading={loading}>
          Şifreyi Sıfırla
        </Button>
      </form>
    </AuthLayout>
  )
}
