<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Bell,
  Clock,
  DataBoard,
  Document,
  Histogram,
  HomeFilled,
  Lock,
  Search,
  Setting,
  Upload,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http, { getErrorMessage } from '../api/http'

// ========== API data types ==========
interface CensorKeywordDTO {
  id: number
  keyword: string
  enabled: boolean
  createdAt: string
}

// ========== Reactive data ==========
const keywords = ref<CensorKeywordDTO[]>([])
const loading = ref(false)

// Load from API
async function loadKeywords() {
  loading.value = true
  try {
    const res = await http.get('/v1/admin/keywords')
    const data = res.data
    if (data && data.items) {
      keywords.value = data.items as CensorKeywordDTO[]
    }
  } catch (e) {
    ElMessage.error(getErrorMessage(e, 'Failed to load keywords'))
  } finally {
    loading.value = false
  }
}

// ========== Filters ==========
const searchKeyword = ref('')
const filterCategory = ref('')
const filterRiskLevel = ref('')
const filterStatus = ref('')

const categories = ['政治敏感', '暴力恐怖', '色情低俗', '垃圾广告', '人身攻击', '金融诈骗', '其他违规']
const riskLevels = ['高风险', '中风险', '低风险']
const statuses = ['启用', '停用']

// ========== Selection ==========
const selectedIds = ref<Set<number>>(new Set())
const isAllSelected = computed(() => {
  return filteredWords.value.length > 0 && filteredWords.value.every(w => selectedIds.value.has(w.id))
})

function toggleAll() {
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(filteredWords.value.map(w => w.id))
  }
}

function toggleOne(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}


// ========== Filtered data ==========
const filteredWords = computed(() => {
  return keywords.value.filter(w => {
    if (searchKeyword.value && !w.keyword.includes(searchKeyword.value)) return false
    return true
  })
})

// ========== Pagination ==========
const currentPage = ref(1)
const pageSize = ref(8)
const totalCount = computed(() => filteredWords.value.length)
const pagedWords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredWords.value.slice(start, start + pageSize.value)
})

function handlePageChange(page: number) {
  currentPage.value = page
}

// ========== Dashboard stats ==========
const totalWords = computed(() => keywords.value.length)
const todayTriggers = computed(() => 0)
const highRiskCount = computed(() => 0)
const lastUpdateTime = ref('...')

// ========== Sidebar nav ==========
const sidebarCollapsed = ref(false)

// ========== Risk level badge ==========
function riskClass(level: string) {
  if (level === '高风险') return 'bg-red-50 text-red-600 border-red-200'
  if (level === '中风险') return 'bg-orange-50 text-orange-600 border-orange-200'
  return 'bg-blue-50 text-blue-600 border-blue-200'
}

// ========== Status display ==========
function statusText(enabled: boolean) {
  return enabled ? '启用' : '停用'
}

// ========== Actions ==========
async function toggleStatus(word: CensorKeywordDTO) {
  try {
    if (word.enabled) {
      await http.put(`/v1/admin/keywords/${word.id}/disable`)
      ElMessage.success('已停用')
    } else {
      await http.put(`/v1/admin/keywords/${word.id}/enable`)
      ElMessage.success('已启用')
    }
    await loadKeywords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  }
}

async function batchEnable() {
  try {
    for (const id of selectedIds.value) {
      await http.put(`/v1/admin/keywords/${id}/enable`)
    }
    ElMessage.success('已批量启用')
    selectedIds.value = new Set()
    await loadKeywords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  }
}

async function batchDisable() {
  try {
    for (const id of selectedIds.value) {
      await http.put(`/v1/admin/keywords/${id}/disable`)
    }
    ElMessage.success('已批量停用')
    selectedIds.value = new Set()
    await loadKeywords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '操作失败'))
  }
}

async function deleteWord(word: CensorKeywordDTO) {
  try {
    await http.delete(`/v1/admin/keywords/${word.id}`)
    ElMessage.success('已删除')
    await loadKeywords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '删除失败'))
  }
}

// ========== Add dialog ==========
const addDialogVisible = ref(false)
const newKeyword = ref('')
async function addWord() {
  if (!newKeyword.value.trim()) return
  try {
    await http.post('/v1/admin/keywords', { keyword: newKeyword.value.trim() })
    ElMessage.success('已添加')
    addDialogVisible.value = false
    newKeyword.value = ''
    await loadKeywords()
  } catch (e) {
    ElMessage.error(getErrorMessage(e, '添加失败'))
  }
}

function importDict() {
  // placeholder
}

onMounted(() => {
  loadKeywords().then(() => {
    lastUpdateTime.value = new Date().toLocaleString('zh-CN')
  })
})
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-gray-100">
    <!-- ========== Sidebar ========== -->
    <aside
      class="flex shrink-0 flex-col border-r border-gray-200 bg-gray-900 text-gray-300 transition-all duration-300"
      :class="sidebarCollapsed ? 'w-16' : 'w-56'"
    >
      <!-- Logo -->
      <div class="flex h-14 items-center gap-3 border-b border-gray-800 px-4">
        <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white font-bold text-sm">
          企
        </div>
        <span v-show="!sidebarCollapsed" class="text-sm font-semibold text-white">管理后台</span>
      </div>

      <!-- Nav items -->
      <nav class="flex-1 overflow-y-auto px-2 py-3">
        <router-link
          to="/"
          class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 hover:text-white no-underline"
        >
          <el-icon :size="16"><HomeFilled /></el-icon>
          <span v-show="!sidebarCollapsed" class="truncate">首页</span>
        </router-link>
        <router-link
          to="/admin/sensitive-words"
          class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-blue-400 bg-blue-900/40 transition no-underline"
        >
          <el-icon :size="16"><DataBoard /></el-icon>
          <span v-show="!sidebarCollapsed" class="truncate font-medium">敏感词概览</span>
        </router-link>
        <router-link
          to="/admin/sensitive-words"
          class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 hover:text-white no-underline"
        >
          <el-icon :size="16"><Lock /></el-icon>
          <span v-show="!sidebarCollapsed" class="truncate">敏感词管理</span>
        </router-link>
        <router-link
          to="/admin/security-logs"
          class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 hover:text-white no-underline"
        >
          <el-icon :size="16"><Document /></el-icon>
          <span v-show="!sidebarCollapsed" class="truncate">触发日志</span>
        </router-link>
        <button
          class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-gray-800 hover:text-white"
        >
          <el-icon :size="16"><Setting /></el-icon>
          <span v-show="!sidebarCollapsed" class="truncate">系统设置</span>
        </button>
      </nav>

      <!-- Collapse toggle -->
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
      <!-- Top bar -->
      <header class="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
        <div class="flex items-center gap-3">
          <h1 class="text-base font-semibold text-gray-800">敏感词监管</h1>
          <span class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">管理后台</span>
        </div>
        <div class="flex items-center gap-3">
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100"
            title="通知"
          >
            <el-icon :size="16"><Bell /></el-icon>
          </button>
          <div class="flex items-center gap-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
              管
            </div>
            <span class="hidden text-sm text-gray-600 sm:inline">管理员</span>
            <svg class="h-3 w-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </header>

      <!-- Scrollable content area -->
      <div class="flex-1 overflow-y-auto p-5 sm:p-6">
        <!-- Dashboard cards -->
        <div class="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <!-- 敏感词总数 -->
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-100 text-blue-600">
              <el-icon :size="20"><Lock /></el-icon>
            </div>
            <div class="min-w-0">
              <p class="text-xl font-bold text-gray-800">{{ totalWords }}</p>
              <p class="truncate text-xs text-gray-500">敏感词总数</p>
            </div>
          </div>

          <!-- 今日触发次数 -->
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-green-100 text-green-600">
              <el-icon :size="20"><Histogram /></el-icon>
            </div>
            <div class="min-w-0">
              <p class="text-xl font-bold text-gray-800">{{ todayTriggers.toLocaleString() }}</p>
              <p class="truncate text-xs text-gray-500">今日触发次数</p>
            </div>
          </div>

          <!-- 高风险词数量 -->
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-orange-100 text-orange-600">
              <el-icon :size="20"><Warning /></el-icon>
            </div>
            <div class="min-w-0">
              <p class="text-xl font-bold text-gray-800">{{ highRiskCount }}</p>
              <p class="truncate text-xs text-gray-500">高风险词数量</p>
            </div>
          </div>

          <!-- 最近更新时间 -->
          <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-gray-100 text-gray-500">
              <el-icon :size="20"><Clock /></el-icon>
            </div>
            <div class="min-w-0">
              <p class="text-sm font-semibold text-gray-700">{{ lastUpdateTime }}</p>
              <p class="truncate text-xs text-gray-500">最近更新时间</p>
            </div>
          </div>
        </div>

        <!-- Filter toolbar -->
        <div class="mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm">
          <!-- Left: filters -->
          <div class="flex flex-1 flex-wrap items-center gap-3 min-w-0">
            <div class="relative w-48">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索敏感词..."
                :prefix-icon="Search"
                size="small"
                clearable
              />
            </div>
          </div>

          <!-- Right: action buttons -->
          <div class="flex items-center gap-2">
            <button
              class="flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-medium text-white transition hover:bg-blue-700 shadow-sm"
              @click="addDialogVisible = true"
            >
              <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4" />
              </svg>
              新增敏感词
            </button>
            <button
              class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs text-gray-600 transition hover:bg-gray-50 hover:text-blue-600"
              @click="batchEnable"
            >
              批量启用
            </button>
            <button
              class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs text-gray-600 transition hover:bg-gray-50 hover:text-orange-600"
              @click="batchDisable"
            >
              批量停用
            </button>
            <button
              class="flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-xs text-gray-600 transition hover:bg-gray-50"
              @click="importDict"
            >
              <el-icon :size="14"><Upload /></el-icon>
              导入词库
            </button>
          </div>
        </div>

        <!-- Data Table -->
        <div class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-gray-100 bg-gray-50">
              <tr>
                <th class="w-10 px-4 py-3">
                  <el-checkbox
                    :model-value="isAllSelected"
                    :indeterminate="selectedIds.size > 0 && !isAllSelected"
                    size="small"
                    @change="toggleAll"
                  />
                </th>
                <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">敏感词</th>
                <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">状态</th>
                <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">创建时间</th>
                <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr
                v-for="word in pagedWords"
                :key="word.id"
                class="transition hover:bg-gray-50"
              >
                <td class="px-4 py-3">
                  <el-checkbox
                    :model-value="selectedIds.has(word.id)"
                    size="small"
                    @change="toggleOne(word.id)"
                  />
                </td>
                <td class="px-4 py-3">
                  <p class="font-medium text-gray-800">{{ word.keyword }}</p>
                </td>
                <td class="px-4 py-3">
                  <span
                    class="text-xs"
                    :class="word.enabled ? 'text-green-600' : 'text-gray-400'"
                  >
                    {{ statusText(word.enabled) }}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <span class="text-xs text-gray-500">{{ word.createdAt }}</span>
                </td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2 text-xs">
                    <button
                      class="transition hover:underline"
                      :class="word.enabled ? 'text-orange-500 hover:text-orange-700' : 'text-green-500 hover:text-green-700'"
                      @click="toggleStatus(word)"
                    >
                      {{ word.enabled ? '停用' : '启用' }}
                    </button>
                    <button
                      class="text-red-400 transition hover:text-red-600 hover:underline"
                      @click="deleteWord(word)"
                    >
                      删除
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="pagedWords.length === 0 && !loading">
                <td colspan="5" class="px-4 py-12 text-center text-sm text-gray-400">
                  暂无数据
                </td>
              </tr>
              <tr v-if="loading">
                <td colspan="5" class="px-4 py-8 text-center text-sm text-gray-400">
                  加载中...
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Pagination -->
          <div class="flex items-center justify-between border-t border-gray-100 px-4 py-3">
            <span class="text-xs text-gray-500">共 {{ totalCount }} 条记录</span>
            <el-pagination
              :current-page="currentPage"
              :page-size="pageSize"
              :total="totalCount"
              :pager-count="5"
              layout="prev, pager, next"
              small
              background
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Add Keyword Dialog -->
    <el-dialog v-model="addDialogVisible" title="新增敏感词" width="400px" :close-on-click-modal="false">
      <el-input v-model="newKeyword" placeholder="输入敏感词" />
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addWord">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>