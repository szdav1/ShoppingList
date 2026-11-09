export interface User {
  id: number
  username: string
  admin: boolean
  createdAt: string
}

export interface AuthSession {
  user: User
  accessToken: string
  tokenType: string
  expiresAt: string
}

interface LoginResponse extends AuthSession {}

const sessionStorageKey = 'shopping-list-auth-session'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, init)
  } catch {
    throw new ApiError('Nem sikerült kapcsolódni a szerverhez. Próbáld újra később.', 0)
  }

  if (response.status === 204) return undefined as T

  let body: unknown
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (!response.ok) {
    const message =
      typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
        ? body.message
        : 'A kérés nem sikerült. Próbáld újra.'
    throw new ApiError(message, response.status)
  }

  return body as T
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/users/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export function getCurrentUser(accessToken: string, signal?: AbortSignal): Promise<User> {
  return apiRequest<User>('/api/users/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal,
  })
}

export function logout(accessToken: string, tokenType: string): Promise<void> {
  return apiRequest<void>('/api/users/logout', {
    method: 'POST',
    headers: { Authorization: `${tokenType} ${accessToken}` },
  })
}

export function saveSession(session: AuthSession): void {
  localStorage.setItem(sessionStorageKey, JSON.stringify(session))
}

export function loadSession(): AuthSession | null {
  const serialized = localStorage.getItem(sessionStorageKey)
  if (!serialized) return null

  try {
    const session = JSON.parse(serialized) as Partial<AuthSession>
    const validSession =
      typeof session.accessToken === 'string' &&
      typeof session.tokenType === 'string' &&
      typeof session.expiresAt === 'string' &&
      typeof session.user === 'object' &&
      session.user !== null &&
      typeof session.user.username === 'string'

    if (!validSession || Date.parse(session.expiresAt as string) <= Date.now()) {
      clearSession()
      return null
    }

    return session as AuthSession
  } catch {
    clearSession()
    return null
  }
}

export function clearSession(): void {
  localStorage.removeItem(sessionStorageKey)
}