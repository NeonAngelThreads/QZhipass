import axios from 'axios'

/**
 * 热键配置 API（对齐 Hotkey.md）
 *
 * GET  /api/v1/configs/registry/hotkeys
 * GET  /api/v1/configs/registry/functions
 * GET  /api/v1/user/config/hotkey?funcId=
 * POST /api/v1/user/config/hotkey?funcId=&keyId=
 * PUT  /api/v1/user/config/hotkey?funcId=&keyId=
 * DELETE /api/v1/user/config/hotkey?funcId=
 */

export interface RegistryResponse {
  success?: boolean
  message?: string
  mapping?: Record<string, string>
  data?: null
}

export interface HotkeyBinding {
  userId?: number | string
  funcId: number
  keyId: number
  createAt?: string
}

export interface ApiErrorBody {
  success?: boolean
  message?: string
  data?: null
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token =
    localStorage.getItem('access_token') ||
    localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function unwrapError(err: unknown): string {
  const ax = err as { response?: { data?: ApiErrorBody; status?: number }; message?: string }
  return (
    ax?.response?.data?.message ||
    ax?.message ||
    '请求失败'
  )
}

/** 1. 热键 id 注册表：{ "1": "#", "20": "ESC", ... } */
export async function fetchHotkeyRegistry(): Promise<Record<string, string>> {
  const { data } = await http.get<RegistryResponse>('/v1/configs/registry/hotkeys')
  if (data?.success === false) {
    throw new Error(data.message || '获取热键注册表失败')
  }
  return data?.mapping || {}
}

/** 2. 功能 id 注册表：{ "1": "model.list", ... } */
export async function fetchFunctionRegistry(): Promise<Record<string, string>> {
  const { data } = await http.get<RegistryResponse>('/v1/configs/registry/functions')
  if (data?.success === false) {
    throw new Error(data.message || '获取功能注册表失败')
  }
  return data?.mapping || {}
}

/** 6. 查询某功能的绑定 */
export async function getHotkeyBinding(funcId: number): Promise<HotkeyBinding | null> {
  try {
    const { data, status } = await http.get<HotkeyBinding>('/v1/user/config/hotkey', {
      params: { funcId }
    })
    if (status === 404) return null
    if ((data as unknown as ApiErrorBody)?.success === false) return null
    if (data?.funcId == null) return null
    return data
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number } })?.response?.status
    if (status === 404) return null
    throw new Error(unwrapError(err))
  }
}

/** 3. 新建绑定 */
export async function createHotkeyBinding(funcId: number, keyId: number): Promise<HotkeyBinding> {
  try {
    const { data } = await http.post<HotkeyBinding>('/v1/user/config/hotkey', null, {
      params: { funcId, keyId }
    })
    return data
  } catch (err: unknown) {
    throw new Error(unwrapError(err))
  }
}

/** 4. 更新绑定 */
export async function updateHotkeyBinding(funcId: number, keyId: number): Promise<HotkeyBinding> {
  try {
    const { data } = await http.put<HotkeyBinding>('/v1/user/config/hotkey', null, {
      params: { funcId, keyId }
    })
    return data
  } catch (err: unknown) {
    throw new Error(unwrapError(err))
  }
}

/**
 * 绑定或更新：已有绑定则 PUT，否则 POST。
 * 若 POST 返回 409 "Hotkey config already exists" 则自动改走 PUT。
 */
export async function bindOrUpdateHotkey(funcId: number, keyId: number): Promise<HotkeyBinding> {
  const existing = await getHotkeyBinding(funcId)
  if (existing) {
    return updateHotkeyBinding(funcId, keyId)
  }
  try {
    return await createHotkeyBinding(funcId, keyId)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    if (/already exists/i.test(msg)) {
      return updateHotkeyBinding(funcId, keyId)
    }
    throw err
  }
}

/** 5. 重置（删除）某功能绑定 */
export async function resetHotkeyBinding(funcId: number): Promise<void> {
  try {
    await http.delete('/v1/user/config/hotkey', { params: { funcId } })
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number } })?.response?.status
    if (status === 404) return
    throw new Error(unwrapError(err))
  }
}

/** 功能 code → 展示名 */
export const FUNCTION_LABELS: Record<string, string> = {
  'model.list': '大模型列表',
  'agent.create': '创建 Agent',
  'agent.call': '调用 Agent',
  'agent.list.close': '关闭 Agent 调用列表'
}

export const FUNCTION_DESCRIPTIONS: Record<string, string> = {
  'model.list': '打开/切换大模型选择列表',
  'agent.create': '快速创建新的 Agent',
  'agent.call': '唤起 Agent 调用',
  'agent.list.close': '关闭 Agent 调用列表弹层'
}

/** 注册表默认热键展示（后端未绑定时的兜底） */
export const DEFAULT_FUNC_KEY: Record<string, string> = {
  'model.list': '#',
  'agent.create': '~',
  'agent.call': '!',
  'agent.list.close': 'ESC'
}

/** 规范化按键字符串，便于 keydown 匹配 */
export function normalizeKeyLabel(raw: string): string {
  if (!raw) return ''
  const t = raw.trim()
  if (/^esc$/i.test(t)) return 'Escape'
  return t
    .replace(/Ctrl/gi, 'Control')
    .replace(/Cmd/gi, 'Meta')
    .replace(/\s*\+\s*/g, '+')
}

export function displayKeyLabel(raw: string): string {
  if (!raw) return '未绑定'
  if (/^esc(ape)?$/i.test(raw)) return 'Esc'
  return raw.replace(/Control/gi, 'Ctrl').replace(/Meta/gi, 'Cmd').replace(/\+/g, ' + ')
}
