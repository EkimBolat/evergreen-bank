import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, ErrorBanner, Input, Label } from '../components/ui'
import { authApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'

interface LocationState {
  pendingToken?: string
}

export function TwoFactorPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const pendingToken = (location.state as LocationState | null)?.pendingToken

  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!pendingToken) {
    return <Navigate to="/login" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await authApi.verifyTwoFactor({ pendingToken: pendingToken!, code })
      login({
        token: response.token,
        refreshToken: response.refreshToken,
        email: response.email,
        role: response.role,
        customerId: response.customerId,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Doğrulama başarısız oldu.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="İki adımlı doğrulama" subtitle="Authenticator uygulamanızdaki 6 haneli kodu girin">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBanner message={error} />}

        <div>
          <Label htmlFor="code">Doğrulama Kodu</Label>
          <Input
            id="code"
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            className="text-center text-lg tracking-[0.5em]"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            autoFocus
            required
          />
        </div>

        <Button type="submit" fullWidth loading={loading} disabled={code.length !== 6}>
          Doğrula
        </Button>
      </form>
    </AuthLayout>
  )
}
