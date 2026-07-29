import http, { getErrorMessage } from './http'
import { type LoginInfo, saveLoginInfo, type UserRole } from './session'

type PortalLoginType = 'MOBILE_PWD' | 'EMAIL_PWD' | 'mobile'

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

interface LoginStatusResponse {
  login?: boolean
}

const MOBILE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidMobile(mobile: string) {
  return MOBILE_PATTERN.test(mobile)
}

export function isValidEmail(email: string) {
  return EMAIL_PATTERN.test(email)
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

function readRole(value: unknown): UserRole | undefined {
  return value === 'ADMIN' || value === 'USER' ? value : undefined
}

function normalizeLoginInfo(response: PortalLoginResponse): LoginInfo {
  if (response.success === false) {
    throw new Error(response.message || '登录失败')
  }

  const payload = response.data && typeof response.data === 'object' ? response.data : {}
  const userPayload =
    payload.user && typeof payload.user === 'object'
      ? payload.user as Record<string, unknown>
      : {}
  const conversationPayload =
    payload.conversation && typeof payload.conversation === 'object'
      ? payload.conversation as Record<string, unknown>
      : {}
  const userId =
    readIdentifier(payload.user_id) ||
    readIdentifier(payload.userId) ||
    readIdentifier(payload.id) ||
    readIdentifier(userPayload.id) ||
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
    readIdentifier(payload.initialConversationId) ||
    readIdentifier(payload.initial_conversation_id) ||
    readIdentifier(conversationPayload.id) ||
    undefined
  const role =
    readRole(response.role) ||
    readRole(payload.role) ||
    readRole(userPayload.role)

  if (!userId) {
    throw new Error('登录成功但后端未返回用户 ID')
  }

  if (!accessToken) {
    throw new Error('登录成功但后端未返回访问令牌')
  }

  return {
    userId,
    accessToken,
    initialConversationId,
    role
  }
}

async function login(
  loginType: PortalLoginType,
  credential: Record<string, string>,
  fallback: string
) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/v1/auth/portal/login', {
      loginType,
      credential
    })
    const loginInfo = normalizeLoginInfo(data)

    saveLoginInfo(loginInfo)
    return loginInfo
  } catch (error) {
    throw new Error(getErrorMessage(error, fallback))
  }
}

export async function loginByPassword(account: string, password: string) {
  const loginType: PortalLoginType = isValidMobile(account) ? 'MOBILE_PWD' : 'EMAIL_PWD'
  const accountField = loginType === 'MOBILE_PWD' ? 'mobile' : 'email'
  return login(
    loginType,
    {
      [accountField]: account,
      password
    },
    isValidEmail(account) ? '邮箱或密码错误' : '手机号或密码错误'
  )
}

export async function sendSmsCode(mobile: string) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/v1/auth/portal/send_code', {
      phone: mobile
    })

    if (data?.success === false) {
      throw new Error(data.message || '验证码发送失败')
    }

    return true
  } catch (error) {
    throw new Error(getErrorMessage(error, '验证码发送失败'))
  }
}

export async function loginBySms(mobile: string, smsCode: string) {
  return login(
    'mobile',
    {
      mobile,
      smsCode
    },
    '验证码登录失败'
  )
}

export async function checkLoginStatus(userId: string) {
  try {
    const { data } = await http.post<LoginStatusResponse>('/v1/credential/checkstatus', {
      User_id: userId
    })

    return Boolean(data?.login)
  } catch {
    return false
  }
}
