export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US').format(value)
}

export function formatKg(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value)
}
