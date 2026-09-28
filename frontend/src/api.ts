export class ApiRequestError extends Error {
  status: number
  code?: string
  fields: Record<string, string>

  constructor(status: number, message: string, code?: string, fields?: Record<string, string>) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.fields = fields ?? {}
  }
}

type ErrorBody = {
  error?: string
  message?: string
  fields?: Record<string, string>
}

export async function api<T>(path: string, options: RequestInit = {}, token?: string | null): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new ApiRequestError(0, 'Can’t reach the server. Start the API and try again.')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let data: ErrorBody | null = null
  if (text) {
    try {
      data = JSON.parse(text) as ErrorBody
    } catch {
      data = { message: text }
    }
  }

  if (!response.ok) {
    throw new ApiRequestError(
      response.status,
      data?.message || 'Request failed.',
      data?.error,
      data?.fields,
    )
  }

  return data as T
}
