import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, ErrorBanner, Input, Label } from '../components/ui'
import { authApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'

export function RegisterPage() {
  const navigate = useNavigate()
  const { login } = useAuth()

  const [nationalId, setNationalId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (password !== confirmPassword) {
      setError('Şifreler eşleşmiyor.')
      return
    }

    setLoading(true)
    try {
      const response = await authApi.register({ nationalId, email, password })
      login({
        token: response.token,
        refreshToken: response.refreshToken,
        email: response.email,
        role: response.role,
        customerId: response.customerId,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kayıt oluşturulamadı.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="Giriş bilginizi oluşturun" subtitle="Şubemizde kaydı olan müşteriler için">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBanner message={error} />}

        <p className="rounded-xl bg-ink-50 px-3.5 py-2.5 text-xs leading-relaxed text-ink-500">
          Bu form yeni müşteri kaydı oluşturmaz. Şubemizde daha önce hesabı açılmış müşterilerimiz,
          T.C. Kimlik No'larıyla dijital giriş bilgisi (email + şifre) oluşturabilir.
        </p>

        <div>
          <Label htmlFor="nationalId">T.C. Kimlik No</Label>
          <Input
            id="nationalId"
            inputMode="numeric"
            maxLength={11}
            value={nationalId}
            onChange={(e) => setNationalId(e.target.value)}
            required
          />
        </div>

        <div>
          <Label htmlFor="email">E-posta</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div>
          <Label htmlFor="password">Şifre</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <div>
          <Label htmlFor="confirmPassword">Şifre (Tekrar)</Label>
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
          Giriş Bilgisi Oluştur
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        Zaten giriş bilginiz var mı?{' '}
        <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
          Giriş yapın
        </Link>
      </p>
    </AuthLayout>
  )
}
