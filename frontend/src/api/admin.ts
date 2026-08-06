import http from './http'

interface ApiResponse<T> {
  success?: boolean
  message?: string
  data?: T
}

interface PagePayload<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
}

export interface AdminUserItem {
  id: number
  name: string
  phone: string
  email?: string | null
  status: 'NORMAL' | 'CANCELLED' | 'FROZEN' | string
  createdAt?: string
  lastLoginAt?: string
}

interface AdminUserListResponse {
  total: number
  items: AdminUserItem[]
}

export async function listAdminUsers(q = '') {
  const response = await http.get<AdminUserListResponse>('/v1/admin/users', {
    params: { q: q.trim() || undefined, page: 1, size: 1000 }
  })
  return Array.isArray(response.data.items) ? response.data.items : []
}

export async function deactivateAdminUser(userId: number) {
  await http.delete(`/v1/admin/users/${userId}`)
}

export interface CensorKeywordPayload {
  id: number
  keyword: string
  enabled: boolean
  createdAt: string
}

interface CensorKeywordListResponse {
  items?: CensorKeywordPayload[]
}

export async function listCensorKeywords() {
  const response = await http.get<CensorKeywordListResponse>('/v1/admin/keywords', {
    params: { page: 1, size: 1000 }
  })
  return Array.isArray(response.data.items) ? response.data.items : []
}

export async function setCensorKeywordEnabled(id: number, enabled: boolean) {
  await http.put(`/v1/admin/keywords/${id}/enabled`, { enabled })
}

export async function createCensorKeyword(keyword: string) {
  await http.post('/v1/admin/keywords', { keyword: keyword.trim() })
}

export async function deleteCensorKeyword(id: number) {
  await http.delete(`/v1/admin/keywords/${id}`)
}

export interface SecurityLogRecord {
  id: number
  userId: number
  username: string | null
  phone: string | null
  department: string | null
  modelName: string | null
  hitKeywords: string | null
  createdAt: string
}

export async function listSecurityLogs(q: string, page: number, size = 20) {
  const response = await http.get<ApiResponse<PagePayload<SecurityLogRecord>>>('/admin/security-logs', {
    params: { q: q.trim() || undefined, page, size }
  })
  if (response.data.success === false || !response.data.data) {
    throw new Error(response.data.message || '安全日志加载失败')
  }
  return response.data.data
}

export interface AlertContextPayload {
  time: string
  keyword: string
  before: string
  hit: string
  after: string
}

export interface AlertRecordPayload {
  id: number
  employeeId: string
  name: string
  department: string
  position: string
  triggeredAt: string
  ruleName: string
  periodDays: number
  threshold: number
  triggerCount: number
  currentCount: number
  keywords: string[]
  status: 'pending' | 'handled'
  noticeSentAt: string
  email: string
  handledAt?: string
  handledBy?: string
  contexts: AlertContextPayload[]
}

export interface AlertRulePayload {
  id: number
  name: string
  periodDays: number
  threshold: number
  enabled: boolean
  isDefault: boolean
  updatedAt: string
  createdBy: string
}

export async function listAlerts() {
  const response = await http.get<ApiResponse<PagePayload<AlertRecordPayload>>>('/v1/admin/alerts', {
    params: { page: 0, size: 1000 }
  })
  if (response.data.success === false || !response.data.data) {
    throw new Error(response.data.message || '告警记录加载失败')
  }
  return response.data.data.content ?? []
}

export async function markAlertHandled(id: number, handledBy = '管理员') {
  const response = await http.post<ApiResponse<AlertRecordPayload>>(`/v1/admin/alerts/${id}/handle`, {
    handledBy
  })
  if (response.data.success === false || !response.data.data) {
    throw new Error(response.data.message || '告警处理失败')
  }
  return response.data.data
}

export async function listAlertRules() {
  const response = await http.get<ApiResponse<AlertRulePayload[]>>('/v1/admin/alerts/rules')
  if (response.data.success === false || !Array.isArray(response.data.data)) {
    throw new Error(response.data.message || '告警规则加载失败')
  }
  return response.data.data
}

export async function createAlertRule(rule: Omit<AlertRulePayload, 'id' | 'isDefault' | 'updatedAt' | 'createdBy'>) {
  const response = await http.post<ApiResponse<AlertRulePayload>>('/v1/admin/alerts/rules', rule)
  if (response.data.success === false || !response.data.data) {
    throw new Error(response.data.message || '告警规则创建失败')
  }
  return response.data.data
}

export async function updateAlertRule(id: number, rule: Partial<AlertRulePayload>) {
  const response = await http.put<ApiResponse<AlertRulePayload>>(`/v1/admin/alerts/rules/${id}`, rule)
  if (response.data.success === false || !response.data.data) {
    throw new Error(response.data.message || '告警规则更新失败')
  }
  return response.data.data
}

export async function deleteAlertRule(id: number) {
  await http.delete(`/v1/admin/alerts/rules/${id}`)
}
