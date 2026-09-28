import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../api'
import { useAuth } from '../auth'
import { Field } from '../components/Field'
import { validateAccount } from '../validation'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    document.title = 'Log in · Personal Fitness'
  }, [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = validateAccount(email, password)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setFormError(error.message)
        setErrors(error.fields)
      } else {
        setFormError('Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="narrow">
      <form className="card form" noValidate onSubmit={onSubmit} data-testid="login-form">
        <p className="eyebrow">Welcome back</p>
        <h1>Log in</h1>
        {formError ? <p className="banner" role="alert" data-testid="form-error">{formError}</p> : null}
        <Field label="Email" error={errors.email}>
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            data-testid="email"
          />
        </Field>
        <Field label="Password" error={errors.password}>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            data-testid="password"
          />
        </Field>
        <button type="submit" className="primary" disabled={submitting} data-testid="login-submit">
          {submitting ? 'Signing in…' : 'Log in'}
        </button>
        <p className="switch">
          New here? <Link to="/signup">Create an account</Link>
        </p>
      </form>
    </div>
  )
}
