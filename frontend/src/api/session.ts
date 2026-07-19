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
  // DEV: 临时绕过登录校验
  return {
    userId: 'dev-test-user',
    accessToken: 'dev-bypass-token',
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(ROLE_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function isLoggedIn() {
  // DEV: 临时绕过登录校验
  return true
}

export function isAdmin() {
  // DEV: 临时设为主
  return true
}