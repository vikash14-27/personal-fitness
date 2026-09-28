import { ACTIVITY_OPTIONS, SEX_OPTIONS } from '../options'
import type { BioInput } from '../validation'
import { Field } from './Field'

export function BioFields({
  values,
  errors,
  onChange,
}: {
  values: BioInput
  errors: Record<string, string>
  onChange: (patch: Partial<BioInput>) => void
}) {
  return (
    <>
      <div className="split">
        <Field label="Age" error={errors.age} hint="Years, 16–100">
          <input
            inputMode="numeric"
            value={values.age}
            onChange={(event) => onChange({ age: event.target.value })}
            data-testid="age"
          />
        </Field>
        <fieldset className={errors.sex ? 'choice-set invalid' : 'choice-set'}>
          <legend>Sex</legend>
          <div className="choice-row">
            {SEX_OPTIONS.map((option) => (
              <label key={option.value} className={values.sex === option.value ? 'choice selected' : 'choice'}>
                <input
                  type="radio"
                  name="sex"
                  value={option.value}
                  checked={values.sex === option.value}
                  onChange={() => onChange({ sex: option.value })}
                />
                {option.label}
              </label>
            ))}
          </div>
          {errors.sex ? <small role="alert">{errors.sex}</small> : <small className="hint">Used for the Mifflin–St Jeor equation.</small>}
        </fieldset>
      </div>

      <div className="split">
        <Field label="Height (cm)" error={errors.heightCm} hint="120–250">
          <input
            inputMode="decimal"
            value={values.heightCm}
            onChange={(event) => onChange({ heightCm: event.target.value })}
            data-testid="height"
          />
        </Field>
        <Field label="Current weight (kg)" error={errors.currentWeightKg} hint="30–300">
          <input
            inputMode="decimal"
            value={values.currentWeightKg}
            onChange={(event) => onChange({ currentWeightKg: event.target.value })}
            data-testid="current-weight-input"
          />
        </Field>
      </div>

      <Field label="Target weight (kg)" error={errors.targetWeightKg} hint="30–300">
        <input
          inputMode="decimal"
          value={values.targetWeightKg}
          onChange={(event) => onChange({ targetWeightKg: event.target.value })}
          data-testid="target-weight-input"
        />
      </Field>

      <fieldset className={errors.activityLevel ? 'choice-set invalid' : 'choice-set'}>
        <legend>Activity level</legend>
        <div className="activity-list">
          {ACTIVITY_OPTIONS.map((option) => (
            <label key={option.value} className={values.activityLevel === option.value ? 'activity selected' : 'activity'}>
              <input
                type="radio"
                name="activityLevel"
                value={option.value}
                checked={values.activityLevel === option.value}
                onChange={() => onChange({ activityLevel: option.value })}
              />
              <span>
                <strong>{option.label}</strong>
                <small>{option.detail}</small>
              </span>
            </label>
          ))}
        </div>
        {errors.activityLevel ? <small role="alert">{errors.activityLevel}</small> : null}
      </fieldset>

      <Field label="Timeline to goal (weeks)" error={errors.timelineWeeks} hint="1–104 weeks">
        <input
          inputMode="numeric"
          value={values.timelineWeeks}
          onChange={(event) => onChange({ timelineWeeks: event.target.value })}
          data-testid="timeline"
        />
      </Field>
    </>
  )
}
