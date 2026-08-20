import { useEffect, useState } from 'react'
import { Layout } from '../components/Layout'
import { Card } from '../components/ui'
import { customerApi, natureApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatDate } from '../lib/format'
import type { CustomerResponse, TreeCertificateResponse, TreeSpecies } from '../lib/types'

const POINTS_PER_TREE = 100
const DAILY_POINTS_CAP = 50

export function NaturePage() {
  const { token } = useAuth()
  const [profile, setProfile] = useState<CustomerResponse | null>(null)
  const [certificates, setCertificates] = useState<TreeCertificateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [me, certs] = await Promise.all([customerApi.getMe(token!), natureApi.getMyCertificates(token!)])
        if (cancelled) return
        setProfile(me)
        setCertificates(certs)
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err, 'Doğa puanı bilgileri yüklenemedi.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [token])

  const progressPercent = profile ? Math.round((profile.naturePoints / POINTS_PER_TREE) * 100) : 0

  return (
    <Layout>
      {loading && <p className="text-sm text-ink-500">Yükleniyor...</p>}

      {!loading && error && (
        <Card className="p-8 text-center">
          <p className="text-sm text-danger-600">{error}</p>
        </Card>
      )}

      {!loading && profile && (
        <div className="space-y-6">
          <div>
            <h1 className="text-xl font-bold text-ink-900">Doğa Puanım</h1>
            <p className="mt-1 text-sm text-ink-500">
              50₺ ve üzeri her işlemde doğa puanı kazanırsınız; 100 puanda sizin adınıza bir fidan dikilir.
              Günlük en fazla {DAILY_POINTS_CAP} puan kazanabilirsiniz.
            </p>
          </div>

          <div className="rounded-2xl bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white shadow-sm sm:p-8">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-brand-100">Bir sonraki fidana</p>
                <p className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
                  {profile.naturePoints} / {POINTS_PER_TREE} puan
                </p>
              </div>
              <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold">
                {profile.treesPlanted} fidan dikildi
              </span>
            </div>
            <div className="mt-6 h-2 w-full overflow-hidden rounded-full bg-white/20">
              <div
                className="h-full rounded-full bg-white transition-all"
                style={{ width: `${Math.min(progressPercent, 100)}%` }}
              />
            </div>
            <p className="mt-3 text-xs text-brand-100">
              Bugün kazanılan: {profile.dailyNaturePoints} / {DAILY_POINTS_CAP} puan
            </p>
          </div>

          <Card>
            <div className="border-b border-ink-100 px-5 py-4">
              <h2 className="text-sm font-semibold text-ink-900">Fidan Sertifikalarım</h2>
            </div>

            {certificates.length === 0 ? (
              <p className="px-5 py-8 text-center text-sm text-ink-400">
                Henüz adınıza dikilmiş bir fidan yok. Puan biriktirmeye devam edin.
              </p>
            ) : (
              <div className="grid gap-4 p-5 sm:grid-cols-2">
                {certificates.map((cert) => (
                  <div key={cert.id} className="rounded-xl border border-ink-100 bg-ink-50 p-4">
                    <div className="flex items-start justify-between">
                      <p className="text-sm font-semibold text-ink-900">{speciesLabel(cert.species)}</p>
                      <span className="font-mono text-xs text-ink-400">{cert.certificateNumber}</span>
                    </div>
                    <p className="mt-1 text-sm text-ink-600">{cert.plantingRegion}</p>
                    <p className="mt-2 text-xs text-ink-400">{formatDate(cert.plantedAt)}</p>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      )}
    </Layout>
  )
}

function speciesLabel(species: TreeSpecies): string {
  switch (species) {
    case 'OAK':
      return 'Meşe'
    case 'PINE':
      return 'Çam'
    case 'LINDEN':
      return 'Ihlamur'
    case 'CHESTNUT':
      return 'Kestane'
    case 'BIRCH':
      return 'Huş'
    default:
      return species
  }
}
