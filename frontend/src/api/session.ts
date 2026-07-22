export interface LoginInfo {
  userId: string
  accessToken: string
  role?: string
  initialConversationId?: number
}

const STORAGE_KEY = 'qzhipass_login'
const USER_ID_KEY = 'user_id'
const ACCESS_TOKEN_KEY = 'access_token'
const ROLE_KEY = 'user_role'
const INITIAL_CONVERSATION_ID_KEY = 'initial_conversation_id'

export function saveLoginInfo(info: LoginInfo) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(info))
  window.localStorage.setItem(USER_ID_KEY, info.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, info.accessToken)
  if (info.role) window.localStorage.setItem(ROLE_KEY, info.role)
  else window.localStorage.removeItem(ROLE_KEY)
  if (info.initialConversationId) saveInitialConversationId(info.initialConversationId)
  else window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function saveInitialConversationId(initialConversationId: number) {
  window.localStorage.setItem(INITIAL_CONVERSATION_ID_KEY, String(initialConversationId))
  const current = readLoginInfo()
  if (current) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...current, initialConversationId }))
  }
}

export function readLoginInfo(): LoginInfo | null {
  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (stored) {
    try {
      const value = JSON.parse(stored) as Partial<LoginInfo>
      if (value.userId && value.accessToken) {
        return {
          userId: String(value.userId),
          accessToken: value.accessToken,
          role: value.role || 'USER',
          initialConversationId: value.initialConversationId
        }
      }
    } catch {
      // Fall through to legacy keys from the original frontend.
    }
  }

  const userId = window.localStorage.getItem(USER_ID_KEY)
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  if (!userId || !accessToken) return null
  const initialConversationId = Number(window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY))
  return {
    userId,
    accessToken,
    role: window.localStorage.getItem(ROLE_KEY) || 'USER',
    initialConversationId: Number.isInteger(initialConversationId) && initialConversationId > 0
      ? initialConversationId
      : undefined
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(STORAGE_KEY)
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(ROLE_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function isLoggedIn() {
  return Boolean(readLoginInfo())
}

export function isAdmin() {
  return readLoginInfo()?.role === 'ADMIN'
}
