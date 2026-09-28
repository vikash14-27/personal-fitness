import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth()
  const location = useLocation()
  if (!ready) return <p className="status">Loading your account…</p>
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

export function GuestOnly({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth()
  if (!ready) return <p className="status">Loading…</p>
  if (token) return <Navigate to="/dashboard" replace />
  return children
}
