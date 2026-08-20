import QRCode from 'qrcode'
import { useEffect, useState, type FormEvent } from 'react'
import { Layout } from '../components/Layout'
import { Badge, Button, Card, ErrorBanner, Input, Label } from '../components/ui'
import { twoFactorApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'

export function SettingsPage() {
  const { token } = useAuth()
  const [enabled, setEnabled] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [setupSecret, setSetupSecret] = useState<string | null>(null)
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null)
  const [startingSetup, setStartingSetup] = useState(false)

  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const status = await twoFactorApi.status(token!)
        if (!cancelled) setEnabled(status.enabled)
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err, 'İki adımlı doğrulama durumu yüklenemedi.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [token])

  async function handleStartSetup() {
    if (!token) return
    setError(null)
    setSuccess(null)
    setStartingSetup(true)
    try {
      const setup = await twoFactorApi.setup(token)
      setSetupSecret(setup.secret)
      const dataUrl = await QRCode.toDataURL(setup.otpAuthUri)
      setQrCodeDataUrl(dataUrl)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kurulum başlatılamadı.'))
    } finally {
      setStartingSetup(false)
    }
  }

  async function handleEnable(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setSubmitting(true)
    try {
      await twoFactorApi.enable(token, { code })
      setEnabled(true)
      setSetupSecret(null)
      setQrCodeDataUrl(null)
      setCode('')
      setSuccess('İki adımlı doğrulama etkinleştirildi.')
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kod doğrulanamadı.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDisable(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setSubmitting(true)
    try {
      await twoFactorApi.disable(token, { code })
      setEnabled(false)
      setCode('')
      setSuccess('İki adımlı doğrulama devre dışı bırakıldı.')
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Kod doğrulanamadı.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Layout>
      <div className="mx-auto max-w-xl space-y-6">
        <h1 className="text-xl font-bold text-ink-900">Ayarlar</h1>

        <Card className="p-6">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-ink-900">İki Adımlı Doğrulama</h2>
            {!loading && <Badge tone={enabled ? 'brand' : 'ink'}>{enabled ? 'Aktif' : 'Kapalı'}</Badge>}
          </div>

          {loading && <p className="mt-4 text-sm text-ink-500">Yükleniyor...</p>}

          {!loading && (
            <div className="mt-4 space-y-4">
              {error && <ErrorBanner message={error} />}
              {success && (
                <div className="rounded-xl border border-brand-500/20 bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700">
                  {success}
                </div>
              )}

              {!enabled && !setupSecret && (
                <div>
                  <p className="text-sm text-ink-600">
                    Girişte şifrenize ek olarak telefonunuzdaki bir doğrulama uygulamasından
                    (ör. Google Authenticator) alınan 6 haneli kod istenir.
                  </p>
                  <Button className="mt-4" onClick={handleStartSetup} loading={startingSetup}>
                    Etkinleştir
                  </Button>
                </div>
              )}

              {!enabled && setupSecret && (
                <div className="space-y-4">
                  <p className="text-sm text-ink-600">
                    Doğrulama uygulamanızla aşağıdaki kodu okutun, ardından uygulamanın gösterdiği
                    6 haneli kodu girin.
                  </p>

                  {qrCodeDataUrl && (
                    <img
                      src={qrCodeDataUrl}
                      alt="İki adımlı doğrulama QR kodu"
                      className="h-48 w-48 rounded-xl border border-ink-200"
                    />
                  )}

                  <div>
                    <Label>QR kodu okutamıyor musunuz? Bu kodu elle girin:</Label>
                    <p className="rounded-lg bg-ink-50 px-3 py-2 font-mono text-sm text-ink-700">{setupSecret}</p>
                  </div>

                  <form onSubmit={handleEnable} className="space-y-3">
                    <div>
                      <Label htmlFor="enable-code">Doğrulama Kodu</Label>
                      <Input
                        id="enable-code"
                        inputMode="numeric"
                        maxLength={6}
                        placeholder="123456"
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        required
                      />
                    </div>
                    <Button type="submit" loading={submitting}>
                      Onayla ve Etkinleştir
                    </Button>
                  </form>
                </div>
              )}

              {enabled && (
                <form onSubmit={handleDisable} className="space-y-3">
                  <p className="text-sm text-ink-600">
                    Devre dışı bırakmak için doğrulama uygulamanızdaki güncel 6 haneli kodu girin.
                  </p>
                  <div>
                    <Label htmlFor="disable-code">Doğrulama Kodu</Label>
                    <Input
                      id="disable-code"
                      inputMode="numeric"
                      maxLength={6}
                      placeholder="123456"
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                      required
                    />
                  </div>
                  <Button type="submit" variant="secondary" loading={submitting}>
                    Devre Dışı Bırak
                  </Button>
                </form>
              )}
            </div>
          )}
        </Card>
      </div>
    </Layout>
  )
}
