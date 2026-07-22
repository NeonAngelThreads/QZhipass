import http, { getErrorMessage } from './http'
import { saveLoginInfo, type LoginInfo } from './session'

interface PortalLoginResponse {
  success?: boolean
  message?: string
  data?: Record<string, unknown>
  user_id?: unknown
  userId?: unknown
  access_token?: unknown
  accessToken?: unknown
  token?: unknown
  initialConversationId?: unknown
}

const MOBILE_PATTERN = /^1[3-9]\d{9}$/

export function isValidMobile(mobile: string) {
  return MOBILE_PATTERN.test(mobile)
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function readIdentifier(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  return readString(value)
}

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

function normalizeLoginInfo(response: PortalLoginResponse): LoginInfo {
  if (response.success === false) throw new Error(response.message || '登录失败')
  const payload = response.data && typeof response.data === 'object' ? response.data : {}
  const conversation = payload.conversation && typeof payload.conversation === 'object'
    ? payload.conversation as Record<string, unknown>
    : {}
  const userId = readIdentifier(payload.userId)
    || readIdentifier(payload.user_id)
    || readIdentifier(payload.id)
    || readIdentifier(response.userId)
    || readIdentifier(response.user_id)
  const accessToken = readString(payload.accessToken)
    || readString(payload.access_token)
    || readString(payload.token)
    || readString(response.accessToken)
    || readString(response.access_token)
    || readString(response.token)
  const initialConversationId = readNumber(payload.initialConversationId)
    || readNumber(payload.initial_conversation_id)
    || readNumber(response.initialConversationId)
    || readNumber(conversation.id)

  if (!userId) throw new Error('登录成功但后端未返回用户 ID')
  if (!accessToken) throw new Error('登录成功但后端未返回访问令牌')
  return {
    userId,
    accessToken,
    role: readString(payload.role) || 'USER',
    initialConversationId
  }
}

async function login(loginType: string, params: Record<string, string>, fallback: string) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/v1/auth/portal/login', {
      loginType,
      params
    })
    const info = normalizeLoginInfo(data)
    saveLoginInfo(info)
    return info
  } catch (error) {
    throw new Error(getErrorMessage(error, fallback))
  }
}

export function loginByPhonePassword(phone: string, password: string) {
  return login('MOBILE_PWD', { mobile: phone, password }, '手机号或密码登录失败')
}

export function loginByEmailPassword(email: string, password: string) {
  return login('EMAIL_PWD', { email, password }, '邮箱或密码登录失败')
}

export function loginBySms(phone: string, code: string) {
  return login('MOBILE_CODE', { phone, smsCode: code }, '手机号或验证码登录失败')
}

export async function sendSmsCode(phone: string) {
  try {
    await http.post('/v1/auth/portal/send_code', { phone })
  } catch (error) {
    throw new Error(getErrorMessage(error, '验证码发送失败'))
  }
}

export async function logoutPortal() {
  try {
    await http.delete('/v1/auth/portal/logout')
  } catch {
    // JWT logout is completed by clearing local state even when the backend is unavailable.
  }
}
