import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

export function Layout() {
  const { token, logout } = useAuth()
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <a className="skip" href="#content">Skip to content</a>
      <header className="topbar">
        <NavLink to={token ? '/dashboard' : '/login'} className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span>
            <strong>Personal Fitness</strong>
            <small>Meals and a daily calorie goal</small>
          </span>
        </NavLink>
        {token ? (
          <nav>
            <NavLink to="/dashboard">Dashboard</NavLink>
            <NavLink to="/profile">Profile</NavLink>
            <button type="button" className="ghost" data-testid="logout-button" onClick={() => void onLogout()}>
              Log out
            </button>
          </nav>
        ) : (
          <nav>
            <NavLink to="/login">Log in</NavLink>
            <NavLink to="/signup" className="nav-cta">Sign up</NavLink>
          </nav>
        )}
      </header>
      <main id="content">
        <Outlet />
      </main>
    </div>
  )
}
