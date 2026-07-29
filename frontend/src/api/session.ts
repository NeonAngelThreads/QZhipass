export type UserRole = 'USER' | 'ADMIN'

export interface LoginInfo {
  userId: string
  accessToken: string
  initialConversationId?: number
  role?: UserRole
}

const USER_ID_KEY = 'user_id'
const ACCESS_TOKEN_KEY = 'access_token'
const INITIAL_CONVERSATION_ID_KEY = 'initial_conversation_id'
const ROLE_KEY = 'user_role'

export function saveLoginInfo(data: LoginInfo) {
  window.localStorage.setItem(USER_ID_KEY, data.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
  if (data.initialConversationId) {
    saveInitialConversationId(data.initialConversationId)
  } else {
    window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
  }
  if (data.role === 'USER' || data.role === 'ADMIN') {
    window.localStorage.setItem(ROLE_KEY, data.role)
  } else {
    window.localStorage.removeItem(ROLE_KEY)
  }
}

export function saveInitialConversationId(initialConversationId: number) {
  window.localStorage.setItem(INITIAL_CONVERSATION_ID_KEY, String(initialConversationId))
}

export function readLoginInfo(): LoginInfo | null {
  const userId = window.localStorage.getItem(USER_ID_KEY)?.trim()
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)?.trim()
  const storedRole = window.localStorage.getItem(ROLE_KEY)
  const role = storedRole === 'ADMIN' || storedRole === 'USER'
    ? storedRole
    : undefined

  if (!userId || !accessToken) {
    return null
  }

  const initialConversationIdValue = window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY)
  const initialConversationId = initialConversationIdValue ? Number(initialConversationIdValue) : undefined
  return {
    userId,
    accessToken,
    initialConversationId: typeof initialConversationId === 'number'
      && Number.isFinite(initialConversationId)
      && initialConversationId > 0
      ? initialConversationId
      : undefined,
    role
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
  window.localStorage.removeItem(ROLE_KEY)
}

export function isLoggedIn() {
  return readLoginInfo() !== null
}

export function isAdmin() {
  return readLoginInfo()?.role === 'ADMIN'
}
