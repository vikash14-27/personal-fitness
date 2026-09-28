import { formatNumber } from '../format'
import type { Profile } from '../types'

export function GoalSummary({ profile }: { profile: Profile }) {
  const overridden = profile.calorieGoalSource === 'OVERRIDE'
  const floor = profile.sex === 'MALE' ? '1,500' : '1,200'
  const adjustment =
    profile.dailyAdjustment === 0
      ? 'Even with maintenance'
      : profile.dailyAdjustment > 0
        ? `${formatNumber(profile.dailyAdjustment)} kcal deficit`
        : `${formatNumber(Math.abs(profile.dailyAdjustment))} kcal surplus`

  return (
    <section className="goal-card">
      <div className="goal-main">
        <p className="eyebrow">Daily calorie goal</p>
        <div className="goal-row">
          <p className="goal" data-testid="calorie-goal">{formatNumber(profile.activeCalorieGoal)}</p>
          <span className="goal-unit">kcal</span>
        </div>
        <p className={overridden ? 'badge override' : 'badge computed'} data-testid="goal-source">
          {overridden ? 'Manual override' : 'Computed'}
        </p>
      </div>
      <p className="goal-copy">
        {overridden
          ? `You're using a manual goal. The formula currently computes ${formatNumber(profile.computedCalorieGoal)} kcal.`
          : 'Calculated from Mifflin–St Jeor, your activity level, and the pace to your target weight.'}
      </p>
      {overridden ? (
        <p className="computed-line" data-testid="computed-goal">
          Computed goal: {formatNumber(profile.computedCalorieGoal)} kcal
        </p>
      ) : null}
      {profile.calorieGoalClamp !== 'NONE' ? (
        <p className="callout" data-testid="clamp-note">
          {profile.calorieGoalClamp === 'FLOOR'
            ? `The formula fell below a safe minimum, so the computed goal was raised to ${floor} kcal.`
            : 'The formula rose above 5,000 kcal, so the computed goal was capped there.'}
          {overridden ? ' Your manual number is the one in use.' : ''}
        </p>
      ) : null}
      <h2>Formula breakdown</h2>
      <dl className="breakdown">
        <div>
          <dt>BMR</dt>
          <dd>{formatNumber(profile.bmr)} kcal</dd>
        </div>
        <div>
          <dt>Activity</dt>
          <dd>× {profile.activityMultiplier}</dd>
        </div>
        <div>
          <dt>Maintenance</dt>
          <dd>{formatNumber(profile.tdee)} kcal</dd>
        </div>
        <div>
          <dt>Plan vs maintenance</dt>
          <dd>{adjustment}</dd>
        </div>
      </dl>
    </section>
  )
}
