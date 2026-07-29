import http, {getErrorMessage} from './http'
import {type LoginInfo, saveLoginInfo, type UserRole} from './session'

type PortalLoginType = 'MOBILE_PWD' | 'EMAIL_PWD' | 'MOBILE_CODE'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T | null
}

interface PortalLoginResponse {
  success?: boolean
  message?: string
  data?: Record<string, unknown>
  user_id?: unknown
  userId?: unknown
  access_token?: unknown
  accessToken?: unknown
  token?: unknown
  role?: unknown
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const MOBILE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidMobile(mobile: string) {
  return MOBILE_PATTERN.test(mobile)
}

export function isValidEmail(email: string) {
  return EMAIL_PATTERN.test(email)
}

export function isValidAccount(account: string) {
  return isValidMobile(account) || isValidEmail(account)
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function readIdentifier(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }
  return readString(value)
}

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

function readRole(value: unknown): UserRole | undefined {
  return value === 'ADMIN' || value === 'USER'
    ? value
    : undefined
}

function normalizeLoginInfo(response: PortalLoginResponse): LoginInfo {
  if (response.success === false) {
    throw new Error(response.message || '登录失败')
  }

  const payload = response.data && typeof response.data === 'object' ? response.data : {}
  const conversationPayload =
    payload.conversation && typeof payload.conversation === 'object'
      ? payload.conversation as Record<string, unknown>
      : {}
  const userId =
    readIdentifier(payload.user_id) ||
    readIdentifier(payload.userId) ||
    readIdentifier(response.user_id) ||
    readIdentifier(response.userId)
  const accessToken =
    readString(payload.access_token) ||
    readString(payload.accessToken) ||
    readString(payload.token) ||
    readString(response.access_token) ||
    readString(response.accessToken) ||
    readString(response.token)
  const initialConversationId =
    readNumber(payload.initialConversationId) ||
    readNumber(payload.initial_conversation_id) ||
    readNumber(conversationPayload.id)
  const role =
    readRole(response.role) ||
    readRole(payload.role)

  if (!userId) {
    throw new Error('登录成功但后端未返回 user_id')
  }

  if (!accessToken) {
    throw new Error('登录成功但后端未返回 access_token')
  }

  return {
    userId,
    accessToken,
    initialConversationId,
    role
  }
}

async function login(loginType: PortalLoginType, credential: Record<string, string>) {
  try {
    const {data} = await http.post<PortalLoginResponse>('/v1/auth/portal/login', {
      loginType,
      credential
    })
    const loginInfo = normalizeLoginInfo(data)
    saveLoginInfo(loginInfo)
    return loginInfo
  } catch (error) {
    throw new Error(getErrorMessage(error, '请求失败，请稍后重试'))
  }
}

export async function loginByPassword(account: string, password: string) {
  const loginType: PortalLoginType = isValidMobile(account) ? 'MOBILE_PWD' : 'EMAIL_PWD'
  const accountField = loginType === 'MOBILE_PWD' ? 'mobile' : 'email'
  return login(loginType, {[accountField]: account, password})
}

export async function sendSmsCode(mobile: string) {
  try {
    const {data} = await http.post<ApiResponse<null>>('/v1/auth/portal/send_code', {phone: mobile})
    if (!data.success) {
      throw new Error(data.message || '验证码发送失败')
    }
  } catch (error) {
    throw new Error(getErrorMessage(error, '请求失败，请稍后重试'))
  }
}

export async function loginBySms(mobile: string, smsCode: string) {
  return login('MOBILE_CODE', {mobile, smsCode})
}

export async function changePassword(request: ChangePasswordRequest) {
  try {
    const {data} = await http.put<ApiResponse<null>>('/v1/account/password', request)
    if (!data.success) {
      throw new Error(data.message || '修改密码失败')
    }
  } catch (error) {
    throw new Error(getErrorMessage(error, '请求失败，请稍后重试'))
  }
}
