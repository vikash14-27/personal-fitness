import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../api'
import { useAuth } from '../auth'
import { BioFields } from '../components/BioFields'
import { Field } from '../components/Field'
import type { ActivityLevel, Sex } from '../types'
import { validateAccount, validateBio, type BioInput } from '../validation'

const emptyBio: BioInput = {
  age: '',
  sex: '',
  heightCm: '',
  currentWeightKg: '',
  targetWeightKg: '',
  activityLevel: '',
  timelineWeeks: '',
}

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [bio, setBio] = useState<BioInput>(emptyBio)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    document.title = 'Sign up · Personal Fitness'
  }, [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      ...validateAccount(email, password),
      ...validateBio(bio),
    }
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    try {
      await signup({
        email: email.trim(),
        password,
        age: Number(bio.age),
        sex: bio.sex as Sex,
        heightCm: Number(bio.heightCm),
        currentWeightKg: Number(bio.currentWeightKg),
        targetWeightKg: Number(bio.targetWeightKg),
        activityLevel: bio.activityLevel as ActivityLevel,
        timelineWeeks: Number(bio.timelineWeeks),
      })
      navigate('/dashboard', { replace: true })
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setFormError(error.message)
        if (error.code === 'email_taken') {
          setErrors({ email: error.message })
        } else {
          setErrors(error.fields)
        }
      } else {
        setFormError('Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page-grid">
      <section className="intro">
        <p className="eyebrow">Start here</p>
        <h1>Set the numbers your plan is built on.</h1>
        <p>
          Tell us how you train and where you want your weight to land. We’ll store a daily calorie goal
          from the Mifflin–St Jeor equation, your activity level, and the timeline you choose.
        </p>
      </section>
      <form className="card form" noValidate onSubmit={onSubmit} data-testid="signup-form">
        <h2>Create your account</h2>
        {formError ? <p className="banner" role="alert" data-testid="form-error">{formError}</p> : null}
        <div className="split">
          <Field label="Email" error={errors.email}>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              data-testid="email"
            />
          </Field>
          <Field label="Password" error={errors.password} hint="At least 8 characters">
            <input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              data-testid="password"
            />
          </Field>
        </div>
        <BioFields values={bio} errors={errors} onChange={(patch) => setBio((current) => ({ ...current, ...patch }))} />
        <button type="submit" className="primary" disabled={submitting} data-testid="signup-submit">
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
        <p className="switch">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  )
}
