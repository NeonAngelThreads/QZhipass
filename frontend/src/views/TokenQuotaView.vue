<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute} from 'vue-router'
import {
  Bell,
  Clock,
  DataBoard,
  DataLine,
  Document,
  HomeFilled,
  Lock,
  Refresh,
  Search,
  Setting,
  Warning,
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  type Dashboard,
  type EmployeeRow,
  getDashboard,
  getQuotaDetail,
  getQuotaLogs,
  type LogItem,
  type QuotaDetail,
  resetQuota,
  setGlobalQuota,
  updateQuota,
} from '../api/tokenAdmin'

// ==================== 通用工具 ====================
const fmtNum = (n: number) => (n ?? 0).toLocaleString('en-US')
const fmtCompact = (n: number) => {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(2).replace(/\.?0+$/, '') + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1).replace(/\.0$/, '') + 'K'
  return String(n)
}
const pad = (n: number) => String(n).padStart(2, '0')
const fmtTime = (ms: number) => {
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
const rateOf = (e: EmployeeRow) => (e.quota > 0 ? Math.round((e.totalTokens / e.quota) * 100) : 0)
const statusOf = (rate: number) => (rate >= 100 ? 'over' : rate >= 85 ? 'warning' : 'normal')
const STATUS_MAP: Record<string, { text: string; type: 'success' | 'warning' | 'danger' }> = {
  normal: { text: '正常', type: 'success' },
  warning: { text: '接近上限', type: 'warning' },
  over: { text: '已超额', type: 'danger' },
}

// ==================== 侧栏（真实跳转） ====================
const route = useRoute()
const sidebarCollapsed = ref(false)
const isActive = (to: string) => route.path === to
interface MenuItem { key: string; label: string; icon: any; to?: string; badge?: number; action?: boolean }
const menu: MenuItem[] = [
  { key: 'home', label: '首页', icon: HomeFilled, to: '/' },
  { key: 'so', label: '敏感词概览', icon: DataBoard, to: '/admin/sensitive-words' },
  { key: 'sm', label: '敏感词管理', icon: Lock, to: '/admin/sensitive-words' },
  { key: 'token', label: '个人 Token 统计', icon: DataLine, to: '/token' },
  { key: 'log', label: '触发日志', icon: Document, to: '/admin/security-logs' },
  { key: 'alert', label: '告警', icon: Bell, to: '/admin/alerts' },
  { key: 'set', label: '系统设置', icon: Setting, action: true }, // 暂无对应页面
]
function onSettings() { ElMessage.info('系统设置模块开发中，敬请期待') }

// ==================== 看板数据 ====================
const loading = ref(false)
const lastUpdate = ref('...')
const overview = reactive({ activeUsers: 0, overQuotaUsers: 0, todayTotal: 0, globalLimit: 0 })
const chart = reactive<{ labels: string[]; datasets: { label: string; data: number[] }[] }>({ labels: [], datasets: [] })
const employees = ref<EmployeeRow[]>([])

async function loadDashboard() {
  loading.value = true
  try {
    const res = await getDashboard()
    if (!res.success || !res.data) throw new Error(res.message || '加载失败')
    const d: Dashboard = res.data
    overview.activeUsers = d.activeUsers
    overview.overQuotaUsers = d.overQuotaUsers
    overview.todayTotal = d.todayTotal
    overview.globalLimit = d.globalLimit
    chart.labels = d.chart.labels
    chart.datasets = d.chart.datasets
    employees.value = d.employees
    globalInput.value = d.globalLimit
    lastUpdate.value = new Date().toLocaleString('zh-CN')
  } catch (e: any) {
    ElMessage.error(e.message || '加载看板数据失败')
  } finally {
    loading.value = false
  }
}

// ==================== 表格筛选 ====================
const deptFilter = ref('')
const keyword = ref('')
const departments = computed(() => Array.from(new Set(employees.value.map((e) => e.department))))
const filteredEmployees = computed(() =>
  employees.value.filter(
    (e) =>
      (!deptFilter.value || e.department === deptFilter.value) &&
      (!keyword.value || e.name.includes(keyword.value) || String(e.id).includes(keyword.value))
  )
)

function patchRow(userId: string, patch: Partial<EmployeeRow>) {
  const i = employees.value.findIndex((e) => e.id === userId)
  if (i >= 0) employees.value[i] = { ...employees.value[i], ...patch }
}
function recomputeOverQuota() {
  overview.overQuotaUsers = employees.value.filter((e) => e.totalTokens >= e.quota).length
}

// ==================== 调整上限弹窗 ====================
const adjustDialogVisible = ref(false)
const adjustForm = reactive({ userId: '', name: '', input: 0, detail: null as QuotaDetail | null, saving: false })

async function openAdjust(e: EmployeeRow) {
  adjustDialogVisible.value = true
  adjustForm.userId = e.id
  adjustForm.name = e.name
  adjustForm.input = e.quota
  adjustForm.detail = null
  adjustForm.saving = false
  const res = await getQuotaDetail(e.id)
  if (res.success && res.data) {
    adjustForm.detail = res.data
    adjustForm.input = res.data.effectiveQuota
  }
}

async function saveAdjust() {
  if (!adjustForm.input || adjustForm.input < 1000) { ElMessage.warning('上限值必须 ≥ 1000'); return }
  adjustForm.saving = true
  try {
    const res = await updateQuota(adjustForm.userId, adjustForm.input)
    if (!res.success || !res.data) throw new Error(res.message || '保存失败')
    const r = res.data
    patchRow(adjustForm.userId, { quota: r.newQuota, isPersonalized: true })
    recomputeOverQuota()
    syncCardIfSame(adjustForm.userId, r.newQuota)
    ElMessage.success(r.canChat ? `已保存，${r.userName} 已恢复对话能力` : `已保存，但当前仍超限额`)
    adjustDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    adjustForm.saving = false
  }
}

async function doReset() {
  if (!adjustForm.detail) return
  try {
    await ElMessageBox.confirm(`确定将「${adjustForm.name}」重置为系统默认配额吗？`, '确认重置', { type: 'warning' })
  } catch { return }
  adjustForm.saving = true
  try {
    const res = await resetQuota(adjustForm.userId)
    if (!res.success || !res.data) throw new Error(res.message || '重置失败')
    const r = res.data
    patchRow(adjustForm.userId, { quota: r.newQuota, isPersonalized: false })
    recomputeOverQuota()
    syncCardIfSame(adjustForm.userId, r.newQuota)
    ElMessage.success(`已重置为系统默认 ${fmtNum(r.newQuota)}`)
    adjustDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '重置失败')
  } finally {
    adjustForm.saving = false
  }
}

// ==================== 右侧个人配额卡片 ====================
const cardUserId = ref('')
const cardDetail = ref<QuotaDetail | null>(null)
const cardInput = ref<number | null>(null)

async function onCardUserChange() {
  cardDetail.value = null
  cardInput.value = null
  if (!cardUserId.value) return
  const res = await getQuotaDetail(cardUserId.value)
  if (res.success && res.data) {
    cardDetail.value = res.data
    cardInput.value = res.data.effectiveQuota
  }
}

async function saveCard() {
  if (!cardUserId.value) { ElMessage.warning('请先选择员工'); return }
  const v = Number(cardInput.value)
  if (!v || v < 1000) { ElMessage.warning('上限值必须 ≥ 1000'); return }
  const res = await updateQuota(cardUserId.value, v)
  if (!res.success || !res.data) { ElMessage.error(res.message || '保存失败'); return }
  const r = res.data
  patchRow(cardUserId.value, { quota: r.newQuota, isPersonalized: true })
  recomputeOverQuota()
  if (cardDetail.value) cardDetail.value.effectiveQuota = r.newQuota
  ElMessage.success(r.canChat ? `已保存，${r.userName} 已恢复对话能力` : `已保存，${r.userName} 当前仍超限额`)
}

function syncCardIfSame(userId: string, quota: number) {
  if (cardUserId.value === userId && cardDetail.value) cardDetail.value.effectiveQuota = quota
}

// ==================== 全员默认配额 ====================
const globalInput = ref<number>(120000)
async function saveGlobal() {
  const v = Number(globalInput.value)
  if (!v || v < 1000) { ElMessage.warning('默认上限必须 ≥ 1000'); return }
  const res = await setGlobalQuota(v)
  if (!res.success || !res.data) { ElMessage.error(res.message || '保存失败'); return }
  overview.globalLimit = res.data.dailyLimit
  employees.value = employees.value.map((e) => (e.isPersonalized ? e : { ...e, quota: res.data!.dailyLimit }))
  if (cardDetail.value && !cardDetail.value.isPersonalized) cardDetail.value.effectiveQuota = res.data.dailyLimit
  recomputeOverQuota()
  ElMessage.success(`全员默认上限已更新为 ${fmtNum(res.data.dailyLimit)}，影响 ${res.data.affectedUsers} 人`)
}

// ==================== 操作日志抽屉 ====================
const logDrawerVisible = ref(false)
const logFilter = reactive({ operator: '', target: '', start: '', end: '' })
const logPage = ref(0)
const logSize = 8
const logData = ref<{ list: LogItem[]; total: number; totalPages: number }>({ list: [], total: 0, totalPages: 1 })
const logLoading = ref(false)

async function loadLogs(resetPage = false) {
  if (resetPage) logPage.value = 0
  logLoading.value = true
  try {
    const res = await getQuotaLogs({
      operator: logFilter.operator.trim() || undefined,
      target: logFilter.target.trim() || undefined,
      start: logFilter.start ? new Date(logFilter.start).getTime() : undefined,
      end: logFilter.end ? new Date(logFilter.end).getTime() + 86400000 - 1 : undefined,
      page: logPage.value,
      size: logSize,
    })
    if (!res.success || !res.data) throw new Error(res.message || '日志加载失败')
    logData.value = { list: res.data.list, total: res.data.total, totalPages: res.data.totalPages }
  } catch (e: any) {
    ElMessage.error(e.message || '日志加载失败')
  } finally {
    logLoading.value = false
  }
}

function openLogs() { logDrawerVisible.value = true; loadLogs(true) }

// ==================== 手写 SVG 折线图 ====================
const SERIES_COLOR: Record<string, string> = { '千问': '#3b82f6', 'DeepSeek': '#22c55e', 'Llama-3.1': '#f59e0b' }
const W = 920, H = 240, PL = 46, PR = 18, PT = 14, PB = 30
const chartMax = computed(() => {
  let m = 0
  chart.datasets.forEach((s) => s.data.forEach((v) => (m = Math.max(m, v))))
  return Math.max(Math.ceil((m * 1.1) / 500000) * 500000, 500000)
})
const yTicks = computed(() => {
  const step = 500000, arr: number[] = []
  for (let v = 0; v <= chartMax.value; v += step) arr.push(v)
  return arr
})
const xOf = (i: number) => PL + (i * (W - PL - PR)) / Math.max(chart.labels.length - 1, 1)
const yOf = (v: number) => PT + (1 - v / chartMax.value) * (H - PT - PB)
const linePoints = (data: number[]) => data.map((v, i) => `${xOf(i)},${yOf(v)}`).join(' ')
const hover = ref<{ x: number; y: number; label: string; series: string; value: number } | null>(null)

onMounted(loadDashboard)
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-gray-100">
    <!-- ========== Sidebar（真实跳转，当前页自动高亮） ========== -->
    <aside
      class="flex shrink-0 flex-col border-r border-gray-200 bg-gray-900 text-gray-300 transition-all duration-300"
      :class="sidebarCollapsed ? 'w-16' : 'w-56'"
    >
      <div class="flex h-14 items-center gap-3 border-b border-gray-800 px-4">
        <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white font-bold text-sm">企</div>
        <span v-show="!sidebarCollapsed" class="text-sm font-semibold text-white">管理后台</span>
      </div>

      <nav class="flex-1 overflow-y-auto px-2 py-3">
        <template v-for="m in menu" :key="m.key">
          <!-- 有路由的：真实跳转 -->
          <router-link
            v-if="m.to"
            :to="m.to"
            class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition no-underline"
            :class="isActive(m.to) ? 'text-blue-400 bg-blue-900/40' : 'hover:bg-gray-800 hover:text-white'"
          >
            <el-icon :size="16"><component :is="m.icon" /></el-icon>
            <span v-show="!sidebarCollapsed" class="truncate" :class="isActive(m.to) ? 'font-medium' : ''">{{ m.label }}</span>
            <span
              v-if="m.badge"
              v-show="!sidebarCollapsed"
              class="ml-auto flex h-5 min-w-[20px] items-center justify-center rounded-full bg-red-500 px-1.5 text-xs font-semibold text-white"
            >{{ m.badge }}</span>
          </router-link>
          <!-- 无路由的（系统设置）：提示 -->
          <button
            v-else
            class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 hover:text-white"
            @click="onSettings"
          >
            <el-icon :size="16"><component :is="m.icon" /></el-icon>
            <span v-show="!sidebarCollapsed" class="truncate">{{ m.label }}</span>
          </button>
        </template>
      </nav>

      <button
        class="flex h-10 w-full items-center justify-center border-t border-gray-800 text-gray-500 transition hover:bg-gray-800 hover:text-gray-300"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <svg class="h-4 w-4 transition-transform" :class="sidebarCollapsed ? 'rotate-180' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
        </svg>
      </button>
    </aside>

    <!-- ========== Main Area ========== -->
    <div class="flex flex-1 flex-col min-w-0 overflow-hidden">
      <header class="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
        <div class="flex items-center gap-3">
          <h1 class="text-base font-semibold text-gray-800">Token 使用统计看板</h1>
          <span class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">管理后台</span>
        </div>
        <div class="flex items-center gap-3">
          <button class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100" title="通知">
            <el-icon :size="16"><Bell /></el-icon>
          </button>
          <div class="flex items-center gap-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">管</div>
            <span class="hidden text-sm text-gray-600 sm:inline">管理员</span>
            <svg class="h-3 w-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" /></svg>
          </div>
        </div>
      </header>

      <div class="flex-1 overflow-y-auto p-5 sm:p-6">
        <div class="mb-5 flex items-start justify-between">
          <div><p class="text-xs text-gray-500">实时监控全员 Token 消耗 · 配额管理 · 立即生效</p></div>
          <div class="flex items-center gap-2">
            <el-button :icon="Document" @click="openLogs">操作日志</el-button>
            <el-button :icon="Refresh" @click="loadDashboard">刷新数据</el-button>
            <span class="flex items-center gap-1 text-xs text-gray-400"><el-icon :size="12"><Clock /></el-icon> {{ lastUpdate }}</span>
          </div>
        </div>

        <!-- 三卡片 -->
        <div class="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-green-100 text-green-600"><el-icon :size="20"><DataBoard /></el-icon></div>
            <div class="min-w-0"><p class="text-xl font-bold text-gray-800">{{ fmtNum(overview.activeUsers) }}</p><p class="truncate text-xs text-gray-500">活跃用户数</p></div>
          </div>
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-red-100 text-red-600"><el-icon :size="20"><Warning /></el-icon></div>
            <div class="min-w-0"><p class="text-xl font-bold text-gray-800">{{ overview.overQuotaUsers }}</p><p class="truncate text-xs text-gray-500">超额用户数</p></div>
          </div>
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-100 text-blue-600"><el-icon :size="20"><DataLine /></el-icon></div>
            <div class="min-w-0"><p class="text-xl font-bold text-gray-800">{{ fmtCompact(overview.todayTotal) }}</p><p class="truncate text-xs text-gray-500">今日总消耗</p></div>
          </div>
        </div>

        <!-- 折线图 -->
        <div class="mb-5 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div class="mb-3 flex items-start justify-between">
            <div>
              <h3 class="text-sm font-semibold text-gray-800">📈 近7日各模型 Token 消耗趋势</h3>
              <p class="text-xs text-gray-500">按天统计 · 不同颜色区分大模型</p>
            </div>
            <div class="flex gap-3">
              <span v-for="s in chart.datasets" :key="s.label" class="flex items-center gap-1.5 rounded-full bg-gray-50 px-2.5 py-1 text-xs text-gray-600">
                <i class="inline-block h-2 w-2 rounded-full" :style="{ background: SERIES_COLOR[s.label] || '#94a3b8' }" />{{ s.label }}
              </span>
            </div>
          </div>
          <svg :viewBox="`0 0 ${W} ${H}`" class="w-full" style="height: 240px;" preserveAspectRatio="none">
            <line v-for="t in yTicks" :key="'g'+t" :x1="PL" :x2="W-PR" :y1="yOf(t)" :y2="yOf(t)" stroke="#eef2f8" stroke-width="1" />
            <text v-for="t in yTicks" :key="'yt'+t" :x="PL-8" :y="yOf(t)+4" fill="#94a3b8" font-size="11" text-anchor="end">{{ fmtCompact(t) }}</text>
            <text v-for="(lb,i) in chart.labels" :key="'x'+i" :x="xOf(i)" :y="H-8" fill="#94a3b8" font-size="11" text-anchor="middle">{{ lb }}</text>
            <polyline v-for="s in chart.datasets" :key="'l'+s.label" :points="linePoints(s.data)" fill="none" :stroke="SERIES_COLOR[s.label]||'#94a3b8'" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round" />
            <template v-for="s in chart.datasets" :key="'pts'+s.label">
              <circle v-for="(v,i) in s.data" :key="i" :cx="xOf(i)" :cy="yOf(v)" r="4" fill="#fff" :stroke="SERIES_COLOR[s.label]||'#94a3b8'" stroke-width="2.4" class="cursor-pointer"
                @mouseenter="hover={x:xOf(i),y:yOf(v),label:chart.labels[i],series:s.label,value:v}" @mouseleave="hover=null" />
            </template>
            <g v-if="hover" :transform="`translate(${Math.min(hover.x+8,W-132)},${Math.max(hover.y-44,2)})`">
              <rect width="124" height="38" rx="6" fill="#0b1220" opacity=".92" />
              <text x="10" y="16" fill="#cbd5e1" font-size="10">{{ hover.series }} · {{ hover.label }}</text>
              <text x="10" y="31" fill="#fff" font-size="12" font-weight="700">{{ fmtNum(hover.value) }} tokens</text>
            </g>
          </svg>
        </div>

        <!-- 下排 -->
        <div class="grid grid-cols-1 gap-5 lg:grid-cols-5">
          <div class="lg:col-span-3 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-4 py-3">
              <h3 class="text-sm font-semibold text-gray-800">员工 Token 使用明细（今日）</h3>
              <div class="flex items-center gap-2">
                <el-select v-model="deptFilter" placeholder="全部部门" clearable size="small" style="width:120px">
                  <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
                </el-select>
                <el-input v-model="keyword" placeholder="搜索姓名或ID..." :prefix-icon="Search" clearable size="small" style="width:160px" />
              </div>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm">
                <thead class="border-b border-gray-100 bg-gray-50">
                  <tr>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">员工</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">部门</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">今日用量</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">限额</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">使用率</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">状态</th>
                    <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-gray-500">操作</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  <tr v-for="e in filteredEmployees" :key="e.id" class="transition hover:bg-gray-50">
                    <td class="px-4 py-3 font-medium text-gray-800">{{ e.name }}</td>
                    <td class="px-4 py-3 text-gray-600">{{ e.department }}</td>
                    <td class="px-4 py-3 text-gray-600">{{ fmtNum(e.totalTokens) }}</td>
                    <td class="px-4 py-3 text-gray-600">{{ fmtNum(e.quota) }}</td>
                    <td class="px-4 py-3">
                      <div class="flex items-center gap-2">
                        <span class="w-8 text-xs text-gray-600">{{ rateOf(e) }}%</span>
                        <div class="h-1.5 w-16 overflow-hidden rounded-full bg-gray-100">
                          <div class="h-full rounded-full transition-all duration-500"
                            :class="statusOf(rateOf(e))==='over'?'bg-red-500':statusOf(rateOf(e))==='warning'?'bg-orange-400':'bg-blue-500'"
                            :style="{width:Math.min(rateOf(e),100)+'%'}" />
                        </div>
                      </div>
                    </td>
                    <td class="px-4 py-3">
                      <el-tag :type="STATUS_MAP[statusOf(rateOf(e))].type" size="small" effect="light">{{ STATUS_MAP[statusOf(rateOf(e))].text }}</el-tag>
                    </td>
                    <td class="px-4 py-3">
                      <el-button link type="primary" size="small" @click="openAdjust(e)">调整上限</el-button>
                    </td>
                  </tr>
                  <tr v-if="loading"><td colspan="7" class="px-4 py-12 text-center text-sm text-gray-400">加载中…</td></tr>
                  <tr v-else-if="!filteredEmployees.length"><td colspan="7" class="px-4 py-12 text-center text-sm text-gray-400">暂无数据</td></tr>
                </tbody>
              </table>
            </div>
            <div class="flex items-center justify-between border-t border-gray-100 px-4 py-3">
              <span class="text-xs text-gray-500">共 {{ filteredEmployees.length }} 人 · 每日 00:00 自动清零</span>
            </div>
          </div>

          <div class="flex flex-col gap-5 lg:col-span-2">
            <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
              <h3 class="text-sm font-semibold text-gray-800">个人配额修改</h3>
              <p class="mb-3 text-xs text-gray-500">仅修改单个员工的每日 Token 配额</p>
              <label class="mb-1 block text-xs text-gray-500">员工姓名/ID</label>
              <el-select v-model="cardUserId" placeholder="请选择员工" clearable filterable class="w-full" @change="onCardUserChange">
                <el-option v-for="e in employees" :key="e.id" :label="`${e.name}（${e.department}）`" :value="e.id" />
              </el-select>
              <template v-if="cardDetail">
                <label class="mt-3 mb-1 block text-xs text-gray-500">当前上限</label>
                <div class="flex items-center gap-2 text-sm font-bold text-gray-800">
                  {{ fmtNum(cardDetail.effectiveQuota) }} <span class="text-xs font-normal text-gray-500">tokens/人/日</span>
                  <el-tag v-if="cardDetail.isPersonalized" size="small" type="primary" effect="light">个性化</el-tag>
                  <el-tag v-else size="small" effect="plain">系统默认</el-tag>
                </div>
              </template>
              <label class="mt-3 mb-1 block text-xs text-gray-500">设置新上限</label>
              <el-input-number v-model="cardInput" :min="1000" :step="1000" controls-position="right" class="w-full" />
              <el-button type="primary" class="mt-3 w-full" @click="saveCard">保存个人配额</el-button>
            </div>

            <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
              <h3 class="text-sm font-semibold text-gray-800">全员默认配额设置</h3>
              <p class="mb-2 text-xs text-gray-500">设置公司全员默认每日 Token 配额</p>
              <div class="mb-2 text-3xl font-extrabold text-gray-800">{{ fmtNum(overview.globalLimit) }} <span class="text-sm font-normal text-gray-500">tokens/人/日</span></div>
              <label class="mb-1 block text-xs text-gray-500">设置新的系统默认上限（立即生效）</label>
              <el-input-number v-model="globalInput" :min="1000" :step="1000" controls-position="right" class="w-full" />
              <el-button type="primary" class="mt-3 w-full" @click="saveGlobal">保存并应用</el-button>
            </div>

            <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
              <h4 class="mb-2 text-sm font-semibold text-gray-800">配额规则说明</h4>
              <ul class="space-y-1.5 text-xs leading-relaxed text-gray-600">
                <li><b class="text-blue-600">①</b> 未单独修改的员工，遵循系统全员默认配额。</li>
                <li><b class="text-blue-600">②</b> 已单独修改的员工，使用个性化配额，不受默认变更影响。</li>
                <li><b class="text-blue-600">③</b> 个人配额修改仅对所选员工生效。</li>
                <li><b class="text-blue-600">④</b> 全员默认配额修改仅对未单独调整的员工生效。</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 调整上限弹窗 -->
    <el-dialog v-model="adjustDialogVisible" :title="`调整「${adjustForm.name}」的每日上限`" width="460px" :close-on-click-modal="false">
      <template v-if="adjustForm.detail">
        <div class="mb-4 grid grid-cols-2 gap-3 rounded-lg bg-gray-50 p-4">
          <div><span class="text-xs text-gray-500">当前生效上限</span><p class="text-sm font-bold">{{ fmtNum(adjustForm.detail.effectiveQuota) }}</p></div>
          <div><span class="text-xs text-gray-500">今日已消耗</span><p class="text-sm font-bold">{{ fmtNum(adjustForm.detail.todayConsumed) }}</p></div>
          <div><span class="text-xs text-gray-500">系统默认</span><p class="text-sm font-bold">{{ fmtNum(adjustForm.detail.globalDefaultQuota) }}</p></div>
          <div><span class="text-xs text-gray-500">配额类型</span><p class="text-sm font-bold">{{ adjustForm.detail.isPersonalized ? '个性化' : '系统默认' }}</p></div>
        </div>
        <label class="mb-1 block text-xs text-gray-500">新上限值（≥ 1000）</label>
        <el-input-number v-model="adjustForm.input" :min="1000" :step="1000" controls-position="right" class="w-full" size="large" />
        <p class="mt-2 text-xs text-gray-400">上调至高于已消耗量时，该员工将立即恢复对话能力。</p>
      </template>
      <p v-else class="py-4 text-center text-sm text-gray-400">加载中…</p>
      <template #footer>
        <el-button @click="doReset" :disabled="!adjustForm.detail || adjustForm.saving">重置为系统默认</el-button>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustForm.saving" @click="saveAdjust">保存</el-button>
      </template>
    </el-dialog>

    <!-- 操作日志抽屉 -->
    <el-drawer v-model="logDrawerVisible" title="配额调整操作日志" size="760px" direction="rtl">
      <div class="mb-4 flex flex-wrap gap-2">
        <el-input v-model="logFilter.operator" placeholder="操作人" clearable size="small" style="width:120px" />
        <el-input v-model="logFilter.target" placeholder="员工姓名/ID" clearable size="small" style="width:140px" />
        <el-date-picker v-model="logFilter.start" type="date" placeholder="开始日期" size="small" value-format="YYYY-MM-DD" style="width:140px" />
        <el-date-picker v-model="logFilter.end" type="date" placeholder="结束日期" size="small" value-format="YYYY-MM-DD" style="width:140px" />
        <el-button type="primary" size="small" @click="loadLogs(true)">筛选</el-button>
      </div>
      <el-table :data="logData.list" stripe size="small" style="width:100%">
        <el-table-column label="操作时间" width="160"><template #default="{row}">{{ fmtTime(row.operatedAt) }}</template></el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column label="目标员工" width="120"><template #default="{row}">{{ row.targetUserName }} <span class="text-gray-400">#{{ row.targetUserId }}</span></template></el-table-column>
        <el-table-column label="调整前" width="100"><template #default="{row}">{{ fmtNum(row.oldQuota) }}</template></el-table-column>
        <el-table-column label="调整后" width="100"><template #default="{row}"><span class="font-bold text-blue-600">{{ fmtNum(row.newQuota) }}</span></template></el-table-column>
        <el-table-column label="当时已消耗"><template #default="{row}">{{ fmtNum(row.currentConsumption) }}</template></el-table-column>
      </el-table>
      <div class="mt-4 flex items-center justify-between text-xs text-gray-500">
        <span>共 {{ logData.total }} 条 · 保留 90 天</span>
        <el-pagination
          :current-page="logPage + 1"
          :page-size="logSize"
          :total="logData.total"
          layout="prev, pager, next"
          small
          background
          @current-change="(p:number) => { logPage = p - 1; loadLogs() }"
        />
      </div>
    </el-drawer>
  </div>
</template>
