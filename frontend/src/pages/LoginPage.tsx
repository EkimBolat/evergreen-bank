import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, ErrorBanner, Input, Label } from '../components/ui'
import { authApi, ApiError } from '../lib/api'
import { useAuth } from '../lib/use-auth'

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()

  const [nationalId, setNationalId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await authApi.login({ nationalId, password })

      if (response.twoFactorRequired && response.pendingToken) {
        navigate('/2fa/verify', { state: { pendingToken: response.pendingToken } })
        return
      }

      login({
        token: response.token,
        refreshToken: response.refreshToken,
        email: response.email,
        role: response.role,
        customerId: response.customerId,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Giriş yapılamadı. Lütfen tekrar deneyin.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="Tekrar hoş geldiniz" subtitle="Hesabınıza giriş yapın">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBanner message={error} />}

        <div>
          <Label htmlFor="nationalId">T.C. Kimlik No</Label>
          <Input
            id="nationalId"
            inputMode="numeric"
            maxLength={11}
            autoComplete="username"
            value={nationalId}
            onChange={(e) => setNationalId(e.target.value)}
            required
          />
        </div>

        <div>
          <Label htmlFor="password">Şifre</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <Button type="submit" fullWidth loading={loading}>
          Giriş Yap
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        Şubemizde hesabınız var ama giriş bilginiz yok mu?{' '}
        <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
          Giriş bilgisi oluşturun
        </Link>
      </p>
    </AuthLayout>
  )
}
