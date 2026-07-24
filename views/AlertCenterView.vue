<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Bell,
  CircleCheck,
  Clock,
  DataBoard,
  Delete,
  Document,
  EditPen,
  Histogram,
  HomeFilled,
  Lock,
  Message,
  Plus,
  Search,
  Setting,
  User,
  View,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

type AlertStatus = 'pending' | 'handled'
type RulePreset = '1' | '7' | 'custom'

interface AlertContext {
  time: string
  keyword: string
  before: string
  hit: string
  after: string
}

interface AlertRecord {
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
  status: AlertStatus
  noticeSentAt: string
  email: string
  handledAt?: string
  handledBy?: string
  contexts: AlertContext[]
}

interface AlertRule {
  id: number
  name: string
  periodDays: number
  threshold: number
  enabled: boolean
  isDefault: boolean
  updatedAt: string
  createdBy: string
}

const alerts = ref<AlertRecord[]>([
  {
    id: 20260722001,
    employeeId: 'E2024018',
    name: '张明',
    department: '财务部',
    position: '资金专员',
    triggeredAt: '2026-07-22 10:32:18',
    ruleName: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    triggerCount: 4,
    currentCount: 4,
    keywords: ['合同金额', '银行卡号', '内部报价'],
    status: 'pending',
    noticeSentAt: '2026-07-22 10:32:19',
    email: 'security-admin@qizhitong.cn',
    contexts: [
      { time: '09:16', keyword: '合同金额', before: '请把这份采购合同的', hit: '合同金额', after: '和付款节点整理成表格。' },
      { time: '10:04', keyword: '银行卡号', before: '收款方的', hit: '银行卡号', after: '需要在本周五前复核。' },
      { time: '10:32', keyword: '内部报价', before: '附件包含客户的', hit: '内部报价', after: '，请不要对外发送。' },
    ],
  },
  {
    id: 20260722002,
    employeeId: 'E2023036',
    name: '王芳',
    department: '销售部',
    position: '大客户经理',
    triggeredAt: '2026-07-22 11:08:45',
    ruleName: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    triggerCount: 3,
    currentCount: 3,
    keywords: ['客户名单', '底价'],
    status: 'pending',
    noticeSentAt: '2026-07-22 11:08:46',
    email: 'security-admin@qizhitong.cn',
    contexts: [
      { time: '10:43', keyword: '客户名单', before: '帮我分析一下这批', hit: '客户名单', after: '的行业分布。' },
      { time: '10:56', keyword: '底价', before: '这次投标的', hit: '底价', after: '暂定为项目预算的八成。' },
      { time: '11:08', keyword: '客户名单', before: '请根据', hit: '客户名单', after: '生成本周跟进计划。' },
    ],
  },
  {
    id: 20260722003,
    employeeId: 'E2025052',
    name: '陈思远',
    department: '技术部',
    position: '后端工程师',
    triggeredAt: '2026-07-22 13:46:03',
    ruleName: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    triggerCount: 5,
    currentCount: 5,
    keywords: ['生产密钥', '数据库密码'],
    status: 'pending',
    noticeSentAt: '2026-07-22 13:46:04',
    email: 'security-admin@qizhitong.cn',
    contexts: [
      { time: '11:27', keyword: '生产密钥', before: '这段配置里包含', hit: '生产密钥', after: '，帮我检查格式。' },
      { time: '12:18', keyword: '数据库密码', before: '连接失败可能是', hit: '数据库密码', after: '已经过期。' },
      { time: '13:46', keyword: '生产密钥', before: '请比较两份', hit: '生产密钥', after: '配置的差异。' },
    ],
  },
  {
    id: 20260721004,
    employeeId: 'E2022109',
    name: '李华',
    department: '技术部',
    position: '测试工程师',
    triggeredAt: '2026-07-21 16:20:11',
    ruleName: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    triggerCount: 3,
    currentCount: 0,
    keywords: ['管理员口令'],
    status: 'handled',
    noticeSentAt: '2026-07-21 16:20:12',
    email: 'security-admin@qizhitong.cn',
    handledAt: '2026-07-21 16:45:09',
    handledBy: '管理员',
    contexts: [
      { time: '14:08', keyword: '管理员口令', before: '测试环境的', hit: '管理员口令', after: '需要重新初始化。' },
      { time: '15:11', keyword: '管理员口令', before: '自动化脚本读取不到', hit: '管理员口令', after: '变量。' },
      { time: '16:20', keyword: '管理员口令', before: '日志中打印了', hit: '管理员口令', after: '字段，请帮忙脱敏。' },
    ],
  },
  {
    id: 20260720005,
    employeeId: 'E2023058',
    name: '赵雪',
    department: '人力资源部',
    position: '薪酬专员',
    triggeredAt: '2026-07-20 09:54:30',
    ruleName: '重点岗位高频告警',
    periodDays: 7,
    threshold: 8,
    triggerCount: 8,
    currentCount: 0,
    keywords: ['员工薪资', '身份证号'],
    status: 'handled',
    noticeSentAt: '2026-07-20 09:54:31',
    email: 'security-admin@qizhitong.cn',
    handledAt: '2026-07-20 10:26:44',
    handledBy: '管理员',
    contexts: [
      { time: '09:12', keyword: '员工薪资', before: '请汇总本月的', hit: '员工薪资', after: '异常变动。' },
      { time: '09:31', keyword: '身份证号', before: '表格中包含完整', hit: '身份证号', after: '，导出前需要脱敏。' },
      { time: '09:54', keyword: '员工薪资', before: '这份', hit: '员工薪资', after: '明细仅用于内部核对。' },
    ],
  },
  {
    id: 20260718006,
    employeeId: 'E2024027',
    name: '周宁',
    department: '法务部',
    position: '法务经理',
    triggeredAt: '2026-07-18 15:37:22',
    ruleName: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    triggerCount: 4,
    currentCount: 0,
    keywords: ['未公开条款', '诉讼策略'],
    status: 'handled',
    noticeSentAt: '2026-07-18 15:37:23',
    email: 'security-admin@qizhitong.cn',
    handledAt: '2026-07-18 16:02:18',
    handledBy: '管理员',
    contexts: [
      { time: '11:09', keyword: '未公开条款', before: '协议中有几项', hit: '未公开条款', after: '需要单独复核。' },
      { time: '13:24', keyword: '诉讼策略', before: '请根据目前的', hit: '诉讼策略', after: '补充风险说明。' },
      { time: '15:37', keyword: '未公开条款', before: '不要把这段', hit: '未公开条款', after: '放入对外版本。' },
    ],
  },
])

const rules = ref<AlertRule[]>([
  {
    id: 1,
    name: '系统默认告警规则',
    periodDays: 1,
    threshold: 3,
    enabled: true,
    isDefault: true,
    updatedAt: '2026-07-01 09:00',
    createdBy: '系统内置',
  },
  {
    id: 2,
    name: '重点岗位高频告警',
    periodDays: 7,
    threshold: 8,
    enabled: true,
    isDefault: false,
    updatedAt: '2026-07-18 14:20',
    createdBy: '管理员',
  },
])

const activeSection = ref<'records' | 'rules'>('records')
const sidebarCollapsed = ref(false)
const showRealtimeNotice = ref(false)
const detailVisible = ref(false)
const selectedAlert = ref<AlertRecord | null>(null)
const currentPage = ref(1)
const pageSize = 5
let noticeTimer: number | undefined

const filters = reactive({
  name: '',
  department: '',
  status: '' as '' | AlertStatus,
  dateRange: [] as string[],
})

const departments = computed(() => [...new Set(alerts.value.map(item => item.department))])
const pendingAlerts = computed(() => alerts.value.filter(item => item.status === 'pending'))
const todayAlerts = computed(() => alerts.value.filter(item => item.triggeredAt.startsWith('2026-07-22')))
const enabledRules = computed(() => rules.value.filter(item => item.enabled).length)

const filteredAlerts = computed(() => {
  const keyword = filters.name.trim().toLocaleLowerCase()
  return alerts.value.filter(item => {
    if (keyword && !`${item.name}${item.employeeId}`.toLocaleLowerCase().includes(keyword)) return false
    if (filters.department && item.department !== filters.department) return false
    if (filters.status && item.status !== filters.status) return false
    if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
      const date = item.triggeredAt.slice(0, 10)
      if (date < filters.dateRange[0] || date > filters.dateRange[1]) return false
    }
    return true
  })
})

const pagedAlerts = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredAlerts.value.slice(start, start + pageSize)
})

watch(filters, () => {
  currentPage.value = 1
}, { deep: true })

function resetFilters() {
  filters.name = ''
  filters.department = ''
  filters.status = ''
  filters.dateRange = []
}

function statusLabel(status: AlertStatus) {
  return status === 'pending' ? '待处理' : '已处理'
}

function openDetail(record: AlertRecord) {
  selectedAlert.value = record
  detailVisible.value = true
  showRealtimeNotice.value = false
}

function markHandled(record: AlertRecord) {
  if (record.status === 'handled') return
  record.status = 'handled'
  record.currentCount = 0
  record.handledAt = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  record.handledBy = '管理员'
  ElMessage.success(`已标记为已处理，${record.name}的敏感词触发次数已清零`)
}

const ruleDialogVisible = ref(false)
const editingRuleId = ref<number | null>(null)
const ruleForm = reactive({
  name: '',
  periodPreset: '1' as RulePreset,
  customDays: 2,
  threshold: 3,
  enabled: true,
})

const actualPeriodDays = computed(() => {
  if (ruleForm.periodPreset === 'custom') return Number(ruleForm.customDays)
  return Number(ruleForm.periodPreset)
})

function openAddRule() {
  editingRuleId.value = null
  ruleForm.name = ''
  ruleForm.periodPreset = '1'
  ruleForm.customDays = 2
  ruleForm.threshold = 3
  ruleForm.enabled = true
  ruleDialogVisible.value = true
}

function openEditRule(rule: AlertRule) {
  editingRuleId.value = rule.id
  ruleForm.name = rule.name
  ruleForm.periodPreset = rule.periodDays === 1 ? '1' : rule.periodDays === 7 ? '7' : 'custom'
  ruleForm.customDays = rule.periodDays
  ruleForm.threshold = rule.threshold
  ruleForm.enabled = rule.enabled
  ruleDialogVisible.value = true
}

function saveRule() {
  const name = ruleForm.name.trim()
  const days = actualPeriodDays.value
  const threshold = Number(ruleForm.threshold)
  if (!name) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!Number.isInteger(days) || days < 1 || !Number.isInteger(threshold) || threshold < 1) {
    ElMessage.warning('天数和触发次数必须为正整数')
    return
  }

  if (editingRuleId.value !== null) {
    const target = rules.value.find(item => item.id === editingRuleId.value)
    if (target) {
      target.name = name
      target.periodDays = days
      target.threshold = threshold
      target.enabled = ruleForm.enabled
      target.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    }
    ElMessage.success('告警规则已更新')
  } else {
    rules.value.unshift({
      id: Date.now(),
      name,
      periodDays: days,
      threshold,
      enabled: ruleForm.enabled,
      isDefault: false,
      updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-'),
      createdBy: '管理员',
    })
    ElMessage.success('告警规则已新增')
  }
  ruleDialogVisible.value = false
}

async function deleteRule(rule: AlertRule) {
  try {
    const warning = rule.isDefault
      ? '这是系统默认告警规则。删除后，未匹配自定义规则的员工将不再按默认阈值告警。确定继续删除吗？'
      : `确定删除“${rule.name}”吗？`
    await ElMessageBox.confirm(warning, rule.isDefault ? '删除系统默认规则' : '删除规则', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
    rules.value = rules.value.filter(item => item.id !== rule.id)
    ElMessage.success('告警规则已删除')
  } catch {
    // The user cancelled the confirmation.
  }
}

onMounted(() => {
  noticeTimer = window.setTimeout(() => {
    showRealtimeNotice.value = true
  }, 650)
})

onBeforeUnmount(() => {
  if (noticeTimer) window.clearTimeout(noticeTimer)
})
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand">
        <div class="brand-mark">企</div>
        <span class="brand-name">管理后台</span>
      </div>

      <nav class="side-nav" aria-label="管理后台导航">
        <router-link to="/home" class="nav-item">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </router-link>
        <router-link to="/admin/sensitive-words" class="nav-item">
          <el-icon><DataBoard /></el-icon>
          <span>敏感词概览</span>
        </router-link>
        <router-link to="/admin/sensitive-words" class="nav-item">
          <el-icon><Lock /></el-icon>
          <span>敏感词管理</span>
        </router-link>
        <router-link to="/admin/security-logs" class="nav-item">
          <el-icon><Document /></el-icon>
          <span>触发日志</span>
        </router-link>
        <router-link to="/admin/alerts" class="nav-item active">
          <el-icon><Bell /></el-icon>
          <span>告警</span>
          <em v-if="pendingAlerts.length" class="nav-badge">{{ pendingAlerts.length }}</em>
        </router-link>
        <button type="button" class="nav-item nav-button">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </button>
      </nav>

      <button
        type="button"
        class="collapse-button"
        :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <span :class="{ rotated: sidebarCollapsed }">‹</span>
      </button>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="page-identity">
          <h1>敏感词告警</h1>
          <span>管理后台</span>
        </div>
        <div class="account-area">
          <el-popover placement="bottom-end" :width="360" trigger="click">
            <template #reference>
              <button type="button" class="bell-button" aria-label="查看告警通知">
                <el-icon><Bell /></el-icon>
                <i v-if="pendingAlerts.length" class="bell-dot"></i>
              </button>
            </template>
            <div class="popover-head">
              <strong>通知</strong>
              <span>{{ pendingAlerts.length }} 条待处理</span>
            </div>
            <button
              v-for="item in pendingAlerts.slice(0, 3)"
              :key="item.id"
              type="button"
              class="popover-alert"
              @click="openDetail(item)"
            >
              <span class="popover-icon"><Warning /></span>
              <span>
                <b>{{ item.name }}触发敏感词告警</b>
                <small>{{ item.department }} · {{ item.triggeredAt }}</small>
              </span>
            </button>
          </el-popover>
          <div class="avatar">管</div>
          <span class="admin-name">管理员</span>
          <span class="account-arrow">⌄</span>
        </div>
      </header>

      <main class="content">
        <transition name="notice">
          <section v-if="showRealtimeNotice" class="realtime-notice" aria-live="polite">
            <button type="button" class="notice-close" aria-label="关闭通知" @click="showRealtimeNotice = false">×</button>
            <div class="notice-title"><span>!</span> 您有一条新的敏感词通知</div>
            <p>触发用户：张明｜规则：一天内触发 3 次｜达到时间：10:32:18</p>
            <button type="button" @click="openDetail(alerts[0])">查看详情</button>
          </section>
        </transition>

        <section class="hero-row">
          <div>
            <p class="eyebrow">SECURITY ALERTS</p>
            <h2>告警中心</h2>
            <p>集中查看敏感词告警、处理风险事件并维护触发规则。</p>
          </div>
          <div class="hero-actions">
            <span class="live-chip"><i></i> 实时监测中</span>
            <button type="button" class="primary-button" @click="openAddRule">
              <el-icon><Plus /></el-icon>
              新增规则
            </button>
          </div>
        </section>

        <section class="stats-grid" aria-label="告警统计">
          <article class="stat-card blue">
            <div class="stat-icon"><Warning /></div>
            <div><strong>{{ todayAlerts.length }}</strong><span>今日告警</span></div>
            <small>较昨日 +1</small>
          </article>
          <article class="stat-card orange">
            <div class="stat-icon"><Clock /></div>
            <div><strong>{{ pendingAlerts.length }}</strong><span>待处理</span></div>
            <small>请及时跟进</small>
          </article>
          <article class="stat-card green">
            <div class="stat-icon"><Message /></div>
            <div><strong>{{ todayAlerts.length }}</strong><span>今日已通知</span></div>
            <small>站内 + 邮件</small>
          </article>
          <article class="stat-card violet">
            <div class="stat-icon"><Histogram /></div>
            <div><strong>{{ enabledRules }}</strong><span>启用规则</span></div>
            <small>{{ rules.some(rule => rule.isDefault) ? '含 1 条默认规则' : '未配置默认规则' }}</small>
          </article>
        </section>

        <section class="workspace-card">
          <div class="section-tabs" role="tablist" aria-label="告警工作区">
            <button
              type="button"
              role="tab"
              :aria-selected="activeSection === 'records'"
              :class="{ active: activeSection === 'records' }"
              @click="activeSection = 'records'"
            >
              告警记录 <span>{{ alerts.length }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSection === 'rules'"
              :class="{ active: activeSection === 'rules' }"
              @click="activeSection = 'rules'"
            >
              规则配置 <span>{{ rules.length }}</span>
            </button>
          </div>

          <template v-if="activeSection === 'records'">
            <div class="filter-bar">
              <el-input v-model="filters.name" :prefix-icon="Search" clearable placeholder="员工姓名 / 工号" />
              <el-select v-model="filters.department" clearable placeholder="全部部门">
                <el-option v-for="department in departments" :key="department" :label="department" :value="department" />
              </el-select>
              <el-date-picker
                v-model="filters.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
              />
              <el-select v-model="filters.status" clearable placeholder="全部状态">
                <el-option label="待处理" value="pending" />
                <el-option label="已处理" value="handled" />
              </el-select>
              <button type="button" class="secondary-button" @click="resetFilters">重置</button>
            </div>

            <div class="table-wrap">
              <el-table :data="pagedAlerts" row-key="id" empty-text="没有符合条件的告警记录">
                <el-table-column label="员工信息" min-width="180">
                  <template #default="scope">
                    <div class="employee-cell">
                      <span class="employee-avatar">{{ scope.row.name.slice(0, 1) }}</span>
                      <span><b>{{ scope.row.name }}</b><small>{{ scope.row.employeeId }}</small></span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="department" label="所属部门" min-width="110" />
                <el-table-column label="达到告警时间" min-width="176">
                  <template #default="scope">
                    <span class="time-cell">{{ scope.row.triggeredAt }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="命中敏感词" min-width="205">
                  <template #default="scope">
                    <div class="keyword-list">
                      <span v-for="keyword in scope.row.keywords.slice(0, 2)" :key="keyword">{{ keyword }}</span>
                      <em v-if="scope.row.keywords.length > 2">+{{ scope.row.keywords.length - 2 }}</em>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="累计次数" min-width="96" align="center">
                  <template #default="scope">
                    <b class="count-value" :class="{ cleared: scope.row.currentCount === 0 }">
                      {{ scope.row.currentCount }}
                    </b>
                    <small class="threshold-text">阈值 {{ scope.row.threshold }}</small>
                  </template>
                </el-table-column>
                <el-table-column label="通知方式" min-width="125">
                  <template #default>
                    <div class="channel-cell"><span>站内</span><span>邮件</span></div>
                  </template>
                </el-table-column>
                <el-table-column label="状态" min-width="94">
                  <template #default="scope">
                    <span class="status-tag" :class="scope.row.status">{{ statusLabel(scope.row.status) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" fixed="right" min-width="170">
                  <template #default="scope">
                    <button type="button" class="link-button" @click="openDetail(scope.row)">详情</button>
                    <button
                      v-if="scope.row.status === 'pending'"
                      type="button"
                      class="link-button success"
                      @click="markHandled(scope.row)"
                    >
                      标记已处理
                    </button>
                    <span v-else class="handled-by">{{ scope.row.handledBy }}处理</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <footer class="table-footer">
              <span>共 {{ filteredAlerts.length }} 条记录</span>
              <el-pagination
                v-model:current-page="currentPage"
                :page-size="pageSize"
                :total="filteredAlerts.length"
                layout="prev, pager, next"
                background
              />
            </footer>
          </template>

          <template v-else>
            <div class="rule-callout">
              <div class="rule-callout-icon"><Clock /></div>
              <div>
                <strong>统一计数与通知策略</strong>
                <p>同一员工在同一自然分钟内无论命中多少敏感词，只计为 1 次；不同分钟累计叠加。同一自然日只发送 1 次告警，第 4 次及之后继续累计，但不重复通知。</p>
              </div>
              <span>系统策略</span>
            </div>

            <div class="rule-list-head">
              <div><h3>告警规则</h3><p>规则触发后实时发送站内通知和管理员邮件。</p></div>
              <button type="button" class="primary-button" @click="openAddRule"><el-icon><Plus /></el-icon> 新增规则</button>
            </div>

            <div class="rule-table-wrap">
              <table class="rule-table">
                <thead>
                  <tr><th>规则名称</th><th>统计周期</th><th>触发条件</th><th>通知渠道</th><th>状态</th><th>更新时间</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="rule in rules" :key="rule.id">
                    <td>
                      <div class="rule-name"><b>{{ rule.name }}</b><span v-if="rule.isDefault">系统默认</span><small>{{ rule.createdBy }}</small></div>
                    </td>
                    <td>{{ rule.periodDays === 1 ? '1 天（自然日）' : `${rule.periodDays} 天` }}</td>
                    <td><b class="rule-condition">≥ {{ rule.threshold }} 次</b></td>
                    <td><div class="channel-cell"><span>站内通知</span><span>管理员邮件</span></div></td>
                    <td><el-switch v-model="rule.enabled" inline-prompt active-text="启" inactive-text="停" /></td>
                    <td><span class="time-cell">{{ rule.updatedAt }}</span></td>
                    <td>
                      <button type="button" class="icon-action" :title="rule.isDefault ? '编辑系统默认规则' : '编辑规则'" @click="openEditRule(rule)"><EditPen /></button>
                      <button type="button" class="icon-action danger" :title="rule.isDefault ? '删除系统默认规则' : '删除规则'" @click="deleteRule(rule)"><Delete /></button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </section>
      </main>
    </div>

    <el-dialog v-model="detailVisible" width="720px" class="alert-detail-dialog" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-title"><span><Warning /></span><div><h3>敏感词告警详情</h3><p>告警编号 {{ selectedAlert?.id }}</p></div></div>
      </template>

      <template v-if="selectedAlert">
        <div class="detail-summary">
          <div class="detail-avatar">{{ selectedAlert.name.slice(0, 1) }}</div>
          <div class="detail-person"><strong>{{ selectedAlert.name }}</strong><span>{{ selectedAlert.employeeId }} · {{ selectedAlert.position }}</span></div>
          <div class="detail-department"><User />{{ selectedAlert.department }}</div>
          <span class="status-tag" :class="selectedAlert.status">{{ statusLabel(selectedAlert.status) }}</span>
        </div>

        <div class="detail-meta-grid">
          <div><span>达到告警时间</span><b>{{ selectedAlert.triggeredAt }}</b></div>
          <div><span>触发规则</span><b>{{ selectedAlert.ruleName }}</b></div>
          <div><span>统计周期</span><b>{{ selectedAlert.periodDays }} 天</b></div>
          <div><span>触发 / 当前累计</span><b>{{ selectedAlert.triggerCount }} 次 / {{ selectedAlert.currentCount }} 次</b></div>
        </div>

        <section class="detail-section">
          <div class="detail-section-title"><h4>命中内容与上下文</h4><span>同分钟多词仅计 1 次</span></div>
          <div class="context-list">
            <article v-for="context in selectedAlert.contexts" :key="`${context.time}-${context.keyword}`">
              <div><time>{{ context.time }}</time><span>{{ context.keyword }}</span></div>
              <p>{{ context.before }}<mark>{{ context.hit }}</mark>{{ context.after }}</p>
            </article>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section-title"><h4>通知与处理记录</h4></div>
          <div class="history-list">
            <div class="history-item completed"><i><Message /></i><span><b>告警通知及邮件已发送</b><small>{{ selectedAlert.noticeSentAt }} · {{ selectedAlert.email }}</small></span></div>
            <div v-if="selectedAlert.handledAt" class="history-item completed"><i><CircleCheck /></i><span><b>{{ selectedAlert.handledBy }}已标记处理，累计次数清零</b><small>{{ selectedAlert.handledAt }}</small></span></div>
            <div v-else class="history-item current"><i><Clock /></i><span><b>等待管理员处理</b><small>同一自然日内不再重复发送通知，后台继续累计次数</small></span></div>
          </div>
        </section>
      </template>

      <template #footer>
        <button type="button" class="secondary-button" @click="detailVisible = false">关闭</button>
        <button
          v-if="selectedAlert?.status === 'pending'"
          type="button"
          class="primary-button success-button"
          @click="markHandled(selectedAlert)"
        >
          <el-icon><CircleCheck /></el-icon> 标记为已处理并清零
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ruleDialogVisible"
      :title="editingRuleId === null ? '新增告警规则' : '编辑告警规则'"
      width="540px"
      class="rule-dialog"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.name" maxlength="30" show-word-limit placeholder="例如：研发部门高频告警" />
        </el-form-item>
        <el-form-item label="统计周期" required>
          <el-radio-group v-model="ruleForm.periodPreset">
            <el-radio-button value="1">一天</el-radio-button>
            <el-radio-button value="7">一周</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
          <div v-if="ruleForm.periodPreset === 'custom'" class="number-field">
            <el-input-number v-model="ruleForm.customDays" :min="1" :step="1" :precision="0" controls-position="right" />
            <span>天（正整数）</span>
          </div>
        </el-form-item>
        <el-form-item label="触发次数" required>
          <div class="number-field">
            <span>周期内达到</span>
            <el-input-number v-model="ruleForm.threshold" :min="1" :step="1" :precision="0" controls-position="right" />
            <span>次时告警</span>
          </div>
        </el-form-item>
        <el-form-item label="通知方式">
          <div class="fixed-channels"><span><Message /> 站内实时通知</span><span>✉ 管理员邮件</span><em>均为必选渠道</em></div>
        </el-form-item>
        <el-form-item label="规则状态">
          <el-switch v-model="ruleForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>

      <div class="rule-preview">
        <b>规则预览</b>
        <p>同一员工在 <strong>{{ actualPeriodDays }}</strong> 天内累计触发敏感词 <strong>{{ ruleForm.threshold }}</strong> 次及以上时，立即向管理员发送站内通知和邮件。</p>
      </div>

      <template #footer>
        <button type="button" class="secondary-button" @click="ruleDialogVisible = false">取消</button>
        <button type="button" class="primary-button" @click="saveRule">保存规则</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-shell {
  --blue: #246bfd;
  --blue-dark: #1558e8;
  --nav: #0d1728;
  --text: #162238;
  --muted: #738096;
  display: flex;
  width: 100%;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
  color: var(--text);
  background: #f4f6f9;
}

.sidebar {
  z-index: 10;
  display: flex;
  width: 236px;
  flex: 0 0 236px;
  flex-direction: column;
  color: #aeb8c7;
  background: var(--nav);
  border-right: 1px solid #1e293b;
  transition: width .2s ease, flex-basis .2s ease;
}

.sidebar.collapsed { width: 72px; flex-basis: 72px; }

.brand {
  display: flex;
  height: 64px;
  flex: 0 0 64px;
  align-items: center;
  gap: 13px;
  padding: 0 18px;
  overflow: hidden;
  border-bottom: 1px solid rgba(255,255,255,.08);
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(145deg, #2f7cff, #1458ed);
  border-radius: 10px;
  box-shadow: 0 6px 16px rgba(36,107,253,.32);
}

.brand-name { color: #fff; font-size: 16px; font-weight: 700; white-space: nowrap; }
.collapsed .brand-name, .collapsed .nav-item span, .collapsed .nav-badge { display: none; }

.side-nav { flex: 1; padding: 18px 10px; overflow: hidden auto; }

.nav-item {
  position: relative;
  display: flex;
  width: 100%;
  height: 46px;
  align-items: center;
  gap: 13px;
  margin-bottom: 5px;
  padding: 0 14px;
  color: #aeb8c7;
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
  background: transparent;
  border: 0;
  border-radius: 9px;
  cursor: pointer;
  transition: .18s ease;
}

.nav-item:hover { color: #fff; background: rgba(255,255,255,.06); }
.nav-item.active { color: #fff; background: rgba(36,107,253,.28); }
.nav-item.active::before { position: absolute; left: -10px; width: 3px; height: 24px; content: ''; background: #4c8aff; border-radius: 0 4px 4px 0; }
.nav-item .el-icon { flex: 0 0 18px; font-size: 18px; }
.nav-button { text-align: left; }
.nav-badge { margin-left: auto; padding: 1px 7px; color: #fff; font-size: 11px; font-style: normal; background: #ef5350; border-radius: 10px; }

.collapse-button { height: 50px; color: #667389; background: transparent; border: 0; border-top: 1px solid rgba(255,255,255,.08); cursor: pointer; }
.collapse-button:hover { color: #fff; background: rgba(255,255,255,.04); }
.collapse-button span { display: inline-block; font-size: 28px; transition: transform .2s; }
.collapse-button .rotated { transform: rotate(180deg); }

.main-area { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.topbar { display: flex; height: 64px; flex: 0 0 64px; align-items: center; justify-content: space-between; padding: 0 26px; background: #fff; border-bottom: 1px solid #e6eaf0; }
.page-identity { display: flex; align-items: center; gap: 12px; }
.page-identity h1 { margin: 0; font-size: 18px; font-weight: 750; }
.page-identity span { padding: 4px 9px; color: #7a8495; font-size: 12px; background: #f3f5f8; border-radius: 5px; }
.account-area { display: flex; align-items: center; gap: 10px; color: #606b7d; }
.bell-button { position: relative; display: grid; width: 36px; height: 36px; margin-right: 4px; place-items: center; color: #8a96a8; background: transparent; border: 0; border-radius: 9px; cursor: pointer; }
.bell-button:hover { color: var(--blue); background: #f4f7ff; }
.bell-dot { position: absolute; top: 7px; right: 7px; width: 7px; height: 7px; background: #f04438; border: 2px solid #fff; border-radius: 50%; box-sizing: content-box; }
.avatar { display: grid; width: 34px; height: 34px; place-items: center; color: #fff; font-size: 13px; font-weight: 700; background: var(--blue); border-radius: 50%; }
.admin-name { font-size: 14px; }
.account-arrow { color: #9ba5b4; }

.content { position: relative; flex: 1; min-height: 0; padding: 24px 26px 34px; overflow: auto; }
.hero-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.eyebrow { margin: 0 0 6px; color: var(--blue); font-size: 10px; font-weight: 800; letter-spacing: 1.4px; }
.hero-row h2 { margin: 0; font-size: 25px; letter-spacing: -.3px; }
.hero-row p:not(.eyebrow) { margin: 7px 0 0; color: var(--muted); font-size: 13px; }
.hero-actions { display: flex; align-items: center; gap: 12px; }
.live-chip { display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; color: #26795b; font-size: 12px; font-weight: 600; background: #eaf8f1; border: 1px solid #c8efdc; border-radius: 20px; }
.live-chip i { width: 7px; height: 7px; background: #2daf78; border-radius: 50%; box-shadow: 0 0 0 4px rgba(45,175,120,.12); }

.primary-button, .secondary-button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 6px; padding: 0 15px; font-size: 13px; font-weight: 600; border-radius: 7px; cursor: pointer; transition: .18s; }
.primary-button { color: #fff; background: var(--blue); border: 1px solid var(--blue); box-shadow: 0 4px 10px rgba(36,107,253,.18); }
.primary-button:hover { background: var(--blue-dark); }
.secondary-button { color: #536176; background: #fff; border: 1px solid #d9dfe8; }
.secondary-button:hover { color: var(--blue); border-color: #9cbbff; }
.success-button { background: #20a36a; border-color: #20a36a; box-shadow: 0 4px 10px rgba(32,163,106,.17); }
.success-button:hover { background: #178b59; }

.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 14px; margin-bottom: 16px; }
.stat-card { position: relative; display: flex; min-height: 100px; align-items: center; gap: 14px; padding: 18px; overflow: hidden; background: #fff; border: 1px solid #e5e9ef; border-radius: 11px; box-shadow: 0 2px 8px rgba(18,34,62,.04); }
.stat-icon { display: grid; width: 46px; height: 46px; flex: 0 0 46px; place-items: center; border-radius: 11px; }
.stat-icon svg { width: 21px; }
.stat-card strong { display: block; margin-bottom: 3px; font-size: 25px; line-height: 1; }
.stat-card span { color: #6f7b8d; font-size: 12px; }
.stat-card small { position: absolute; right: 16px; bottom: 17px; color: #9aa4b3; font-size: 10px; }
.stat-card.blue .stat-icon { color: #246bfd; background: #eaf1ff; }
.stat-card.orange .stat-icon { color: #f08b32; background: #fff3e6; }
.stat-card.green .stat-icon { color: #26a66f; background: #e9f8f1; }
.stat-card.violet .stat-icon { color: #7967e8; background: #f0eeff; }

.workspace-card { min-width: 0; background: #fff; border: 1px solid #e3e7ed; border-radius: 11px; box-shadow: 0 2px 8px rgba(20,35,60,.04); }
.section-tabs { display: flex; height: 57px; align-items: flex-end; gap: 28px; padding: 0 22px; border-bottom: 1px solid #e8ebf0; }
.section-tabs button { position: relative; height: 57px; padding: 0 2px; color: #748095; font-size: 14px; font-weight: 600; background: transparent; border: 0; cursor: pointer; }
.section-tabs button.active { color: var(--blue); }
.section-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 3px; content: ''; background: var(--blue); border-radius: 3px 3px 0 0; }
.section-tabs button span { margin-left: 4px; padding: 1px 6px; color: inherit; font-size: 10px; background: #eef2f7; border-radius: 8px; }
.section-tabs button.active span { background: #eaf1ff; }

.filter-bar { display: grid; grid-template-columns: minmax(170px,1fr) 150px minmax(280px,1.35fr) 130px auto; gap: 10px; padding: 16px 20px; background: #fbfcfd; border-bottom: 1px solid #eef0f4; }
.filter-bar :deep(.el-date-editor) { width: 100%; }
.filter-bar :deep(.el-input__wrapper), .filter-bar :deep(.el-select__wrapper), .filter-bar :deep(.el-date-editor.el-input__wrapper) { min-height: 36px; box-shadow: 0 0 0 1px #dfe4eb inset; }
.table-wrap { width: 100%; overflow-x: auto; }
.table-wrap :deep(.el-table) { min-width: 1140px; --el-table-border-color: #edf0f4; --el-table-header-bg-color: #fafbfc; --el-table-row-hover-bg-color: #f8faff; color: #344054; font-size: 12px; }
.table-wrap :deep(.el-table th.el-table__cell) { height: 47px; color: #657188; font-weight: 650; background: #fafbfc; }
.table-wrap :deep(.el-table td.el-table__cell) { height: 62px; }
.employee-cell { display: flex; align-items: center; gap: 10px; }
.employee-avatar { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: #2868e8; font-weight: 700; background: #edf3ff; border-radius: 9px; }
.employee-cell b, .employee-cell small { display: block; }
.employee-cell b { margin-bottom: 2px; color: #26354d; font-size: 13px; }
.employee-cell small { color: #929baa; font-size: 10px; }
.time-cell { color: #647086; font-variant-numeric: tabular-nums; white-space: nowrap; }
.keyword-list { display: flex; flex-wrap: wrap; gap: 4px; }
.keyword-list span { padding: 3px 7px; color: #ca4b45; background: #fff1f0; border: 1px solid #ffdedb; border-radius: 4px; }
.keyword-list em { padding: 3px 5px; color: #8f98a7; font-size: 10px; font-style: normal; }
.count-value { display: block; color: #e35b50; font-size: 17px; }
.count-value.cleared { color: #2ba975; }
.threshold-text { display: block; color: #9aa3b1; font-size: 9px; }
.channel-cell { display: flex; flex-wrap: wrap; gap: 4px; }
.channel-cell span { padding: 3px 6px; color: #58708e; font-size: 10px; background: #f1f5f9; border-radius: 4px; white-space: nowrap; }
.status-tag { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; font-size: 11px; font-weight: 600; border-radius: 5px; white-space: nowrap; }
.status-tag::before { width: 5px; height: 5px; content: ''; border-radius: 50%; }
.status-tag.pending { color: #c96c1c; background: #fff4e7; border: 1px solid #ffe1bd; }
.status-tag.pending::before { background: #eb8c32; }
.status-tag.handled { color: #21845a; background: #ecf9f2; border: 1px solid #cceedd; }
.status-tag.handled::before { background: #2aa870; }
.link-button { padding: 3px 5px; color: var(--blue); font-size: 12px; background: transparent; border: 0; cursor: pointer; }
.link-button:hover { text-decoration: underline; }
.link-button.success { color: #219463; }
.handled-by { margin-left: 7px; color: #a0a8b5; font-size: 10px; }
.table-footer { display: flex; min-height: 58px; align-items: center; justify-content: space-between; padding: 10px 20px; color: #7e899a; font-size: 11px; border-top: 1px solid #edf0f4; }

.rule-callout { display: flex; align-items: center; gap: 13px; margin: 18px 20px 0; padding: 14px 16px; background: #f4f8ff; border: 1px solid #dce8ff; border-radius: 9px; }
.rule-callout-icon { display: grid; width: 36px; height: 36px; flex: 0 0 36px; place-items: center; color: var(--blue); background: #e2ecff; border-radius: 9px; }
.rule-callout strong { display: block; margin-bottom: 3px; font-size: 13px; }
.rule-callout p { margin: 0; color: #69778c; font-size: 11px; line-height: 1.65; }
.rule-callout > span { margin-left: auto; padding: 4px 8px; color: #5270a6; font-size: 10px; background: #e7efff; border-radius: 5px; white-space: nowrap; }
.rule-list-head { display: flex; align-items: flex-end; justify-content: space-between; padding: 22px 20px 13px; }
.rule-list-head h3 { margin: 0 0 5px; font-size: 16px; }
.rule-list-head p { margin: 0; color: #7b8798; font-size: 11px; }
.rule-table-wrap { padding: 0 20px 22px; overflow-x: auto; }
.rule-table { width: 100%; min-width: 950px; border-spacing: 0; border: 1px solid #e7ebf0; border-radius: 8px; }
.rule-table th, .rule-table td { padding: 13px 14px; text-align: left; font-size: 11px; border-bottom: 1px solid #edf0f3; }
.rule-table th { color: #6b778c; font-weight: 650; background: #fafbfc; }
.rule-table tr:last-child td { border-bottom: 0; }
.rule-name { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 4px 7px; }
.rule-name b { color: #2c394d; font-size: 12px; }
.rule-name > span { justify-self: start; padding: 2px 5px; color: #5c70a0; font-size: 9px; background: #edf2ff; border-radius: 4px; }
.rule-name small { grid-column: 1 / -1; color: #9aa3b0; font-size: 9px; }
.rule-condition { color: #df5d52; }
.icon-action svg { width: 13px; }
.icon-action { width: 29px; height: 29px; margin-right: 4px; padding: 7px; color: #5272a5; background: #f3f6fa; border: 0; border-radius: 6px; cursor: pointer; }
.icon-action:hover { color: var(--blue); background: #eaf1ff; }
.icon-action.danger:hover { color: #e04b45; background: #fff0ef; }

.realtime-notice { position: fixed; z-index: 2000; top: 78px; right: 24px; width: min(390px, calc(100vw - 40px)); padding: 16px 18px; background: #fff; border: 1px solid #e4e8ee; border-left: 4px solid #f2ad42; border-radius: 9px; box-shadow: 0 12px 34px rgba(21,34,56,.17); }
.notice-title { display: flex; align-items: center; gap: 8px; padding-right: 22px; font-size: 14px; font-weight: 750; }
.notice-title span { display: grid; width: 19px; height: 19px; place-items: center; color: #fff; font-size: 11px; background: #efac45; border-radius: 50%; }
.realtime-notice p { margin: 8px 0 11px; color: #6c7788; font-size: 11px; line-height: 1.6; }
.realtime-notice > button:not(.notice-close) { padding: 6px 11px; color: #fff; font-size: 11px; font-weight: 600; background: var(--blue); border: 0; border-radius: 5px; cursor: pointer; }
.notice-close { position: absolute; top: 8px; right: 10px; color: #9ca5b2; font-size: 20px; background: transparent; border: 0; cursor: pointer; }
.notice-enter-active, .notice-leave-active { transition: .25s ease; }
.notice-enter-from, .notice-leave-to { opacity: 0; transform: translateY(-10px); }

.popover-head { display: flex; align-items: center; justify-content: space-between; padding: 3px 4px 9px; border-bottom: 1px solid #edf0f4; }
.popover-head strong { font-size: 14px; }
.popover-head span { color: #e0752f; font-size: 10px; }
.popover-alert { display: flex; width: 100%; gap: 9px; padding: 11px 4px; text-align: left; background: #fff; border: 0; border-bottom: 1px solid #f0f2f5; cursor: pointer; }
.popover-alert:hover { background: #f8faff; }
.popover-alert > span:last-child { display: grid; gap: 4px; }
.popover-alert b { color: #344054; font-size: 11px; }
.popover-alert small { color: #8c96a6; font-size: 9px; }
.popover-icon { display: grid; width: 28px; height: 28px; padding: 7px; color: #e88739; background: #fff3e7; border-radius: 7px; }

.dialog-title { display: flex; align-items: center; gap: 11px; }
.dialog-title > span { display: grid; width: 38px; height: 38px; padding: 10px; color: #e87935; background: #fff0e5; border-radius: 9px; }
.dialog-title h3 { margin: 0 0 2px; font-size: 16px; }
.dialog-title p { margin: 0; color: #929baa; font-size: 10px; }
.detail-summary { display: flex; align-items: center; gap: 11px; padding: 14px; background: #f8fafc; border: 1px solid #e9edf2; border-radius: 9px; }
.detail-avatar { display: grid; width: 42px; height: 42px; place-items: center; color: #2768ec; font-size: 16px; font-weight: 700; background: #e8f0ff; border-radius: 10px; }
.detail-person { display: grid; gap: 3px; }
.detail-person strong { font-size: 14px; }
.detail-person span { color: #8490a2; font-size: 10px; }
.detail-department { display: flex; align-items: center; gap: 5px; margin-left: auto; color: #69778b; font-size: 11px; }
.detail-department svg { width: 13px; }
.detail-summary .status-tag { margin-left: 12px; }
.detail-meta-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 1px; margin: 14px 0; overflow: hidden; background: #e7ebf0; border: 1px solid #e7ebf0; border-radius: 8px; }
.detail-meta-grid div { display: grid; gap: 4px; padding: 11px 13px; background: #fff; }
.detail-meta-grid span { color: #8791a1; font-size: 9px; }
.detail-meta-grid b { color: #39465a; font-size: 11px; font-weight: 600; }
.detail-section { margin-top: 17px; }
.detail-section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 9px; }
.detail-section-title h4 { margin: 0; font-size: 13px; }
.detail-section-title span { color: #939caa; font-size: 9px; }
.context-list { display: grid; gap: 7px; }
.context-list article { display: grid; grid-template-columns: 105px 1fr; align-items: center; padding: 10px 12px; background: #fff; border: 1px solid #e7ebf0; border-radius: 7px; }
.context-list article > div { display: grid; gap: 4px; }
.context-list time { color: #7c8797; font-size: 10px; }
.context-list article span { justify-self: start; padding: 2px 5px; color: #c94f48; font-size: 9px; background: #fff0ef; border-radius: 3px; }
.context-list p { margin: 0; color: #4d5a6d; font-size: 11px; line-height: 1.7; }
.context-list mark { padding: 1px 3px; color: #b43e37; font-weight: 700; background: #ffe0dd; border-radius: 3px; }
.history-list { margin-left: 11px; border-left: 1px solid #dce2e9; }
.history-item { display: flex; gap: 10px; margin-left: -11px; padding-bottom: 12px; }
.history-item:last-child { padding-bottom: 0; }
.history-item i { display: grid; width: 21px; height: 21px; flex: 0 0 21px; padding: 5px; color: #fff; background: #2baa75; border: 2px solid #fff; border-radius: 50%; box-shadow: 0 0 0 1px #cfeadd; }
.history-item.current i { color: #d07a32; background: #fff; box-shadow: 0 0 0 1px #f2c79f; }
.history-item span { display: grid; gap: 3px; }
.history-item b { color: #465268; font-size: 10px; }
.history-item small { color: #9099a7; font-size: 9px; }

.number-field { display: flex; align-items: center; gap: 10px; margin-top: 9px; color: #687589; font-size: 12px; }
.fixed-channels { display: flex; width: 100%; flex-wrap: wrap; align-items: center; gap: 8px; }
.fixed-channels span { display: inline-flex; align-items: center; gap: 5px; padding: 7px 9px; color: #46658f; font-size: 11px; background: #f2f6fc; border: 1px solid #e1e8f2; border-radius: 6px; }
.fixed-channels svg { width: 13px; }
.fixed-channels em { color: #969fad; font-size: 10px; font-style: normal; }
.rule-preview { margin-top: 4px; padding: 12px 14px; color: #5d6b80; background: #f5f8ff; border: 1px solid #e0e9fb; border-radius: 8px; }
.rule-preview b { color: #3d5e91; font-size: 11px; }
.rule-preview p { margin: 5px 0 0; font-size: 10px; line-height: 1.65; }
.rule-preview strong { color: #2868eb; }

:deep(.el-dialog) { max-width: calc(100vw - 32px); border-radius: 12px; }
:deep(.el-dialog__header) { padding: 19px 20px 14px; border-bottom: 1px solid #edf0f4; }
:deep(.el-dialog__body) { padding: 16px 20px; }
:deep(.el-dialog__footer) { padding: 13px 20px 18px; }
.rule-dialog :deep(.el-form-item) { margin-bottom: 17px; }
.rule-dialog :deep(.el-form-item__label) { color: #48566a; font-size: 12px; font-weight: 600; }

@media (max-width: 1180px) {
  .stats-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .filter-bar { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .filter-bar .secondary-button { justify-self: start; }
}

@media (max-width: 780px) {
  .sidebar { width: 72px; flex-basis: 72px; }
  .sidebar .brand-name, .sidebar .nav-item span, .sidebar .nav-badge { display: none; }
  .topbar { padding: 0 16px; }
  .content { padding: 18px 14px 28px; }
  .admin-name, .account-arrow { display: none; }
  .hero-row { align-items: flex-start; flex-direction: column; }
  .hero-actions { width: 100%; justify-content: space-between; }
  .stats-grid { grid-template-columns: 1fr; }
  .filter-bar { grid-template-columns: 1fr; }
  .detail-meta-grid { grid-template-columns: 1fr; }
  .context-list article { grid-template-columns: 1fr; gap: 7px; }
}

@media (max-width: 520px) {
  .sidebar { width: 58px; flex-basis: 58px; }
  .brand { padding: 0 11px; }
  .side-nav { padding: 14px 6px; }
  .nav-item { padding: 0 14px; }
  .page-identity span, .live-chip { display: none; }
  .detail-summary { flex-wrap: wrap; }
  .detail-department { margin-left: 53px; }
  .detail-summary .status-tag { margin-left: auto; }
}
</style>
