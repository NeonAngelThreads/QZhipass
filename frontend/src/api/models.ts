import http from './http'
import { readLoginInfo } from './session'

interface ApiResponse<T> {
  success?: boolean
  message?: string
  data?: T | null
}

/**
 * 对应后端：
 * org.microsoft.qintelipass.dtos.response.ModelResponse
 */
export interface ModelResponse {
  modelKey: string
  displayName: string
  provider: string
}

/**
 * 获取当前用户可用的模型列表。
 *
 * 实际请求地址：
 * GET /api/v1/models/available
 */
export async function listAvailableModels(): Promise<ModelResponse[]> {
  const accessToken = readLoginInfo()?.accessToken

  const response = await http.get<ApiResponse<ModelResponse[]>>(
    '/v1/models/available',
    {
      headers: accessToken
        ? {
            Authorization: `Bearer ${accessToken}`
          }
        : undefined
    }
  )

  const body = response.data

  if (body.success === false) {
    throw new Error(body.message || '获取可用模型列表失败')
  }

  return Array.isArray(body.data) ? body.data : []
}

/**
 * 返回组件内部使用的模型唯一标识。
 */
export function modelKeyOf(model: ModelResponse): string {
  return model.modelKey
}

/**
 * 返回适合显示在界面上的模型名称。
 * 后端未提供 displayName 时回退到 modelKey。
 */
export function modelLabelOf(model: ModelResponse): string {
  const displayName = model.displayName?.trim()
  return displayName || model.modelKey
}
