import type { AuthState } from './auth-types'

export const AUTH_STORAGE_KEY = 'evergreen-bank-auth'

export function readStoredAuth(): AuthState | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw) as AuthState
  } catch {
    return null
  }
}

export function writeStoredAuth(state: AuthState): void {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(state))
}

export function clearStoredAuth(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}
