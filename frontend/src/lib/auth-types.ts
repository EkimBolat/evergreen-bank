import { createContext } from 'react'
import type { Role } from './types'

export interface AuthState {
  token: string | null
  refreshToken: string | null
  email: string | null
  role: Role | null
  customerId: number | null
}

export interface AuthContextValue extends AuthState {
  isAuthenticated: boolean
  login: (state: AuthState) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
