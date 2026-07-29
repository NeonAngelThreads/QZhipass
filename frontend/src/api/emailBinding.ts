import axios from 'axios'
import http, { getErrorMessage } from './http'

export const EMAIL_BINDING_MESSAGES = {
  statusFailed: '邮箱绑定状态查询失败，请稍后重试',
  sendFailed: '验证码发送失败，请稍后重试',
  bindFailed: '邮箱绑定失败，请稍后重试'
} as const

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface EmailBindingStatus {
  bound: boolean
  email: string | null
  cooldownSeconds: number
}

export interface EmailBindingSendCodeResult {
  expiresInSeconds: number
  cooldownSeconds: number
}

export interface EmailBindingVerifyResult {
  bound: boolean
  email: string
}

export interface EmailBindingApiResult<T> {
  message: string
  data: T
}

export class EmailBindingApiError extends Error {
  readonly cooldownSeconds: number

  constructor(message: string, cooldownSeconds = 0) {
    super(message)
    this.name = 'EmailBindingApiError'
    this.cooldownSeconds = cooldownSeconds
  }
}

export async function getEmailBindingStatus() {
  return request<EmailBindingStatus>(
    () => http.get<ApiResponse<EmailBindingStatus>>('/v1/account/email-binding'),
    EMAIL_BINDING_MESSAGES.statusFailed
  )
}

export async function sendEmailBindingCode(email: string) {
  return request<EmailBindingSendCodeResult>(
    () => http.post<ApiResponse<EmailBindingSendCodeResult>>(
      '/v1/account/email-binding/code',
      { email }
    ),
    EMAIL_BINDING_MESSAGES.sendFailed
  )
}

export async function verifyEmailBinding(email: string, code: string) {
  return request<EmailBindingVerifyResult>(
    () => http.post<ApiResponse<EmailBindingVerifyResult>>(
      '/v1/account/email-binding/verify',
      { email, code }
    ),
    EMAIL_BINDING_MESSAGES.bindFailed
  )
}

async function request<T>(
  operation: () => Promise<{ data: ApiResponse<T> }>,
  fallback: string
): Promise<EmailBindingApiResult<T>> {
  try {
    const response = await operation()
    if (!response.data?.success) {
      throw new EmailBindingApiError(
        response.data?.message || fallback,
        readCooldownSeconds(response.data?.data)
      )
    }
    return {
      message: response.data.message,
      data: response.data.data
    }
  } catch (error) {
    if (error instanceof EmailBindingApiError) {
      throw error
    }
    if (axios.isAxiosError(error)) {
      const responseData = error.response?.data
      if (responseData && typeof responseData === 'object') {
        const data = 'data' in responseData ? responseData.data : undefined
        throw new EmailBindingApiError(
          getErrorMessage(error, fallback),
          readCooldownSeconds(data)
        )
      }
    }
    throw new EmailBindingApiError(getErrorMessage(error, fallback))
  }
}

function readCooldownSeconds(value: unknown) {
  if (!value || typeof value !== 'object' || !('cooldownSeconds' in value)) {
    return 0
  }
  const seconds = value.cooldownSeconds
  return typeof seconds === 'number' && Number.isFinite(seconds) && seconds > 0
    ? Math.ceil(seconds)
    : 0
}
