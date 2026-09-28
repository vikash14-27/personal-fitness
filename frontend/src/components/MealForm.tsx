import { useState, type FormEvent } from 'react'
import { api, ApiRequestError } from '../api'
import { formatDay } from '../format'
import type { MealEstimate } from '../types'
import { validateEstimateText, validateMeal, type MealInput } from '../validation'
import { Field } from './Field'

export type MealPayload = {
  day: string
  description: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
}

const emptyDraft = (): MealInput => ({
  description: '',
  calories: '',
  proteinG: '',
  carbsG: '',
  fatG: '',
})

function macroInput(value: number): string {
  if (!Number.isFinite(value)) return ''
  return String(Math.round(value * 10) / 10)
}

export function mealToInput(meal: {
  description: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
}): MealInput {
  return {
    description: meal.description,
    calories: String(meal.calories),
    proteinG: macroInput(meal.proteinG),
    carbsG: macroInput(meal.carbsG),
    fatG: macroInput(meal.fatG),
  }
}

export function MealForm({
  token,
  day,
  initial,
  submitLabel,
  allowDayEdit = false,
  onSubmit,
  onCancel,
}: {
  token: string
  day: string
  initial?: MealInput | null
  submitLabel: string
  allowDayEdit?: boolean
  onSubmit: (payload: MealPayload) => Promise<void>
  onCancel?: () => void
}) {
  const prefix = allowDayEdit ? 'edit-meal' : 'meal'
  const [text, setText] = useState(initial?.description ?? '')
  const [draft, setDraft] = useState<MealInput>(initial ?? emptyDraft())
  const [mealDay, setMealDay] = useState(day)
  const [textError, setTextError] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [estimateError, setEstimateError] = useState('')
  const [formError, setFormError] = useState('')
  const [estimating, setEstimating] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function patch(next: Partial<MealInput>) {
    setDraft((current) => ({ ...current, ...next }))
  }

  async function estimate() {
    const problem = validateEstimateText(text)
    setTextError(problem ?? '')
    setEstimateError('')
    setFormError('')
    if (problem) return
    setEstimating(true)
    try {
      const estimateResult = await api<MealEstimate>('/api/meals/estimate', {
        method: 'POST',
        body: JSON.stringify({ text: text.trim() }),
      }, token)
      setDraft({
        description: estimateResult.description,
        calories: String(estimateResult.calories),
        proteinG: macroInput(estimateResult.proteinG),
        carbsG: macroInput(estimateResult.carbsG),
        fatG: macroInput(estimateResult.fatG),
      })
      setErrors({})
    } catch (error) {
      const message = error instanceof ApiRequestError
        ? error.message
        : 'Couldn’t estimate that meal. Enter the calories and macros yourself.'
      setEstimateError(message)
      setDraft((current) => ({
        ...current,
        description: current.description.trim() || text.trim().slice(0, 300),
      }))
    } finally {
      setEstimating(false)
    }
  }

  async function onSubmitForm(event: FormEvent) {
    event.preventDefault()
    const description = draft.description.trim() || text.trim()
    const nextErrors = validateMeal({ ...draft, description })
    const chosenDay = allowDayEdit ? mealDay : day
    if (allowDayEdit && !chosenDay) nextErrors.day = 'Pick a day'
    setErrors(nextErrors)
    setFormError('')
    setEstimateError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      await onSubmit({
        day: chosenDay,
        description,
        calories: Number(draft.calories),
        proteinG: Number(draft.proteinG),
        carbsG: Number(draft.carbsG),
        fatG: Number(draft.fatG),
      })
      if (!allowDayEdit) {
        setText('')
        setDraft(emptyDraft())
        setErrors({})
        setTextError('')
      }
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
    <form className="form" noValidate onSubmit={onSubmitForm} data-testid={allowDayEdit ? 'edit-meal-form' : 'meal-form'}>
      {formError ? <p className="banner" role="alert" data-testid={`${prefix}-form-error`}>{formError}</p> : null}
      {estimateError ? <p className="banner" role="alert" data-testid={allowDayEdit ? 'edit-estimate-error' : 'estimate-error'}>{estimateError}</p> : null}
      <Field
        label="What did you eat?"
        error={textError}
        hint="A sentence is enough. Estimate fills the numbers. Change them, or type them yourself, before you save."
      >
        <textarea
          value={text}
          rows={3}
          onChange={(event) => setText(event.target.value)}
          data-testid={`${prefix}-text`}
        />
      </Field>
      <div className="row-actions">
        <button
          type="button"
          className="ghost"
          disabled={estimating || text.trim().length === 0}
          data-testid={`${prefix}-estimate`}
          onClick={() => void estimate()}
        >
          {estimating ? 'Estimating…' : 'Estimate calories'}
        </button>
      </div>
      {allowDayEdit ? (
        <Field label="Day" error={errors.day}>
          <input
            type="date"
            value={mealDay}
            onChange={(event) => setMealDay(event.target.value)}
            data-testid="edit-meal-day"
          />
        </Field>
      ) : (
        <p className="hint">Saving to {formatDay(day)}.</p>
      )}
      <Field label="Name" error={errors.description}>
        <input
          value={draft.description}
          onChange={(event) => patch({ description: event.target.value })}
          data-testid={`${prefix}-description`}
        />
      </Field>
      <div className="split">
        <Field label="Calories" error={errors.calories}>
          <input
            inputMode="numeric"
            value={draft.calories}
            onChange={(event) => patch({ calories: event.target.value })}
            data-testid={`${prefix}-calories`}
          />
        </Field>
        <Field label="Protein (g)" error={errors.proteinG}>
          <input
            inputMode="decimal"
            value={draft.proteinG}
            onChange={(event) => patch({ proteinG: event.target.value })}
            data-testid={`${prefix}-protein`}
          />
        </Field>
      </div>
      <div className="split">
        <Field label="Carbs (g)" error={errors.carbsG}>
          <input
            inputMode="decimal"
            value={draft.carbsG}
            onChange={(event) => patch({ carbsG: event.target.value })}
            data-testid={`${prefix}-carbs`}
          />
        </Field>
        <Field label="Fat (g)" error={errors.fatG}>
          <input
            inputMode="decimal"
            value={draft.fatG}
            onChange={(event) => patch({ fatG: event.target.value })}
            data-testid={`${prefix}-fat`}
          />
        </Field>
      </div>
      <div className="row-actions">
        <button type="submit" className="primary" disabled={submitting} data-testid={allowDayEdit ? 'save-meal-edit' : 'save-meal'}>
          {submitting ? 'Saving…' : submitLabel}
        </button>
        {onCancel ? (
          <button type="button" className="ghost" onClick={onCancel}>Cancel</button>
        ) : null}
      </div>
    </form>
  )
}
