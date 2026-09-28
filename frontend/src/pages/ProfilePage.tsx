import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiRequestError } from '../api'
import { useAuth } from '../auth'
import { BioFields } from '../components/BioFields'
import { GoalSummary } from '../components/GoalSummary'
import { sexFloor } from '../options'
import type { Profile } from '../types'
import { validateBio, validateOverride, type BioInput } from '../validation'

function fromProfile(profile: Profile): BioInput {
  return {
    age: String(profile.age),
    sex: profile.sex,
    heightCm: String(profile.heightCm),
    currentWeightKg: String(profile.currentWeightKg),
    targetWeightKg: String(profile.targetWeightKg),
    activityLevel: profile.activityLevel,
    timelineWeeks: String(profile.timelineWeeks),
  }
}

export function ProfilePage() {
  const { token, profile, setProfile } = useAuth()
  const [bio, setBio] = useState<BioInput | null>(profile ? fromProfile(profile) : null)
  const [useOverride, setUseOverride] = useState(profile?.calorieGoalOverride != null)
  const [override, setOverride] = useState(profile?.calorieGoalOverride != null ? String(profile.calorieGoalOverride) : '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saved, setSaved] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    document.title = 'Profile · Personal Fitness'
  }, [])

  useEffect(() => {
    if (profile && !bio) {
      setBio(fromProfile(profile))
      setUseOverride(profile.calorieGoalOverride != null)
      setOverride(profile.calorieGoalOverride != null ? String(profile.calorieGoalOverride) : '')
    }
  }, [profile, bio])

  if (!profile || !bio) return <p className="status">Loading your profile…</p>

  const floor = sexFloor(bio.sex)
  const overrideNumber = Number(override)
  const belowFloor = useOverride && floor != null && /^\d+$/.test(override.trim()) && overrideNumber < floor

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = validateBio(bio!)
    const overrideError = validateOverride(useOverride, override)
    if (overrideError) nextErrors.calorieGoalOverride = overrideError
    setErrors(nextErrors)
    setFormError('')
    setSaved('')
    if (Object.keys(nextErrors).length > 0 || !token) return

    setSubmitting(true)
    try {
      const updated = await api<Profile>('/api/profile', {
        method: 'PUT',
        body: JSON.stringify({
          age: Number(bio!.age),
          sex: bio!.sex,
          heightCm: Number(bio!.heightCm),
          currentWeightKg: Number(bio!.currentWeightKg),
          targetWeightKg: Number(bio!.targetWeightKg),
          activityLevel: bio!.activityLevel,
          timelineWeeks: Number(bio!.timelineWeeks),
          calorieGoalOverride: useOverride ? Number(override) : null,
        }),
      }, token)
      setProfile(updated)
      setSaved(updated.calorieGoalSource === 'OVERRIDE'
        ? 'Saved. The dashboard will use your manual goal.'
        : 'Saved. Your calorie goal was recomputed.')
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
    <div className="page-grid">
      <div className="stack">
        <GoalSummary profile={profile} />
        <p className="fine-print">
          <Link to="/dashboard">Back to dashboard</Link>
        </p>
      </div>
      <form className="card form" noValidate onSubmit={onSubmit} data-testid="profile-form">
        <p className="eyebrow">Profile</p>
        <h1>Edit your plan</h1>
        <p className="lede">{profile.email}</p>
        {formError ? <p className="banner" role="alert" data-testid="form-error">{formError}</p> : null}
        {saved ? <p className="banner ok" role="status" data-testid="save-status">{saved}</p> : null}
        <BioFields values={bio} errors={errors} onChange={(patch) => setBio((current) => ({ ...current!, ...patch }))} />

        <fieldset className={errors.calorieGoalOverride ? 'choice-set invalid' : 'choice-set'}>
          <legend>Calorie goal</legend>
          <label className="check">
            <input
              type="checkbox"
              checked={useOverride}
              onChange={(event) => setUseOverride(event.target.checked)}
              data-testid="override-toggle"
            />
            <span>Use a manual calorie goal instead of the formula</span>
          </label>
          {useOverride ? (
            <label className="field">
              <span>Manual goal (kcal)</span>
              <input
                inputMode="numeric"
                value={override}
                onChange={(event) => setOverride(event.target.value)}
                data-testid="override-input"
              />
            </label>
          ) : (
            <small className="hint">Leave this off to keep the computed goal. Saving still recalculates it from the fields above.</small>
          )}
          {errors.calorieGoalOverride ? <small role="alert">{errors.calorieGoalOverride}</small> : null}
          {belowFloor ? (
            <small className="hint warn">
              This is below the {floor?.toLocaleString('en-US')} kcal minimum we use for calculated goals. You can still save it.
            </small>
          ) : null}
        </fieldset>

        <button type="submit" className="primary" disabled={submitting} data-testid="save-profile">
          {submitting ? 'Saving…' : 'Save profile'}
        </button>
      </form>
    </div>
  )
}
