import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, ErrorBanner, Input, Label } from '../components/ui'
import { authApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'

export function ForgotPasswordPage() {
  const [nationalId, setNationalId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [resetToken, setResetToken] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const response = await authApi.forgotPassword({ nationalId })
      setResetToken(response.resetToken)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Sıfırlama linki oluşturulamadı.'))
    } finally {
      setLoading(false)
    }
  }

  if (resetToken) {
    return (
      <AuthLayout title="Sıfırlama linki hazır" subtitle="Demo modu">
        <div className="space-y-4">
          <p className="rounded-xl bg-ink-50 px-3.5 py-2.5 text-xs leading-relaxed text-ink-500">
            Bu bir demo ortamı olduğu için e-posta gönderimi yapılandırılmadı — gerçek bir üründe bu link
            e-posta adresinize gönderilirdi. Aşağıdaki linke tıklayarak şifrenizi sıfırlayabilirsiniz.
          </p>

          <Link
            to={`/reset-password?token=${encodeURIComponent(resetToken)}`}
            className="block break-all rounded-xl border border-brand-500/20 bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700 hover:bg-brand-100"
          >
            Şifremi sıfırla →
          </Link>

          <div>
            <Label>Link açılmıyorsa bu kodu elle girin:</Label>
            <p className="break-all rounded-lg bg-ink-50 px-3 py-2 font-mono text-xs text-ink-700">{resetToken}</p>
          </div>

          <p className="text-xs text-ink-400">Bu link 30 dakika içinde geçerliliğini yitirir.</p>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Şifremi unuttum" subtitle="T.C. Kimlik numaranızı girin">
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

        <Button type="submit" fullWidth loading={loading}>
          Sıfırlama Linki Oluştur
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        Şifrenizi hatırladınız mı?{' '}
        <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
          Giriş yapın
        </Link>
      </p>
    </AuthLayout>
  )
}
