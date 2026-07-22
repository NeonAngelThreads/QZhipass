import http, {getErrorMessage} from './http'
import {type LoginInfo, saveLoginInfo} from './session'

type PortalLoginType = 'MOBILE_PWD' | 'EMAIL_PWD' | 'MOBILE_CODE'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T | null
}

interface LoginPayload {
  user_id: string | number
  access_token: string
  role?: string
  initialConversationId?: number
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

function normalizeLoginInfo(response: ApiResponse<LoginPayload>): LoginInfo {
  if (!response.success || !response.data) {
    throw new Error(response.message || '登录失败')
  }

  const userId = String(response.data.user_id || '').trim()
  const accessToken = response.data.access_token?.trim()
  if (!userId || !accessToken) {
    throw new Error('登录成功，但后端未返回完整认证信息')
  }

  return {
    userId,
    accessToken,
    role: response.data.role,
    initialConversationId: response.data.initialConversationId
  }
}

async function login(loginType: PortalLoginType, credential: Record<string, string>) {
  try {
    const {data} = await http.post<ApiResponse<LoginPayload>>('/v1/auth/portal/login', {
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
