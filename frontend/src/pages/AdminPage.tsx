import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react'
import { Layout } from '../components/Layout'
import { Button, Card, ErrorBanner, Input, Label } from '../components/ui'
import { adminAccountApi, auditLogApi, branchApi, customerApi } from '../lib/api'
import { friendlyErrorMessage } from '../lib/errors'
import { useAuth } from '../lib/use-auth'
import { formatDate } from '../lib/format'
import type {
  AccountType,
  AuditLogResponse,
  BranchResponse,
  CustomerResponse,
} from '../lib/types'

type Tab = 'customers' | 'branches' | 'audit'

export function AdminPage() {
  const [tab, setTab] = useState<Tab>('customers')

  return (
    <Layout>
      <h1 className="mb-6 text-lg font-bold text-ink-900">Admin Paneli</h1>

      <div className="mb-6 flex gap-1 rounded-xl bg-ink-100 p-1 sm:w-fit">
        <TabButton label="Müşteriler" active={tab === 'customers'} onClick={() => setTab('customers')} />
        <TabButton label="Şubeler" active={tab === 'branches'} onClick={() => setTab('branches')} />
        <TabButton label="Denetim Kayıtları" active={tab === 'audit'} onClick={() => setTab('audit')} />
      </div>

      {tab === 'customers' && <CustomersTab />}
      {tab === 'branches' && <BranchesTab />}
      {tab === 'audit' && <AuditLogTab />}
    </Layout>
  )
}

function TabButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg px-4 py-1.5 text-sm font-semibold transition ${
        active ? 'bg-white text-brand-700 shadow-sm' : 'text-ink-500 hover:text-ink-700'
      }`}
    >
      {label}
    </button>
  )
}

function CustomersTab() {
  const { token } = useAuth()
  const [customers, setCustomers] = useState<CustomerResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const [formOpen, setFormOpen] = useState(false)
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [nationalId, setNationalId] = useState('')
  const [age, setAge] = useState('')
  const [address, setAddress] = useState('')
  const [creating, setCreating] = useState(false)

  const [accountFormCustomerId, setAccountFormCustomerId] = useState<number | null>(null)
  const isInitialLoadRef = useRef(true)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const showSpinner = isInitialLoadRef.current

    async function load() {
      if (showSpinner) setLoading(true)
      setError(null)
      try {
        const result = await customerApi.list(token!, page, 10)
        if (cancelled) return
        setCustomers(result.content)
        setTotalPages(result.totalPages)
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err, 'Müşteriler yüklenemedi.'))
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
  }, [token, page, refreshKey])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setCreating(true)
    setError(null)
    try {
      await customerApi.create(token, {
        firstName,
        lastName,
        email,
        phoneNumber,
        nationalId,
        age: Number(age),
        address: address || undefined,
      })
      setFirstName('')
      setLastName('')
      setEmail('')
      setPhoneNumber('')
      setNationalId('')
      setAge('')
      setAddress('')
      setFormOpen(false)
      setPage(0)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Müşteri oluşturulamadı.'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-ink-900">Müşteriler</h2>
        <Button onClick={() => setFormOpen((v) => !v)}>{formOpen ? 'Vazgeç' : 'Yeni Müşteri'}</Button>
      </div>

      {error && <ErrorBanner message={error} />}

      {formOpen && (
        <Card className="p-5">
          <form onSubmit={handleCreate} className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="firstName">Ad</Label>
              <Input id="firstName" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="lastName">Soyad</Label>
              <Input id="lastName" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="email">E-posta</Label>
              <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="phoneNumber">Telefon</Label>
              <Input id="phoneNumber" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="nationalId">T.C. Kimlik No</Label>
              <Input
                id="nationalId"
                maxLength={11}
                value={nationalId}
                onChange={(e) => setNationalId(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="age">Yaş</Label>
              <Input id="age" type="number" min="18" value={age} onChange={(e) => setAge(e.target.value)} required />
            </div>
            <div className="sm:col-span-2">
              <Label htmlFor="address">Adres</Label>
              <Input id="address" value={address} onChange={(e) => setAddress(e.target.value)} />
            </div>
            <div className="sm:col-span-2">
              <Button type="submit" loading={creating}>
                Müşteri Oluştur
              </Button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        {loading ? (
          <p className="p-5 text-sm text-ink-500">Yükleniyor...</p>
        ) : customers.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">Henüz müşteri yok.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs uppercase tracking-wide text-ink-400">
                  <th className="px-5 py-3 font-medium">Ad Soyad</th>
                  <th className="px-5 py-3 font-medium">T.C. Kimlik No</th>
                  <th className="px-5 py-3 font-medium">E-posta</th>
                  <th className="px-5 py-3 font-medium">Doğa Puanı</th>
                  <th className="px-5 py-3 font-medium"></th>
                </tr>
              </thead>
              <tbody>
                {customers.map((c) => (
                  <Fragment key={c.id}>
                    <tr className="border-b border-ink-50 last:border-0">
                      <td className="px-5 py-3 font-medium text-ink-900">
                        {c.firstName} {c.lastName}
                      </td>
                      <td className="px-5 py-3 text-ink-600">{c.nationalId}</td>
                      <td className="px-5 py-3 text-ink-600">{c.email}</td>
                      <td className="px-5 py-3 text-ink-600">{c.naturePoints}</td>
                      <td className="px-5 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => setAccountFormCustomerId((prev) => (prev === c.id ? null : c.id))}
                          className="text-xs font-semibold text-brand-600 hover:text-brand-700"
                        >
                          Hesap Aç
                        </button>
                      </td>
                    </tr>
                    {accountFormCustomerId === c.id && (
                      <tr className="border-b border-ink-50 bg-ink-50">
                        <td colSpan={5} className="px-5 py-3">
                          <OpenAccountForm customerId={c.id} onDone={() => setRefreshKey((k) => k + 1)} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  )
}

function OpenAccountForm({ customerId, onDone }: { customerId: number; onDone: () => void }) {
  const { token } = useAuth()
  const [accountType, setAccountType] = useState<AccountType>('CHECKING')
  const [interestRate, setInterestRate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setLoading(true)
    setError(null)
    setSuccess(null)
    try {
      const account = await adminAccountApi.create(token, customerId, {
        accountType,
        interestRate: accountType === 'SAVINGS' ? Number(interestRate) : undefined,
      })
      setSuccess(`Hesap açıldı: ${account.accountNumber}`)
      onDone()
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Hesap açılamadı.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
      {error && <p className="w-full text-xs font-medium text-danger-600">{error}</p>}
      {success && <p className="w-full text-xs font-medium text-brand-700">{success}</p>}
      <div>
        <Label htmlFor={`type-${customerId}`}>Hesap Tipi</Label>
        <select
          id={`type-${customerId}`}
          value={accountType}
          onChange={(e) => setAccountType(e.target.value as AccountType)}
          className="rounded-xl border border-ink-200 bg-white px-3 py-2 text-sm"
        >
          <option value="CHECKING">Vadesiz</option>
          <option value="SAVINGS">Vadeli</option>
        </select>
      </div>
      {accountType === 'SAVINGS' && (
        <div>
          <Label htmlFor={`rate-${customerId}`}>Faiz Oranı (%)</Label>
          <Input
            id={`rate-${customerId}`}
            type="number"
            min="0.01"
            step="0.01"
            value={interestRate}
            onChange={(e) => setInterestRate(e.target.value)}
            required
          />
        </div>
      )}
      <Button type="submit" loading={loading}>
        Hesabı Aç
      </Button>
    </form>
  )
}

function BranchesTab() {
  const { token } = useAuth()
  const [branches, setBranches] = useState<BranchResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const [formOpen, setFormOpen] = useState(false)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [city, setCity] = useState('')
  const [address, setAddress] = useState('')
  const [creating, setCreating] = useState(false)
  const isInitialLoadRef = useRef(true)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const showSpinner = isInitialLoadRef.current

    async function load() {
      if (showSpinner) setLoading(true)
      setError(null)
      try {
        const result = await branchApi.list(token!)
        if (!cancelled) setBranches(result.content)
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err, 'Şubeler yüklenemedi.'))
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

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setCreating(true)
    setError(null)
    try {
      await branchApi.create(token, { name, code, city, address: address || undefined })
      setName('')
      setCode('')
      setCity('')
      setAddress('')
      setFormOpen(false)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(friendlyErrorMessage(err, 'Şube oluşturulamadı.'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-ink-900">Şubeler</h2>
        <Button onClick={() => setFormOpen((v) => !v)}>{formOpen ? 'Vazgeç' : 'Yeni Şube'}</Button>
      </div>

      {error && <ErrorBanner message={error} />}

      {formOpen && (
        <Card className="p-5">
          <form onSubmit={handleCreate} className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="branchName">Şube Adı</Label>
              <Input id="branchName" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="branchCode">Şube Kodu</Label>
              <Input id="branchCode" value={code} onChange={(e) => setCode(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="branchCity">Şehir</Label>
              <Input id="branchCity" value={city} onChange={(e) => setCity(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="branchAddress">Adres</Label>
              <Input id="branchAddress" value={address} onChange={(e) => setAddress(e.target.value)} />
            </div>
            <div className="sm:col-span-2">
              <Button type="submit" loading={creating}>
                Şube Oluştur
              </Button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        {loading ? (
          <p className="p-5 text-sm text-ink-500">Yükleniyor...</p>
        ) : branches.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">Henüz şube yok.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs uppercase tracking-wide text-ink-400">
                  <th className="px-5 py-3 font-medium">Şube</th>
                  <th className="px-5 py-3 font-medium">Kod</th>
                  <th className="px-5 py-3 font-medium">Şehir</th>
                  <th className="px-5 py-3 font-medium">Adres</th>
                </tr>
              </thead>
              <tbody>
                {branches.map((b) => (
                  <tr key={b.id} className="border-b border-ink-50 last:border-0">
                    <td className="px-5 py-3 font-medium text-ink-900">{b.name}</td>
                    <td className="px-5 py-3 text-ink-600">{b.code}</td>
                    <td className="px-5 py-3 text-ink-600">{b.city}</td>
                    <td className="px-5 py-3 text-ink-600">{b.address ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function AuditLogTab() {
  const { token } = useAuth()
  const [logs, setLogs] = useState<AuditLogResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const isInitialLoadRef = useRef(true)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const showSpinner = isInitialLoadRef.current

    async function load() {
      if (showSpinner) setLoading(true)
      setError(null)
      try {
        const result = await auditLogApi.list(token!, page)
        if (cancelled) return
        setLogs(result.content)
        setTotalPages(result.totalPages)
      } catch (err) {
        if (!cancelled) setError(friendlyErrorMessage(err, 'Denetim kayıtları yüklenemedi.'))
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
  }, [token, page])

  return (
    <div className="space-y-4">
      <h2 className="text-sm font-semibold text-ink-900">Denetim Kayıtları</h2>

      {error && <ErrorBanner message={error} />}

      <Card>
        {loading ? (
          <p className="p-5 text-sm text-ink-500">Yükleniyor...</p>
        ) : logs.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">Henüz kayıt yok.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs uppercase tracking-wide text-ink-400">
                  <th className="px-5 py-3 font-medium">Zaman</th>
                  <th className="px-5 py-3 font-medium">Varlık</th>
                  <th className="px-5 py-3 font-medium">İşlem</th>
                  <th className="px-5 py-3 font-medium">Yapan</th>
                  <th className="px-5 py-3 font-medium">Detay</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id} className="border-b border-ink-50 last:border-0">
                    <td className="whitespace-nowrap px-5 py-3 text-ink-500">{formatDate(log.timestamp)}</td>
                    <td className="px-5 py-3 text-ink-600">
                      {log.entityType}
                      {log.entityId != null ? ` #${log.entityId}` : ''}
                    </td>
                    <td className="px-5 py-3 font-medium text-ink-900">{log.action}</td>
                    <td className="px-5 py-3 text-ink-600">{log.performedBy}</td>
                    <td className="px-5 py-3 text-ink-500">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  )
}

function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null

  return (
    <div className="flex items-center justify-center gap-3 text-sm">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className="rounded-lg border border-ink-200 px-3 py-1.5 font-medium text-ink-600 transition hover:bg-ink-50 disabled:opacity-40"
      >
        Önceki
      </button>
      <span className="text-ink-500">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="rounded-lg border border-ink-200 px-3 py-1.5 font-medium text-ink-600 transition hover:bg-ink-50 disabled:opacity-40"
      >
        Sonraki
      </button>
    </div>
  )
}
