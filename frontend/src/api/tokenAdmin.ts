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
  totalTokens: number   // 今日已用
  quota: number         // 生效上限（默认或个性化，后端已算好）
  isPersonalized: boolean
}
export interface ChartSeries { label: string; data: number[] }
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
  isPersonalized: boolean
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
  operatedAt: number // epoch 毫秒
}
export interface LogPage {
  list: LogItem[]
  total: number
  totalPages: number
  page: number
  size: number
}
export interface ApiRes<T> { success: boolean; message?: string; data?: T }

// ---------- 真后端请求封装 ----------
function authHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' }
  const t = localStorage.getItem(TOKEN_KEY)
  if (t) h['Authorization'] = `Bearer ${t}`
  return h
}
async function http<T = any>(method: string, path: string, body?: any): Promise<T> {
  const res = await fetch(BASE_URL + path, {
    method,
    headers: authHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })
  return (await res.json()) as T
}

// ============================================================
//  内置演示数据（mock）—— 数字与效果图完全对齐
// ============================================================
const mockState = {
  globalLimit: 120000,
  employees: [
    { id: '1', name: '张明', department: '研发部', totalTokens: 98500, quota: 120000, isPersonalized: false },
    { id: '2', name: '李华', department: '产品部', totalTokens: 115200, quota: 120000, isPersonalized: false },
    { id: '3', name: '王芳', department: '市场部', totalTokens: 121300, quota: 120000, isPersonalized: true },
    { id: '4', name: '刘洋', department: '运营部', totalTokens: 45600, quota: 120000, isPersonalized: false },
    { id: '5', name: '陈晨', department: '客服部', totalTokens: 8900, quota: 120000, isPersonalized: false },
  ] as EmployeeRow[],
  chart: {
    labels: ['06-20 周五', '06-21 周六', '06-22 周日', '06-23 周一', '06-24 周二', '06-25 周三', '06-26 周四'],
    datasets: [
      { label: '千问', data: [1650000, 2100000, 1850000, 2200000, 1800000, 2250000, 1650000] },
      { label: 'DeepSeek', data: [850000, 1200000, 1000000, 1300000, 1100000, 1350000, 1000000] },
      { label: 'Llama-3.1', data: [350000, 550000, 420000, 550000, 450000, 620000, 450000] },
    ] as ChartSeries[],
  },
  logs: [
    { id: 101, operatorName: 'Admin', targetUserId: '3', targetUserName: '王芳', oldQuota: 120000, newQuota: 150000, currentConsumption: 118000, operatedAt: Date.now() - 1000 * 60 * 60 * 26 },
    { id: 100, operatorName: 'Admin', targetUserId: '2', targetUserName: '李华', oldQuota: 100000, newQuota: 120000, currentConsumption: 92000, operatedAt: Date.now() - 1000 * 60 * 60 * 50 },
    { id: 99, operatorName: 'Admin', targetUserId: '1', targetUserName: '张明', oldQuota: 120000, newQuota: 100000, currentConsumption: 41000, operatedAt: Date.now() - 1000 * 60 * 60 * 73 },
  ] as LogItem[],
}
let mockLogSeq = 200

function mockDashboard(): Dashboard {
  return {
    activeUsers: 1248,
    overQuotaUsers: mockState.employees.filter((e) => e.totalTokens >= e.quota).length + 18, // 演示：含未列出员工
    todayTotal: 8760000,
    globalLimit: mockState.globalLimit,
    chart: mockState.chart,
    employees: mockState.employees.map((e) => ({ ...e })),
  }
}
function mockQuotaDetail(userId: string): QuotaDetail {
  const e = mockState.employees.find((x) => x.id === userId)
  return {
    userId,
    userName: e?.name ?? '',
    effectiveQuota: e?.quota ?? mockState.globalLimit,
    globalDefaultQuota: mockState.globalLimit,
    isPersonalized: e?.isPersonalized ?? false,
    todayConsumed: e?.totalTokens ?? 0,
    todayRemaining: Math.max((e?.quota ?? mockState.globalLimit) - (e?.totalTokens ?? 0), 0),
  }
}
function mockUpdate(userId: string, limit: number): UpdateResult {
  const e = mockState.employees.find((x) => x.id === userId)!
  const old = e.quota
  e.quota = limit
  e.isPersonalized = true
  const consumed = e.totalTokens
  mockState.logs.unshift({
    id: ++mockLogSeq, operatorName: 'Admin', targetUserId: userId, targetUserName: e.name,
    oldQuota: old, newQuota: limit, currentConsumption: consumed, operatedAt: Date.now(),
  })
  return { userId, userName: e.name, oldQuota: old, newQuota: limit, currentConsumption: consumed, remaining: Math.max(limit - consumed, 0), canChat: limit > consumed, isPersonalized: true }
}
function mockReset(userId: string): UpdateResult {
  const e = mockState.employees.find((x) => x.id === userId)!
  const old = e.quota
  e.quota = mockState.globalLimit
  e.isPersonalized = false
  const consumed = e.totalTokens
  mockState.logs.unshift({
    id: ++mockLogSeq, operatorName: 'Admin', targetUserId: userId, targetUserName: e.name,
    oldQuota: old, newQuota: mockState.globalLimit, currentConsumption: consumed, operatedAt: Date.now(),
  })
  return { userId, userName: e.name, oldQuota: old, newQuota: mockState.globalLimit, currentConsumption: consumed, remaining: Math.max(mockState.globalLimit - consumed, 0), canChat: mockState.globalLimit > consumed, isPersonalized: false }
}
function mockSetGlobal(limit: number) {
  mockState.globalLimit = limit
  const affected = mockState.employees.filter((e) => !e.isPersonalized).length
  return { dailyLimit: limit, affectedUsers: affected + 123 } // 演示：含未列出员工
}
function mockLogs(p: { operator?: string; target?: string; start?: number; end?: number; page: number; size: number }): LogPage {
  let arr = mockState.logs.slice()
  if (p.operator) arr = arr.filter((l) => l.operatorName.includes(p.operator!))
  if (p.target) arr = arr.filter((l) => l.targetUserName.includes(p.target!) || l.targetUserId === p.target)
  if (p.start) arr = arr.filter((l) => l.operatedAt >= p.start!)
  if (p.end) arr = arr.filter((l) => l.operatedAt <= p.end!)
  const total = arr.length
  const totalPages = Math.max(1, Math.ceil(total / p.size))
  const list = arr.slice(p.page * p.size, p.page * p.size + p.size)
  return { list, total, totalPages, page: p.page, size: p.size }
}

// ---------- 真后端：把后端返回映射成归一化结构 ----------
// 后端 dashboard 接口员工字段：id/name/department/totalTokens/quota/overQuota
// 注意：后端不返回 isPersonalized，需通过 quota !== globalLimit 推断。
const normDashboard = (j: any): Dashboard => {
  const r = j.rawData || j.data || {}
  const globalLimit = r.globalLimit ?? 0
  return {
    activeUsers: r.activeUsers ?? 0,
    overQuotaUsers: r.overQuotaUsers ?? 0,
    todayTotal: r.todayTotalConsumption ?? 0,
    globalLimit,
    chart: { labels: r.chartData?.labels ?? [], datasets: (r.chartData?.datasets ?? []).map((d: any) => ({ label: d.label, data: d.data })) },
    employees: (r.employees ?? []).map((e: any) => ({
      id: String(e.id),
      name: e.name,
      department: e.department,
      totalTokens: e.totalTokens,
      quota: e.quota,
      isPersonalized: e.quota !== globalLimit,
    })),
  }
}
// 从 dashboard 数据中提取单个用户的配额详情
const detailFromDashboard = (r: any, userId: string): QuotaDetail | null => {
  const globalLimit = r.globalLimit ?? 0
  const e = (r.employees ?? []).find((x: any) => String(x.id) === String(userId))
  if (!e) return null
  return {
    userId,
    userName: e.name,
    effectiveQuota: e.quota,
    globalDefaultQuota: globalLimit,
    isPersonalized: e.quota !== globalLimit,
    todayConsumed: e.totalTokens,
    todayRemaining: Math.max(e.quota - e.totalTokens, 0),
  }
}

// ============================================================
//  对外函数（页面调用这些；内部自动切换 mock / 真后端）
// ============================================================
const delay = (ms = 180) => new Promise((r) => setTimeout(r, ms))

export async function getDashboard(): Promise<ApiRes<Dashboard>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockDashboard() } }
  try { const j = await http('GET', '/api/v1/admin/token/dashboard'); return j.success ? { success: true, data: normDashboard(j) } : { success: false, message: j.message } }
  catch (e: any) { return { success: false, message: e.message } }
}
// 后端无 GET /quota/{userId} 接口，从 dashboard 数据中聚合出用户配额详情
export async function getQuotaDetail(userId: string): Promise<ApiRes<QuotaDetail>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockQuotaDetail(userId) } }
  try {
    const j = await http('GET', '/api/v1/admin/token/dashboard')
    if (!j.success) throw new Error(j.message || '获取员工列表失败')
    const r = j.rawData || j.data || {}
    const detail = detailFromDashboard(r, userId)
    if (!detail) throw new Error('员工不存在')
    return { success: true, data: detail }
  } catch (e: any) { return { success: false, message: e.message } }
}
// 后端 PUT /quota/{userId} 仅返回 {success, message}，UpdateResult 需自行聚合
export async function updateQuota(userId: string, dailyLimit: number): Promise<ApiRes<UpdateResult>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockUpdate(userId, dailyLimit) } }
  try {
    // 先取 dashboard 获取旧配额/已用量/姓名
    const dash = await http('GET', '/api/v1/admin/token/dashboard')
    if (!dash.success) throw new Error(dash.message || '获取员工列表失败')
    const r = dash.rawData || dash.data || {}
    const e = (r.employees ?? []).find((x: any) => String(x.id) === String(userId))
    const oldQuota = e?.quota ?? 0
    const consumed = e?.totalTokens ?? 0

    const j = await http('PUT', `/api/v1/admin/token/quota/${userId}`, { daily_token_limit: dailyLimit })
    if (!j.success) throw new Error(j.message || '更新配额失败')

    return {
      success: true,
      data: {
        userId,
        userName: e?.name ?? '',
        oldQuota,
        newQuota: dailyLimit,
        currentConsumption: consumed,
        remaining: Math.max(dailyLimit - consumed, 0),
        canChat: dailyLimit > consumed,
        isPersonalized: true,
      },
      message: j.message,
    }
  } catch (e: any) { return { success: false, message: e.message } }
}
// 后端无 DELETE /quota/{userId} 接口，"重置" 等同于把用户配额设回全局默认值
export async function resetQuota(userId: string): Promise<ApiRes<UpdateResult>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockReset(userId) } }
  try {
    const dash = await http('GET', '/api/v1/admin/token/dashboard')
    if (!dash.success) throw new Error(dash.message || '获取员工列表失败')
    const r = dash.rawData || dash.data || {}
    const globalLimit = r.globalLimit ?? 0
    const e = (r.employees ?? []).find((x: any) => String(x.id) === String(userId))
    const oldQuota = e?.quota ?? 0
    const consumed = e?.totalTokens ?? 0

    const j = await http('PUT', `/api/v1/admin/token/quota/${userId}`, { daily_token_limit: globalLimit })
    if (!j.success) throw new Error(j.message || '重置配额失败')

    return {
      success: true,
      data: {
        userId,
        userName: e?.name ?? '',
        oldQuota,
        newQuota: globalLimit,
        currentConsumption: consumed,
        remaining: Math.max(globalLimit - consumed, 0),
        canChat: globalLimit > consumed,
        isPersonalized: false,
      },
      message: j.message,
    }
  } catch (e: any) { return { success: false, message: e.message } }
}
export async function setGlobalQuota(dailyLimit: number): Promise<ApiRes<{ dailyLimit: number; affectedUsers: number }>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockSetGlobal(dailyLimit) } }
  try { const j = await http('PUT', '/api/v1/admin/token/quota', { daily_token_limit: dailyLimit }); const r = j.rawData || j.data || {}; return j.success ? { success: true, data: { dailyLimit: r.daily_limit, affectedUsers: r.affectedUsers }, message: j.message } : { success: false, message: j.message } }
  catch (e: any) { return { success: false, message: e.message } }
}
// 后端目前无配额操作日志接口，真后端模式下返回空分页
export async function getQuotaLogs(p: { operator?: string; target?: string; start?: number; end?: number; page: number; size: number }): Promise<ApiRes<LogPage>> {
  if (USE_MOCK) { await delay(); return { success: true, data: mockLogs(p) } }
  return { success: true, data: { list: [], total: 0, totalPages: 1, page: p.page, size: p.size } }
}