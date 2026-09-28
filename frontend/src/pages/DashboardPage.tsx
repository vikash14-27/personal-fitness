import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { GoalSummary } from '../components/GoalSummary'
import { formatKg } from '../format'
import { activityLabel } from '../options'

export function DashboardPage() {
  const { profile, refresh } = useAuth()

  useEffect(() => {
    document.title = 'Dashboard · Personal Fitness'
    void refresh().catch(() => undefined)
  }, [refresh])

  if (!profile) return <p className="status">Loading your goal…</p>

  return (
    <div className="dashboard">
      <div className="dash-head">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h1>Today’s baseline</h1>
          <p className="lede">Signed in as {profile.email}</p>
        </div>
        <Link className="primary link-button" to="/profile">Edit profile</Link>
      </div>
      <GoalSummary profile={profile} />
      <ul className="stats">
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
      <p className="fine-print">An estimate for planning, not medical advice. Change the inputs any time on your profile.</p>
    </div>
  )
}
