// ============================================================
//  Token 配额管理 · 数据层（管理员端）
//  —— 默认 USE_MOCK = true：用内置演示数据，复制即跑、零配置。
//
//  【接后端，只改下面 3 步，页面代码不用动】
//   1) 把 USE_MOCK 改成 false
//   2) 把 BASE_URL 改成后端地址，例如 'http://localhost:8080'
//      （若 vite 已配代理，保持 '' 空字符串即可，最稳）
//   3) 确认登录后 token 存在 localStorage 的哪个 key：
//      下面默认读 'token'，不对就改这个字符串。
//      鉴权统一走 Authorization: Bearer <token>（后端 SecurityUtil 解析）。
// ============================================================

const USE_MOCK = true
const BASE_URL = '' // 真后端地址；用 vite 代理时留空
const TOKEN_KEY = 'token'

// ---------- 归一化后的类型（页面只认这些，不关心后端 rawData/data 差异） ----------
export interface EmployeeRow {
  id: string
  name: string
  department: string
  totalTokens: number
  quota: number
  isPersonalized?: boolean
}

export interface ChartSeries {
  label: string
  data: number[]
}

export interface Dashboard {
  activeUsers: number
  overQuotaUsers: number
  todayTotal: number
  globalLimit: number
  chart: { labels: string[]; datasets: ChartSeries[] }
  employees: EmployeeRow[]
}

export interface QuotaDetail {
  userId: string
  userName: string
  effectiveQuota: number
  globalDefaultQuota: number
  isPersonalized?: boolean
  todayConsumed: number
  todayRemaining: number
}

export interface UpdateResult {
  userId: string
  userName: string
  oldQuota: number
  newQuota: number
  currentConsumption: number
  remaining: number
  canChat: boolean
  isPersonalized: boolean
}

export interface LogItem {
  id: number
  operatorName: string
  targetUserId: string
  targetUserName: string
  oldQuota: number
  newQuota: number
  currentConsumption: number
  operatedAt: number
}

export interface LogPage {
  list: LogItem[]
  total: number
  totalPages: number
  page: number
  size: number
}

export interface ApiRes<T> {
  success: boolean
  message?: string
  data?: T
}

interface BackendResponse {
  success?: boolean
  message?: string
  data?: unknown
  rawData?: unknown
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function asNumber(value: unknown) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function normalizeDashboard(response: BackendResponse): Dashboard {
  const raw = asRecord(response.rawData ?? response.data)
  const chart = asRecord(raw.chartData)
  const datasets = Array.isArray(chart.datasets) ? chart.datasets : []
  const employees = Array.isArray(raw.employees) ? raw.employees : []

  return {
    activeUsers: asNumber(raw.activeUsers),
    overQuotaUsers: asNumber(raw.overQuotaUsers),
    todayTotal: asNumber(raw.todayTotalConsumption),
    globalLimit: asNumber(raw.globalLimit),
    chart: {
      labels: Array.isArray(chart.labels) ? chart.labels.map(String) : [],
      datasets: datasets.map(item => {
        const dataset = asRecord(item)
        return {
          label: String(dataset.label ?? ''),
          data: Array.isArray(dataset.data) ? dataset.data.map(asNumber) : []
        }
      })
    },
    employees: employees.map(item => {
      const employee = asRecord(item)
      return {
        id: String(employee.id ?? ''),
        name: String(employee.name ?? ''),
        department: String(employee.department ?? ''),
        totalTokens: asNumber(employee.totalTokens),
        quota: asNumber(employee.quota),
        isPersonalized: typeof employee.isPersonalized === 'boolean'
          ? employee.isPersonalized
          : undefined
      }
    }).filter(employee => employee.id)
  }
}

export async function getDashboard(): Promise<ApiRes<Dashboard>> {
  try {
    const response = await http.get<BackendResponse>('/v1/admin/token/dashboard')
    if (response.data.success === false) {
      return { success: false, message: response.data.message || 'Token 看板加载失败' }
    }
    return { success: true, data: normalizeDashboard(response.data) }
  } catch (error) {
    return { success: false, message: getErrorMessage(error, 'Token 看板加载失败') }
  }
}

export async function getQuotaDetail(userId: string): Promise<ApiRes<QuotaDetail>> {
  const dashboard = await getDashboard()
  const employee = dashboard.data?.employees.find(item => item.id === userId)
  if (!dashboard.success || !dashboard.data) {
    return { success: false, message: dashboard.message }
  }
  if (!employee) {
    return { success: false, message: '后端返回的 Token 看板中没有该用户。' }
  }
  return {
    success: true,
    data: {
      userId,
      effectiveQuota: employee.quota,
      globalDefaultQuota: dashboard.data.globalLimit,
      isPersonalized: employee.isPersonalized,
      todayConsumed: employee.totalTokens,
      todayRemaining: Math.max(employee.quota - employee.totalTokens, 0)
    }
  }
}

export async function updateQuota(userId: string, dailyLimit: number): Promise<ApiRes<UpdateResult>> {
  const before = await getDashboard()
  const previous = before.data?.employees.find(item => item.id === userId)
  try {
    const response = await http.put<BackendResponse>(`/v1/admin/token/quota/${userId}`, {
      daily_token_limit: dailyLimit
    })
    if (response.data.success === false) {
      return { success: false, message: response.data.message || '用户 Token 配额更新失败' }
    }
    const after = await getDashboard()
    const current = after.data?.employees.find(item => item.id === userId)
    const currentConsumption = current?.totalTokens ?? previous?.totalTokens ?? 0
    const newQuota = current?.quota ?? dailyLimit
    return {
      success: true,
      message: response.data.message,
      data: {
        userId,
        userName: current?.name ?? previous?.name ?? '',
        oldQuota: previous?.quota ?? newQuota,
        newQuota,
        currentConsumption,
        remaining: Math.max(newQuota - currentConsumption, 0),
        canChat: currentConsumption < newQuota,
        isPersonalized: true
      }
    }
  } catch (error) {
    return { success: false, message: getErrorMessage(error, '用户 Token 配额更新失败') }
  }
}

export async function resetQuota(userId: string): Promise<ApiRes<UpdateResult>> {
  void userId
  return {
    success: false,
    message: '后端尚未提供重置单个用户 Token 配额接口，当前无法完成真实重置。'
  }
}

export async function setGlobalQuota(dailyLimit: number): Promise<ApiRes<{ dailyLimit: number; affectedUsers: number }>> {
  try {
    const response = await http.put<BackendResponse>('/v1/admin/token/quota', {
      daily_token_limit: dailyLimit
    })
    if (response.data.success === false) {
      return { success: false, message: response.data.message || '全员 Token 配额更新失败' }
    }
    const raw = asRecord(response.data.rawData ?? response.data.data)
    return {
      success: true,
      message: response.data.message,
      data: {
        dailyLimit: asNumber(raw.daily_limit) || dailyLimit,
        affectedUsers: asNumber(raw.affectedUsers)
      }
    }
  } catch (error) {
    return { success: false, message: getErrorMessage(error, '全员 Token 配额更新失败') }
  }
}

export async function getQuotaLogs(params: {
  operator?: string
  target?: string
  start?: number
  end?: number
  page: number
  size: number
}): Promise<ApiRes<LogPage>> {
  void params
  return {
    success: false,
    message: '后端尚未提供 Token 配额操作日志接口，当前无法加载真实日志。'
  }
}
