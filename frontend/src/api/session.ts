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
  if (data.role) {
    window.localStorage.setItem(ROLE_KEY, data.role)
  } else {
    window.localStorage.removeItem(ROLE_KEY)
  }
}

export function saveInitialConversationId(initialConversationId: number) {
  window.localStorage.setItem(INITIAL_CONVERSATION_ID_KEY, String(initialConversationId))
}

export function readLoginInfo(): LoginInfo | null {
  const userId = window.localStorage.getItem(USER_ID_KEY)
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  const initialConversationId = Number(window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY))
  const storedRole = window.localStorage.getItem(ROLE_KEY)
  const role = storedRole === 'ADMIN' || storedRole === 'USER' ? storedRole : undefined

  if (!userId || !accessToken) {
    if (import.meta.env.DEV) {
      return {
        userId: '9001',
        accessToken: 'local-dev'
      }
    }
    return null
  }

  return {
    userId,
    accessToken,
    initialConversationId: Number.isFinite(initialConversationId) && initialConversationId > 0
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
  return Boolean(readLoginInfo())
}

export function isAdmin() {
  return readLoginInfo()?.role === 'ADMIN'
}
