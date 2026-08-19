import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthState } from './auth-types'

const STORAGE_KEY = 'evergreen-bank-auth'

function emptyState(): AuthState {
  return { token: null, refreshToken: null, email: null, role: null, customerId: null }
}

function loadStoredAuth(): AuthState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return emptyState()
    return { ...emptyState(), ...(JSON.parse(raw) as Partial<AuthState>) }
  } catch {
    return emptyState()
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(loadStoredAuth)

  useEffect(() => {
    if (state.token) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [state])

  const value: AuthContextValue = {
    ...state,
    isAuthenticated: Boolean(state.token),
    login: (next) => setState(next),
    logout: () => setState(emptyState()),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
