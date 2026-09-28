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
