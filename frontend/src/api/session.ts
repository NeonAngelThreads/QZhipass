export interface LoginInfo {
  userId: string
  accessToken: string
  role?: string
  initialConversationId?: number
}

const USER_ID_KEY = 'user_id'
const ACCESS_TOKEN_KEY = 'access_token'
const ROLE_KEY = 'user_role'
const INITIAL_CONVERSATION_ID_KEY = 'initial_conversation_id'

export function saveLoginInfo(data: LoginInfo) {
  window.localStorage.setItem(USER_ID_KEY, data.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
  if (data.role) {
    window.localStorage.setItem(ROLE_KEY, data.role)
  } else {
    window.localStorage.removeItem(ROLE_KEY)
  }
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
  const userId = window.localStorage.getItem(USER_ID_KEY)?.trim()
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)?.trim()
  if (!userId || !accessToken) {
    return null
  }

  const initialConversationIdValue = window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY)
  const initialConversationId = initialConversationIdValue ? Number(initialConversationIdValue) : undefined
  return {
    userId,
    accessToken,
    role: window.localStorage.getItem(ROLE_KEY) || undefined,
    initialConversationId: Number.isFinite(initialConversationId) ? initialConversationId : undefined
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(ROLE_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function isLoggedIn() {
  return readLoginInfo() !== null
}

export function isAdmin() {
  return readLoginInfo()?.role === 'ADMIN'
}
