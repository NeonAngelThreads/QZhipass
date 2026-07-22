import http, {getErrorMessage} from './http'
import {type LoginInfo, saveLoginInfo} from './session'

type PortalLoginType = 'MOBILE_PWD' | 'MOBILE_CODE'

interface PortalLoginResponse {
  success?: boolean
  message?: string
  data?: Record<string, unknown>
  user_id?: unknown
  userId?: unknown
  access_token?: unknown
  accessToken?: unknown
  token?: unknown
}

interface LoginStatusResponse {
  login?: boolean
}

const MOBILE_PATTERN = /^1[3-9]\d{9}$/

export function isValidMobile(mobile: string) {
  return MOBILE_PATTERN.test(mobile)
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

function normalizeLoginInfo(response: PortalLoginResponse, mobile: string): LoginInfo {
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
    readString(response.token) ||
    readString(response.message)
  const initialConversationId =
    readNumber(payload.initialConversationId) ||
    readNumber(payload.initial_conversation_id) ||
    readNumber(conversationPayload.id)

  if (!userId) {
    throw new Error('登录成功但后端未返回 user_id')
  }

  if (!accessToken) {
    throw new Error('登录成功但后端未返回 access_token')
  }

  return {
    userId,
    accessToken,
    initialConversationId
  }
}

async function login(
  loginType: PortalLoginType,
  credential: Record<string, string>,
  mobile: string,
  fallback: string
) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/v1/portal/login', {
      loginType,
      credential
    })
    const loginInfo = normalizeLoginInfo(data, mobile)

    saveLoginInfo(loginInfo)
    return loginInfo
  } catch (error) {
    throw new Error(getErrorMessage(error, fallback))
  }
}

export async function loginByPassword(mobile: string, password: string) {
  return login(
    'MOBILE_PWD',
    {
      mobile,
      password
    },
    mobile,
    '手机号或密码登录失败'
  )
}

export async function sendSmsCode(mobile: string) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/v1/portal/send_code', {
      Phone: mobile
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
    'MOBILE_CODE',
    {
      mobile,
      smsCode
    },
    mobile,
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
