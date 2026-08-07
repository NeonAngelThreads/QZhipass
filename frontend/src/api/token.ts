import http, { getErrorMessage } from './http'
import { readLoginInfo } from './session'

interface TokenUsageResponse {
  success?: boolean
  message?: string
  rawData?: {
    daily_limit?: unknown
    used_today?: unknown
  }
}

export interface CurrentTokenUsage {
  dailyLimit: number
  usedToday: number
}

function readNumber(value: unknown) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

export async function getCurrentTokenUsage(): Promise<CurrentTokenUsage> {
  const userId = readLoginInfo()?.userId
  if (!userId) {
    throw new Error('当前登录状态缺少用户 ID')
  }

  try {
    const response = await http.get<TokenUsageResponse>('/v1/user/token/usage', {
      headers: { 'X-User-Id': userId }
    })
    if (response.data.success === false || !response.data.rawData) {
      throw new Error(response.data.message || 'Token 使用情况加载失败')
    }
    return {
      dailyLimit: readNumber(response.data.rawData.daily_limit),
      usedToday: readNumber(response.data.rawData.used_today)
    }
  } catch (error) {
    throw new Error(getErrorMessage(error, 'Token 使用情况加载失败'))
  }
}
