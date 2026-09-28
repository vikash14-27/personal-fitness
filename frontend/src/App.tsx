import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import { Layout } from './components/Layout'
import { GuestOnly, ProtectedRoute } from './components/RouteGuards'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { SignupPage } from './pages/SignupPage'

function HomeRedirect() {
  const { token, ready } = useAuth()
  if (!ready) return <p className="status">Loading…</p>
  return <Navigate to={token ? '/dashboard' : '/login'} replace />
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="/signup" element={<GuestOnly><SignupPage /></GuestOnly>} />
        <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
