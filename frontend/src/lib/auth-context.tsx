import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthState } from './auth-types'
import { clearStoredAuth, readStoredAuth, writeStoredAuth } from './auth-storage'
import { authApi, setOnTokenRefreshed } from './api'

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
    logout: () => {
      const token = state.token
      setState(emptyState())
      // Best-effort: revokes the refresh token server-side so it can't silently
      // mint new access tokens after logout. Local state is already cleared either way.
      if (token) {
        authApi.logout(token).catch(() => {})
      }
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
