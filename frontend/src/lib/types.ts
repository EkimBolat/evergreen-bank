export type Role = 'CUSTOMER' | 'ADMIN'

export interface AuthResponse {
  userId: number | null
  customerId: number | null
  email: string | null
  message: string
  token: string | null
  role: Role | null
  refreshToken: string | null
  twoFactorRequired: boolean
  pendingToken: string | null
}

export interface LoginRequest {
  nationalId: string
  password: string
}

export interface RegisterRequest {
  nationalId: string
  email: string
  password: string
}

export interface TwoFactorVerifyRequest {
  pendingToken: string
  code: string
}

export type AccountType = 'CHECKING' | 'SAVINGS'

export interface AccountResponse {
  id: number
  accountNumber: string
  balance: number
  accountType: AccountType
  interestRate: number | null
  dailyLimit: number
  monthlyLimit: number
  branchId: number | null
  branchName: string | null
  customerId: number
  customerFullName: string
  createdAt: string
}

export interface TransferResponse {
  id: number
  fromAccountNumber: string
  toAccountNumber: string
  amount: number
  timestamp: string
}

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'INTEREST'

export interface TransactionResponse {
  id: number
  accountNumber: string
  type: TransactionType
  amount: number
  balanceAfter: number
  transferId: number | null
  timestamp: string
}

export type CardStatus = 'ACTIVE' | 'BLOCKED'

export interface CardResponse {
  id: number
  accountId: number
  maskedCardNumber: string
  cardHolderName: string
  expiryDate: string
  status: CardStatus
  createdAt: string
}

export interface CardIssuedResponse {
  id: number
  accountId: number
  cardNumber: string
  cvv: string
  cardHolderName: string
  expiryDate: string
  status: CardStatus
}

export type NotificationType =
  | 'TRANSFER_SENT'
  | 'TRANSFER_RECEIVED'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'LIMIT_EXCEEDED'
  | 'ACCOUNT_LOCKED'
  | 'INTEREST_CREDITED'
  | 'SCHEDULED_TRANSFER_EXECUTED'
  | 'SCHEDULED_TRANSFER_FAILED'
  | 'CARD_ISSUED'
  | 'CARD_BLOCKED'
  | 'TWO_FACTOR_ENABLED'
  | 'TWO_FACTOR_DISABLED'

export interface NotificationResponse {
  id: number
  type: NotificationType
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
