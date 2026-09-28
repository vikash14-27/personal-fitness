export type Sex = 'MALE' | 'FEMALE'

export type ActivityLevel =
  | 'SEDENTARY'
  | 'LIGHTLY_ACTIVE'
  | 'MODERATELY_ACTIVE'
  | 'VERY_ACTIVE'
  | 'EXTRA_ACTIVE'

export type CalorieGoalSource = 'COMPUTED' | 'OVERRIDE'
export type ClampReason = 'NONE' | 'FLOOR' | 'CEILING'

export type Profile = {
  id: string
  email: string
  age: number
  sex: Sex
  heightCm: number
  currentWeightKg: number
  targetWeightKg: number
  activityLevel: ActivityLevel
  timelineWeeks: number
  bmr: number
  tdee: number
  activityMultiplier: number
  dailyAdjustment: number
  computedCalorieGoal: number
  calorieGoalOverride: number | null
  activeCalorieGoal: number
  calorieGoalSource: CalorieGoalSource
  calorieGoalClamp: ClampReason
}

export type AuthResponse = {
  token: string
  profile: Profile
}

export type SignupPayload = {
  email: string
  password: string
  age: number
  sex: Sex
  heightCm: number
  currentWeightKg: number
  targetWeightKg: number
  activityLevel: ActivityLevel
  timelineWeeks: number
}

export type Meal = {
  id: string
  day: string
  description: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
}

export type MealEstimate = {
  description: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
}

export type MacroTotals = {
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
}

export type DayView = {
  day: string
  calorieGoal: number
  calorieGoalSource: CalorieGoalSource
  meals: Meal[]
  totals: MacroTotals
  remainingCalories: number
}

export type ProfilePayload = {
  age: number
  sex: Sex
  heightCm: number
  currentWeightKg: number
  targetWeightKg: number
  activityLevel: ActivityLevel
  timelineWeeks: number
  calorieGoalOverride: number | null
}
