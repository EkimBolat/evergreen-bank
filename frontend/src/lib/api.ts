import type {
  AccountResponse,
  AuthResponse,
  CardIssuedResponse,
  CardResponse,
  CardType,
  CreditCardStatementResponse,
  CreditCardTransactionResponse,
  LoginRequest,
  NotificationResponse,
  Page,
  RegisterRequest,
  TransactionResponse,
  TransferResponse,
  TwoFactorVerifyRequest,
} from './types'

const API_BASE = '/api/v1'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  token?: string | null
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.token) headers['Authorization'] = `Bearer ${options.token}`

  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : undefined

  if (!response.ok) {
    const message = (data && (data.message || data.error)) || `İstek başarısız oldu (${response.status})`
    throw new ApiError(response.status, message)
  }

  return data as T
}

export const authApi = {
  login: (payload: LoginRequest) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: payload }),
  register: (payload: RegisterRequest) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: payload }),
  verifyTwoFactor: (payload: TwoFactorVerifyRequest) =>
    request<AuthResponse>('/2fa/verify', { method: 'POST', body: payload }),
}

export const accountApi = {
  getMyAccount: (token: string) => request<AccountResponse>('/accounts/me', { token }),
  getByAccountNumber: (token: string, accountNumber: string) =>
    request<AccountResponse>(`/accounts/number/${encodeURIComponent(accountNumber)}`, { token }),
}

export const transactionApi = {
  getHistory: (token: string, accountId: number, page = 0, size = 10) =>
    request<Page<TransactionResponse>>(
      `/transactions/account/${accountId}?page=${page}&size=${size}&sort=timestamp,desc`,
      { token },
    ),
  deposit: (token: string, accountId: number, amount: number) =>
    request<TransactionResponse>(`/transactions/deposit/${accountId}`, {
      method: 'POST',
      body: { amount },
      token,
    }),
  withdraw: (token: string, accountId: number, amount: number) =>
    request<TransactionResponse>(`/transactions/withdraw/${accountId}`, {
      method: 'POST',
      body: { amount },
      token,
    }),
  exportCsv: async (token: string, accountId: number): Promise<Blob> => {
    const response = await fetch(`${API_BASE}/transactions/account/${accountId}/export`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!response.ok) {
      throw new ApiError(response.status, `Ekstre indirilemedi (${response.status})`)
    }
    return response.blob()
  },
}

export const transferApi = {
  send: (token: string, payload: { fromAccountId: number; toAccountId: number; amount: number }) =>
    request<TransferResponse>('/transfers', { method: 'POST', body: payload, token }),
}

export const cardApi = {
  list: (token: string, accountId: number) =>
    request<CardResponse[]>(`/accounts/${accountId}/cards`, { token }),
  issue: (token: string, accountId: number, cardType: CardType, creditLimit?: number) =>
    request<CardIssuedResponse>(`/accounts/${accountId}/cards`, {
      method: 'POST',
      body: { cardType, creditLimit },
      token,
    }),
  block: (token: string, cardId: number) =>
    request<CardResponse>(`/cards/${cardId}/block`, { method: 'PATCH', token }),
  activate: (token: string, cardId: number) =>
    request<CardResponse>(`/cards/${cardId}/activate`, { method: 'PATCH', token }),
  cancel: (token: string, cardId: number) =>
    request<CardResponse>(`/cards/${cardId}`, { method: 'DELETE', token }),
}

export const creditCardApi = {
  charge: (token: string, cardId: number, amount: number, description?: string) =>
    request<CreditCardTransactionResponse>(`/cards/${cardId}/charge`, {
      method: 'POST',
      body: { amount, description },
      token,
    }),
  pay: (token: string, cardId: number, amount: number) =>
    request<CreditCardTransactionResponse>(`/cards/${cardId}/pay`, {
      method: 'POST',
      body: { amount },
      token,
    }),
  getStatements: (token: string, cardId: number) =>
    request<CreditCardStatementResponse[]>(`/cards/${cardId}/statements`, { token }),
  getTransactions: (token: string, cardId: number) =>
    request<CreditCardTransactionResponse[]>(`/cards/${cardId}/transactions`, { token }),
}

export const notificationApi = {
  list: (token: string, page = 0, size = 10) =>
    request<Page<NotificationResponse>>(`/notifications?page=${page}&size=${size}`, { token }),
  unreadCount: (token: string) => request<number>('/notifications/unread-count', { token }),
  markAsRead: (token: string, id: number) =>
    request<NotificationResponse>(`/notifications/${id}/read`, { method: 'PATCH', token }),
  markAllAsRead: (token: string) =>
    request<void>('/notifications/read-all', { method: 'PATCH', token }),
}
