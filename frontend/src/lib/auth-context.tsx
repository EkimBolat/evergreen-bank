import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthState } from './auth-types'
import { clearStoredAuth, readStoredAuth, writeStoredAuth } from './auth-storage'
import { setOnTokenRefreshed } from './api'

function emptyState(): AuthState {
  return { token: null, refreshToken: null, email: null, role: null, customerId: null }
}

function loadStoredAuth(): AuthState {
  return { ...emptyState(), ...readStoredAuth() }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(loadStoredAuth)

  useEffect(() => {
    if (state.token) {
      writeStoredAuth(state)
    } else {
      clearStoredAuth()
    }
  }, [state])

  useEffect(() => {
    setOnTokenRefreshed((token, refreshToken) => {
      setState((prev) => (prev.token ? { ...prev, token, refreshToken } : prev))
    })
    return () => setOnTokenRefreshed(null)
  }, [])

  const value: AuthContextValue = {
    ...state,
    isAuthenticated: Boolean(state.token),
    login: (next) => setState(next),
    logout: () => setState(emptyState()),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
