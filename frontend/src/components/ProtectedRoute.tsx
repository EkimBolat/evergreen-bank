import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/use-auth'
import type { Role } from '../lib/types'

export function ProtectedRoute({
  children,
  requireRole,
}: {
  children: ReactNode
  requireRole?: Role
}) {
  const { isAuthenticated, role } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (requireRole && role !== requireRole) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}
