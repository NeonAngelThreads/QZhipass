<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Bell,
  DataBoard,
  Document,
  HomeFilled,
  Lock,
  Search,
  Setting,
  User,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '../api/http'
import {
  createAgent as createAgentRequest,
  deleteAgent as deleteAgentRequest,
  listAgents,
  type AgentPayload
} from '../api/agent'

/* ================================================================
   类型定义
   ================================================================ */
interface AgentItem {
  id: string
  title: string
  prompt: string
  createdAt?: string
}

function toAgentItem(agent: AgentPayload): AgentItem {
  return {
    id: agent.id,
    title: agent.name,
    prompt: agent.prompt,
    createdAt: agent.createdAt ?? undefined
  }
}

/* ================================================================
   侧边栏
   ================================================================ */
const sidebarCollapsed = ref(false)

/* ================================================================
   用户数据
   ================================================================ */
const agents = ref<AgentItem[]>([])
const loading = ref(false)
const searchKeyword = ref('')
// 新增Agent弹窗
const dialogVisible = ref(false)

const agentForm = ref({
  title: '',
  prompt: ''
})


function openCreateDialog() {
  agentForm.value = {
    title: '',
    prompt: ''
  }

  dialogVisible.value = true
}


function closeDialog() {
  dialogVisible.value = false
}


async function createAgent() {

  if (
    !agentForm.value.title.trim() ||
    !agentForm.value.prompt.trim()
  ) {
    ElMessage.warning('请输入完整的Agent标题和提示词')
    return
  }

  try {
    const created = await createAgentRequest(
      agentForm.value.title.trim(),
      agentForm.value.prompt.trim()
    )
    agents.value.unshift(toAgentItem(created))
    ElMessage.success('创建成功')
    closeDialog()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '创建失败，请稍后重试'))
  }
}






/* ================================================================
   注销弹窗
   ================================================================ */
const deactivateModalVisible = ref(false)
const deactivateTarget = ref<AgentItem | null>(null)
const deactivateConfirmed = ref(false)

function openDeactivateModal(user: AgentItem) {
  deactivateTarget.value = user
  deactivateConfirmed.value = false
  deactivateModalVisible.value = true
}

function closeDeactivateModal() {
  deactivateModalVisible.value = false
  deactivateTarget.value = null
  deactivateConfirmed.value = false
}

async function doDeactivate() {
  if (!deactivateConfirmed.value) {
    ElMessage.warning('请先勾选确认框')
    return
  }
  const uid = deactivateTarget.value?.id
  if (!uid) return

  try {
    await deleteAgentRequest(uid)
    agents.value = agents.value.filter(item => item.id !== uid)
    ElMessage.success('删除成功')
    closeDeactivateModal()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除失败，请稍后重试'))
  }
}

/* ================================================================
   分页
   ================================================================ */
const currentPage = ref(1)
const pageSize = ref(10)

const paginatedAgents = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})

/* ---- 搜索过滤 ---- */
const filteredUsers = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return agents.value
  return agents.value.filter((u: AgentItem) =>
    u.title.toLowerCase().includes(kw) ||
    u.prompt.includes(kw)
  )
})

const totalFiltered = computed(() => filteredUsers.value.length)

function handleSearch() {
  currentPage.value = 1
}

/* ================================================================
   数据加载
   ================================================================ */

onMounted(() => {
  fetchAgents()
})
async function fetchAgents() {
  loading.value = true

  try {

    agents.value = (await listAgents()).map(toAgentItem)

  } catch (error) {

    ElMessage.error(getErrorMessage(error, '读取Agent失败'))

  } finally {

    loading.value = false

  }
}
</script>

<template>
  <div class="admin-shell">
    <!-- ============ 侧边栏 ============ -->
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
        <router-link to="/admin/alerts" class="nav-item">
          <el-icon><Bell /></el-icon>
          <span>告警</span>
        </router-link>
        <router-link to="/admin/users" class="nav-item active">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
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

    <!-- ============ 主区域 ============ -->
    <div class="main-area">
      <header class="topbar">
        <div class="page-identity">
          <h1>Agent管理</h1>
          <span>管理后台</span>
        </div>

        <el-button
  type="primary"
  @click="openCreateDialog"
>
  + 新增 Agent
</el-button>

        <div class="account-area">
          <div class="avatar">管</div>
          <span class="admin-name">管理员</span>
        </div>
      </header>

      <div class="content">
        <!-- 搜索栏 -->
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索Agent名称" :prefix-icon="Search" clearable @input="handleSearch" />
          <button class="secondary-button" @click="fetchAgents">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" /></svg>
            刷新
          </button>
        </div>

        <!-- 用户表格 -->
        <div class="workspace-card">
          <div class="section-tabs">
            <button class="active">我的Agent<span>{{ totalFiltered }}</span></button>
          </div>

          <div class="table-wrap" v-loading="loading">
            <el-table :data="paginatedAgents" stripe empty-text="暂无agent数据" style="width: 100%">
              <el-table-column label="Agent名称" min-width="120">
                <template #default="{ row }">
                  <div class="employee-cell">
                    <div class="employee-avatar">{{ row.title.charAt(0) }}</div>
                    <div>
                      <b>{{ row.title }}</b>
                      <small>{{ row.prompt }}</small>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="110" align="center">
                 <template #default>
                     <span class="status-tag active-tag">
                     正常
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" min-width="160">
                <template #default="{ row }">
                  <span class="time-cell">{{ row.createdAt || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center" fixed="right">
                <template #default="{ row }">
                  
    <button
      class="link-button danger"
      @click="openDeactivateModal(row)"
    >
      删除
    </button>
                  </template>
                
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="table-footer">
              <span>共 {{ totalFiltered }} 条记录</span>
              <div class="flex items-center gap-2">
                <el-pagination
                  v-model:current-page="currentPage"
                  v-model:page-size="pageSize"
                  :page-sizes="[10, 20, 50]"
                  :total="totalFiltered"
                  layout="prev, pager, next, sizes"
                  small
                  background
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 注销确认弹窗 ============ -->
    <Teleport to="body">
      <div v-if="deactivateModalVisible" class="modal-overlay" @click.self="closeDeactivateModal">
        <div class="modal-card">
          <div class="modal-header">
            <h3>删除agent</h3>
            <button class="notice-close" @click="closeDeactivateModal">&times;</button>
          </div>

          <div style="padding: 16px 20px">
            <div class="deactivate-body">
              <div class="warn-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </div>
              <p class="deactivate-text">
                您确定要删除Agent【{{ deactivateTarget?.title }}】吗？ 删除后该Agent将无法使用 ，且该操作不可撤销。。
              </p>
            </div>

            <label class="checkbox-label">
              <input v-model="deactivateConfirmed" type="checkbox" />
              <span>我已了解注销风险，确认执行。</span>
            </label>
          </div>

          <div style="display:flex;justify-content:flex-end;gap:10px;padding:14px 20px 18px;border-top:1px solid #edf0f4">
            <button type="button" class="secondary-button" @click="closeDeactivateModal">取消</button>
            <button type="button" class="primary-button danger-button" @click="doDeactivate">确认注销</button>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- 新增Agent弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="新增 Agent"
      width="500px"
    >

      <el-form>

        <el-form-item label="Agent标题">
          <el-input
            v-model="agentForm.title"
            placeholder="请输入Agent标题"
          />
        </el-form-item>


        <el-form-item label="提示词">
          <el-input
            v-model="agentForm.prompt"
            type="textarea"
            :rows="5"
            placeholder="请输入提示词"
          />
        </el-form-item>

      </el-form>


      <template #footer>

        <el-button @click="closeDialog">
          取消
        </el-button>

        <el-button
          type="primary"
          @click="createAgent"
        >
          创建
        </el-button>

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

/* ---- 侧边栏 ---- */
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

/* ---- 主区域 ---- */
.main-area { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.topbar { display: flex; height: 64px; flex: 0 0 64px; align-items: center; justify-content: space-between; padding: 0 26px; background: #fff; border-bottom: 1px solid #e6eaf0; }
.page-identity { display: flex; align-items: center; gap: 12px; }
.page-identity h1 { margin: 0; font-size: 18px; font-weight: 750; }
.page-identity span { padding: 4px 9px; color: #7a8495; font-size: 12px; background: #f3f5f8; border-radius: 5px; }
.account-area { display: flex; align-items: center; gap: 10px; color: #606b7d; }
.avatar { display: grid; width: 34px; height: 34px; place-items: center; color: #fff; font-size: 13px; font-weight: 700; background: var(--blue); border-radius: 50%; }
.admin-name { font-size: 14px; }

/* ---- 内容区 ---- */
.content { position: relative; flex: 1; min-height: 0; padding: 24px 26px 34px; overflow: auto; }

/* ---- 统计卡片 ---- */
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

/* ---- 搜索栏 ---- */
.filter-bar { display: flex; gap: 10px; padding: 14px 0; align-items: center; }
.filter-bar .el-input { max-width: 320px; }

/* ---- 按钮 ---- */
.primary-button, .secondary-button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 6px; padding: 0 15px; font-size: 13px; font-weight: 600; border-radius: 7px; cursor: pointer; transition: .18s; }
.primary-button { color: #fff; background: var(--blue); border: 1px solid var(--blue); box-shadow: 0 4px 10px rgba(36,107,253,.18); }
.primary-button:hover { background: var(--blue-dark); }
.danger-button { background: #e04b45; border-color: #e04b45; box-shadow: 0 4px 10px rgba(224,75,69,.18); }
.danger-button:hover { background: #c93a34; }
.secondary-button { color: #536176; background: #fff; border: 1px solid #d9dfe8; }
.secondary-button:hover { color: var(--blue); border-color: #9cbbff; }

/* ---- 工作区 ---- */
.workspace-card { min-width: 0; background: #fff; border: 1px solid #e3e7ed; border-radius: 11px; box-shadow: 0 2px 8px rgba(20,35,60,.04); }
.section-tabs { display: flex; height: 57px; align-items: flex-end; gap: 28px; padding: 0 22px; border-bottom: 1px solid #e8ebf0; }
.section-tabs button { position: relative; height: 57px; padding: 0 2px; color: #748095; font-size: 14px; font-weight: 600; background: transparent; border: 0; cursor: pointer; }
.section-tabs button.active { color: var(--blue); }
.section-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 3px; content: ''; background: var(--blue); border-radius: 3px 3px 0 0; }
.section-tabs button span { margin-left: 4px; padding: 1px 6px; color: inherit; font-size: 10px; background: #eef2f7; border-radius: 8px; }
.section-tabs button.active span { background: #eaf1ff; }

/* ---- 表格 ---- */
.table-wrap { width: 100%; overflow-x: auto; }
.table-wrap :deep(.el-table) { min-width: 800px; --el-table-border-color: #edf0f4; --el-table-header-bg-color: #fafbfc; --el-table-row-hover-bg-color: #f8faff; color: #344054; font-size: 12px; }
.table-wrap :deep(.el-table th.el-table__cell) { height: 47px; color: #657188; font-weight: 650; background: #fafbfc; }
.table-wrap :deep(.el-table td.el-table__cell) { height: 56px; }

.employee-cell { display: flex; align-items: center; gap: 10px; }
.employee-avatar { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: #2868e8; font-weight: 700; background: #edf3ff; border-radius: 9px; }
.employee-cell b { display: block; margin-bottom: 2px; color: #26354d; font-size: 13px; }
.employee-cell small { display: block; color: #929baa; font-size: 10px; }

.time-cell { color: #647086; font-variant-numeric: tabular-nums; white-space: nowrap; }
.channel-cell { display: flex; flex-wrap: wrap; gap: 4px; }
.channel-cell span { padding: 3px 6px; color: #58708e; font-size: 10px; background: #f1f5f9; border-radius: 4px; white-space: nowrap; }

.status-tag { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; font-size: 11px; font-weight: 600; border-radius: 5px; white-space: nowrap; }
.status-tag::before { width: 5px; height: 5px; content: ''; border-radius: 50%; }
.status-tag.active-tag { color: #21845a; background: #ecf9f2; border: 1px solid #cceedd; }
.status-tag.active-tag::before { background: #2aa870; }
.status-tag.cancelled { color: #a0a8b5; background: #f3f4f6; border: 1px solid #e5e7eb; }
.status-tag.cancelled::before { background: #9ca3af; }

.link-button { padding: 3px 5px; color: var(--blue); font-size: 12px; background: transparent; border: 0; cursor: pointer; }
.link-button:hover { text-decoration: underline; }
.link-button.danger { color: #e04b45; }
.link-button.danger:hover { color: #c0392b; }

.table-footer { display: flex; min-height: 58px; align-items: center; justify-content: space-between; padding: 10px 20px; color: #7e899a; font-size: 11px; border-top: 1px solid #edf0f4; }

/* ---- 弹窗 ---- */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,.45);
  backdrop-filter: blur(3px);
}
.modal-card {
  width: min(440px, calc(100vw - 32px));
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 18px 48px rgba(15,25,50,.22);
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 19px 20px 14px;
  border-bottom: 1px solid #edf0f4;
}
.modal-header h3 { margin: 0; font-size: 16px; font-weight: 700; color: #162238; }
.notice-close { color: #9ca5b2; font-size: 22px; background: transparent; border: 0; cursor: pointer; }
.notice-close:hover { color: #606b7d; }

.deactivate-body { display: flex; gap: 12px; align-items: flex-start; padding: 6px 0 14px; }
.warn-icon { flex: 0 0 20px; color: #e87935; }
.deactivate-text { margin: 0; font-size: 13px; line-height: 1.7; color: #3a4558; }

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #566079;
  user-select: none;
}
.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #e04b45;
  cursor: pointer;
}

/* ---- 响应式 ---- */
@media (max-width: 1180px) {
  .stats-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
}
@media (max-width: 780px) {
  .sidebar { width: 72px; flex-basis: 72px; }
  .sidebar .brand-name, .sidebar .nav-item span, .sidebar .nav-badge { display: none; }
  .topbar { padding: 0 16px; }
  .content { padding: 18px 14px 28px; }
  .admin-name { display: none; }
  .stats-grid { grid-template-columns: 1fr; }
}
</style>
