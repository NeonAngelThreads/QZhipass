export type UserRole = 'ADMIN' | 'USER'

export interface LoginInfo {
  userId: string
  accessToken: string
  role: UserRole
  initialConversationId?: number
}

const USER_ID_KEY = 'id'
const ACCESS_TOKEN_KEY = 'access_token'
const ROLE_KEY = 'role'
const INITIAL_CONVERSATION_ID_KEY = 'initial_conversation_id'

export function normalizeUserRole(value: unknown): UserRole {
  if (typeof value !== 'string') return 'USER'
  return value.trim().toUpperCase() === 'ADMIN' ? 'ADMIN' : 'USER'
}

export function saveLoginInfo(data: LoginInfo) {
  window.localStorage.setItem(USER_ID_KEY, data.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
  window.localStorage.setItem(ROLE_KEY, normalizeUserRole(data.role))
  if (data.initialConversationId) {
    saveInitialConversationId(data.initialConversationId)
  } else {
    window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
  }
}

export function saveInitialConversationId(initialConversationId: number) {
  window.localStorage.setItem(INITIAL_CONVERSATION_ID_KEY, String(initialConversationId))
}

export function readLoginInfo(): LoginInfo | null {
  const userId = window.localStorage.getItem(USER_ID_KEY)
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  const role = normalizeUserRole(window.localStorage.getItem(ROLE_KEY))
  const initialConversationId = Number(window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY))

  if (!userId || !accessToken) {
    clearLoginInfo()
    return null
  }

  return {
    userId,
    accessToken,
    role,
    initialConversationId: Number.isFinite(initialConversationId) && initialConversationId > 0
      ? initialConversationId
      : undefined
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(ROLE_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function isLoggedIn() {
  return Boolean(readLoginInfo())
}

