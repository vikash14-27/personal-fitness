import type { ReactNode } from 'react'

export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <label className={error ? 'field invalid' : 'field'}>
      <span>{label}</span>
      {children}
      {error ? <small role="alert">{error}</small> : hint ? <small className="hint">{hint}</small> : null}
    </label>
  )
}
