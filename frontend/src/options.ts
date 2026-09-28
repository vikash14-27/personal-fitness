import type { ActivityLevel, Sex } from './types'

export const SEX_OPTIONS: { value: Sex; label: string }[] = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
]

export const ACTIVITY_OPTIONS: { value: ActivityLevel; label: string; detail: string }[] = [
  { value: 'SEDENTARY', label: 'Sedentary', detail: 'Desk job, little or no exercise' },
  { value: 'LIGHTLY_ACTIVE', label: 'Lightly active', detail: 'Exercise 1–3 days a week' },
  { value: 'MODERATELY_ACTIVE', label: 'Moderately active', detail: 'Exercise 3–5 days a week' },
  { value: 'VERY_ACTIVE', label: 'Very active', detail: 'Exercise 6–7 days a week' },
  { value: 'EXTRA_ACTIVE', label: 'Extra active', detail: 'Physical job or twice-a-day training' },
]

export function activityLabel(level: ActivityLevel): string {
  return ACTIVITY_OPTIONS.find((option) => option.value === level)?.label ?? level
}

export function sexFloor(sex: Sex | ''): number | null {
  if (sex === 'MALE') return 1500
  if (sex === 'FEMALE') return 1200
  return null
}
