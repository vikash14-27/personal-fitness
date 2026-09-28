import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiRequestError } from '../api'
import { useAuth } from '../auth'
import { GoalSummary } from '../components/GoalSummary'
import { MealForm, mealToInput, type MealPayload } from '../components/MealForm'
import { formatDay, formatKg, formatMacro, formatNumber, isIsoDay, shiftDay, todayInputValue } from '../format'
import { activityLabel } from '../options'
import type { DayView } from '../types'

export function DashboardPage() {
  const { token, profile, refresh } = useAuth()
  const [params, setParams] = useSearchParams()
  const requested = params.get('day')
  const today = todayInputValue()
  const day = requested && isIsoDay(requested) ? requested : today
  const [dayView, setDayView] = useState<DayView | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<string | null>(null)
  const loadSeq = useRef(0)

  const loadDay = useCallback(async () => {
    if (!token || !isIsoDay(day)) return
    const seq = ++loadSeq.current
    setLoading(true)
    try {
      const next = await api<DayView>(`/api/days/${encodeURIComponent(day)}`, {}, token)
      if (seq !== loadSeq.current) return
      setDayView(next)
      setLoadError('')
    } catch (error) {
      if (seq !== loadSeq.current) return
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
        await refresh()
        return
      }
      setLoadError(error instanceof ApiRequestError ? error.message : 'Something went wrong.')
    } finally {
      if (seq === loadSeq.current) setLoading(false)
    }
  }, [token, day, refresh])

  useEffect(() => {
    document.title = `${formatDay(day)} · Personal Fitness`
  }, [day])

  useEffect(() => {
    if (requested && !isIsoDay(requested)) {
      setParams({}, { replace: true })
    }
  }, [requested, setParams])

  useEffect(() => {
    void refresh().catch(() => undefined)
  }, [refresh])

  useEffect(() => {
    setDayView(null)
    setEditingId(null)
    setPendingDelete(null)
    setNotice('')
    setActionError('')
    void loadDay()
  }, [loadDay])

  function setDay(next: string) {
    if (!isIsoDay(next)) return
    if (next === todayInputValue()) setParams({}, { replace: true })
    else setParams({ day: next }, { replace: true })
  }

  async function saveNew(payload: MealPayload) {
    if (!token) return
    await api('/api/meals', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, token)
    setNotice('Meal saved.')
    setActionError('')
    await loadDay()
  }

  async function saveEdit(id: string, payload: MealPayload) {
    if (!token) return
    await api(`/api/meals/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }, token)
    setEditingId(null)
    setNotice('Meal updated.')
    setActionError('')
    await loadDay()
  }

  async function remove(id: string) {
    if (!token) return
    try {
      await api(`/api/meals/${encodeURIComponent(id)}`, { method: 'DELETE' }, token)
      if (editingId === id) setEditingId(null)
      setPendingDelete(null)
      setNotice('Meal deleted.')
      setActionError('')
      await loadDay()
    } catch (error) {
      setActionError(error instanceof ApiRequestError ? error.message : 'Something went wrong.')
    }
  }

  if (!profile || !token) return <p className="status">Loading your day…</p>

  const remaining = dayView?.remainingCalories ?? 0

  return (
    <div className="day-layout">
      <div className="stack">
        <div className="dash-head">
          <div>
            <p className="eyebrow">Day</p>
            <h1>{formatDay(day)}</h1>
            <p className="lede">Signed in as {profile.email}</p>
          </div>
          <div className="day-nav">
            <button type="button" className="ghost" data-testid="prev-day" onClick={() => setDay(shiftDay(day, -1))}>
              Previous
            </button>
            <input
              type="date"
              value={day}
              aria-label="Day"
              data-testid="day-input"
              onChange={(event) => {
                if (isIsoDay(event.target.value)) setDay(event.target.value)
              }}
            />
            <button type="button" className="ghost" data-testid="next-day" onClick={() => setDay(shiftDay(day, 1))}>
              Next
            </button>
            <button type="button" className="ghost" data-testid="today-button" disabled={day === today} onClick={() => setDay(today)}>
              Today
            </button>
          </div>
        </div>

        {loadError ? (
          <div className="banner" role="alert" data-testid="day-error">
            <p>{loadError}</p>
            <button type="button" className="ghost" onClick={() => void loadDay()}>Try again</button>
          </div>
        ) : null}
        {actionError ? <p className="banner" role="alert" data-testid="meal-action-error">{actionError}</p> : null}
        {notice ? <p className="banner ok" role="status" data-testid="meal-status">{notice}</p> : null}

        {dayView ? (
          <section className="card day-totals" data-testid="day-totals">
            <h2>Daily totals</h2>
            <ul className="stats thirds">
              <li>
                <span>Eaten</span>
                <strong data-testid="calories-eaten">{formatNumber(dayView.totals.calories)} kcal</strong>
              </li>
              <li>
                <span>{dayView.calorieGoalSource === 'OVERRIDE' ? 'Goal · manual' : 'Goal · computed'}</span>
                <strong data-testid="day-calorie-goal">{formatNumber(dayView.calorieGoal)} kcal</strong>
              </li>
              <li>
                <span>{remaining < 0 ? 'Over goal' : 'Remaining'}</span>
                <strong data-testid="calories-remaining" className={remaining < 0 ? 'over' : undefined}>
                  {formatNumber(Math.abs(remaining))} kcal
                </strong>
              </li>
            </ul>
            <ul className="stats thirds">
              <li>
                <span>Protein</span>
                <strong data-testid="protein-total">{formatMacro(dayView.totals.proteinG)} g</strong>
              </li>
              <li>
                <span>Carbs</span>
                <strong data-testid="carbs-total">{formatMacro(dayView.totals.carbsG)} g</strong>
              </li>
              <li>
                <span>Fat</span>
                <strong data-testid="fat-total">{formatMacro(dayView.totals.fatG)} g</strong>
              </li>
            </ul>
          </section>
        ) : loading ? <p className="status">Loading meals…</p> : null}

        <section className="card">
          <h2>Meals</h2>
          {dayView && dayView.meals.length === 0 ? (
            <p data-testid="meals-empty">No meals logged for this day.</p>
          ) : null}
          {dayView && dayView.meals.length > 0 ? (
            <ul className="meal-list" data-testid="meal-list">
              {dayView.meals.map((meal) => (
                <li className="meal" key={meal.id} data-testid={`meal-${meal.id}`}>
                  {editingId === meal.id ? (
                    <MealForm
                      token={token}
                      day={meal.day}
                      initial={mealToInput(meal)}
                      submitLabel="Save changes"
                      allowDayEdit
                      onCancel={() => setEditingId(null)}
                      onSubmit={(payload) => saveEdit(meal.id, payload)}
                    />
                  ) : (
                    <>
                      <div className="meal-top">
                        <strong>{meal.description}</strong>
                        <span>{formatNumber(meal.calories)} kcal</span>
                      </div>
                      <p className="macros">
                        <span>Protein {formatMacro(meal.proteinG)} g</span>
                        <span>Carbs {formatMacro(meal.carbsG)} g</span>
                        <span>Fat {formatMacro(meal.fatG)} g</span>
                      </p>
                      {pendingDelete === meal.id ? (
                        <div className="row-actions">
                          <span>Delete this meal?</span>
                          <button type="button" className="danger" data-testid="confirm-delete" onClick={() => void remove(meal.id)}>
                            Delete
                          </button>
                          <button type="button" className="ghost" onClick={() => setPendingDelete(null)}>Cancel</button>
                        </div>
                      ) : (
                        <div className="row-actions">
                          <button type="button" className="ghost" data-testid={`edit-meal-${meal.id}`} onClick={() => { setEditingId(meal.id); setPendingDelete(null) }}>
                            Edit
                          </button>
                          <button type="button" className="danger" data-testid={`delete-meal-${meal.id}`} onClick={() => setPendingDelete(meal.id)}>
                            Delete
                          </button>
                        </div>
                      )}
                    </>
                  )}
                </li>
              ))}
            </ul>
          ) : null}
        </section>

        <section className="card">
          <h2>Log a meal</h2>
          <MealForm key={day} token={token} day={day} submitLabel="Save meal" onSubmit={saveNew} />
        </section>
      </div>

      <div className="stack">
        <GoalSummary profile={profile} />
        <ul className="stats side">
          <li>
            <span>Now</span>
            <strong data-testid="current-weight">{formatKg(profile.currentWeightKg)} kg</strong>
          </li>
          <li>
            <span>Target</span>
            <strong data-testid="target-weight">{formatKg(profile.targetWeightKg)} kg</strong>
          </li>
          <li>
            <span>Timeline</span>
            <strong>{profile.timelineWeeks} weeks</strong>
          </li>
          <li>
            <span>Activity</span>
            <strong>{activityLabel(profile.activityLevel)}</strong>
          </li>
        </ul>
        <p className="fine-print">
          Calorie estimates are approximate, not medical advice.{' '}
          <Link to="/profile">Edit your profile</Link> to change the goal.
        </p>
      </div>
    </div>
  )
}
