import type { ActivityLevel, Sex } from './types'

export type BioInput = {
  age: string
  sex: Sex | ''
  heightCm: string
  currentWeightKg: string
  targetWeightKg: string
  activityLevel: ActivityLevel | ''
  timelineWeeks: string
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function requiredDecimal(raw: string, label: string, min: number, max: number, rangeMessage: string): string | null {
  const value = raw.trim()
  if (!value) return `${label} is required`
  if (!/^\d+(\.\d)?$/.test(value)) return `${label} can have at most one decimal place`
  const number = Number(value)
  if (number < min || number > max) return rangeMessage
  return null
}

export function validateAccount(email: string, password: string): Record<string, string> {
  const errors: Record<string, string> = {}
  const trimmed = email.trim()
  if (!trimmed) errors.email = 'Email is required'
  else if (trimmed.length > 254 || !EMAIL.test(trimmed)) errors.email = 'Enter a valid email address'
  if (!password.trim()) errors.password = 'Password is required'
  else if (password.length < 8 || password.length > 72) errors.password = 'Password must be 8–72 characters'
  return errors
}

export function validateBio(input: BioInput): Record<string, string> {
  const errors: Record<string, string> = {}
  const age = input.age.trim()
  if (!age) errors.age = 'Age is required'
  else if (!/^\d+$/.test(age) || Number(age) < 16 || Number(age) > 100) {
    errors.age = 'Age must be between 16 and 100'
  }
  if (!input.sex) errors.sex = 'Sex is required'
  const height = requiredDecimal(input.heightCm, 'Height', 120, 250, 'Height must be between 120 and 250 cm')
  if (height) errors.heightCm = height
  const current = requiredDecimal(
    input.currentWeightKg,
    'Current weight',
    30,
    300,
    'Current weight must be between 30 and 300 kg',
  )
  if (current) errors.currentWeightKg = current
  const target = requiredDecimal(
    input.targetWeightKg,
    'Target weight',
    30,
    300,
    'Target weight must be between 30 and 300 kg',
  )
  if (target) errors.targetWeightKg = target
  if (!input.activityLevel) errors.activityLevel = 'Activity level is required'
  const weeks = input.timelineWeeks.trim()
  if (!weeks) errors.timelineWeeks = 'Timeline is required'
  else if (!/^\d+$/.test(weeks) || Number(weeks) < 1 || Number(weeks) > 104) {
    errors.timelineWeeks = 'Timeline must be between 1 and 104 weeks'
  }
  return errors
}

export function validateOverride(enabled: boolean, raw: string): string | null {
  if (!enabled) return null
  const value = raw.trim()
  if (!value) return 'Enter a manual calorie goal or turn the override off'
  if (!/^\d+$/.test(value)) return 'Manual calorie goal must be a whole number'
  const kcal = Number(value)
  if (kcal < 1000 || kcal > 5000) return 'Manual calorie goal must be between 1000 and 5000 kcal'
  return null
}
