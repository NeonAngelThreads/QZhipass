<template>
  <div class="page-container">
    <div class="page-header">
      <h1>安全日志</h1>
      <div class="header-actions">
        <button class="btn-secondary" @click="$router.push('/chat')">返回对话</button>
      </div>
    </div>

    <div class="search-bar">
      <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索用户名、手机号、敏感词..."
          @keyup.enter="fetchRecords(0)"
      />
      <button class="btn-primary" @click="fetchRecords(0)">搜索</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <table class="data-table">
        <thead>
        <tr>
          <th>ID</th>
          <th>用户ID</th>
          <th>用户名</th>
          <th>手机号</th>
          <th>部门</th>
          <th>模型</th>
          <th>命中敏感词</th>
          <th>时间</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="record in records" :key="record.id">
          <td>{{ record.id }}</td>
          <td>{{ record.userId }}</td>
          <td>{{ record.username || '-' }}</td>
          <td>{{ record.phone || '-' }}</td>
          <td>{{ record.department || '-' }}</td>
          <td>{{ record.modelName || '-' }}</td>
          <td>
              <span class="keyword-tag" v-for="keyword in splitKeywords(record.hitKeywords)" :key="keyword">
                {{ keyword }}
              </span>
          </td>
          <td>{{ formatTime(record.createdAt) }}</td>
        </tr>
        <tr v-if="records.length === 0">
          <td colspan="8" class="empty-row">暂无记录</td>
        </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="totalPages > 1">
        <button
            :disabled="currentPage === 0"
            @click="fetchRecords(currentPage - 1)"
            class="btn-secondary"
        >
          上一页
        </button>
        <span>第 {{ currentPage + 1 }} / {{ totalPages }} 页（共 {{ totalElements }} 条）</span>
        <button
            :disabled="currentPage >= totalPages - 1"
            @click="fetchRecords(currentPage + 1)"
            class="btn-secondary"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'

interface CensorRecord {
  id: number
  userId: number
  username: string | null
  phone: string | null
  department: string | null
  modelName: string | null
  hitKeywords: string | null
  createdAt: string
}

const records = ref<CensorRecord[]>([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

function splitKeywords(hitKeywords: string | null): string[] {
  if (!hitKeywords) return []
  return hitKeywords.split(',').map(k => k.trim()).filter(Boolean)
}

function formatTime(raw: string): string {
  if (!raw) return '-'
  try {
    return new Date(raw).toLocaleString('zh-CN')
  } catch {
    return raw
  }
}

async function fetchRecords(page: number) {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    if (searchQuery.value.trim()) {
      params.set('q', searchQuery.value.trim())
    }
    params.set('page', String(page))
    params.set('size', '20')

    const resp = await fetch(`/api/admin/security-logs?${params.toString()}`)
    if (!resp.ok) {
      if (resp.status === 403) {
        error.value = '无权限访问，仅管理员可查看。'
        return
      }
      throw new Error(`请求失败 (${resp.status})`)
    }
    const json = await resp.json()
    const data = json.data || json.payload
    records.value = data?.content ?? []
    totalPages.value = data?.totalPages ?? 1
    totalElements.value = data?.totalElements ?? 0
    currentPage.value = data?.number ?? 0
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchRecords(0)
})
</script>

<style scoped>
.page-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.search-bar input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  font-size: 14px;
}

.search-bar input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.btn-primary {
  padding: 8px 20px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.btn-primary:hover {
  background: #2563eb;
}

.btn-secondary {
  padding: 8px 16px;
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.btn-secondary:hover {
  background: #e5e7eb;
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading,
.error {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}

.error {
  color: #ef4444;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
  font-size: 13px;
}

.data-table th {
  background: #f9fafb;
  color: #374151;
  font-weight: 600;
  white-space: nowrap;
}

.data-table tbody tr:hover {
  background: #f9fafb;
}

.keyword-tag {
  display: inline-block;
  background: #fee2e2;
  color: #dc2626;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-right: 4px;
  margin-bottom: 2px;
}

.empty-row {
  text-align: center;
  color: #9ca3af;
  padding: 32px !important;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  font-size: 14px;
  color: #6b7280;
}
</style>