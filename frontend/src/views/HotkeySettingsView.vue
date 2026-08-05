<template>
  <div class="hotkey-settings" v-loading="loading">
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-slate-900">自定义热键</h2>
      <p class="text-sm text-slate-500 mt-1">管理并自定义您的快捷操作热键（与服务端同步）。</p>
    </div>

    <el-alert type="info" :closable="false" show-icon class="mb-5 !rounded-xl">
      <template #title>
        <span class="text-sm">点击下拉框后，请选择自己需要的热键</span>
      </template>
    </el-alert>

    <div class="bg-white border border-slate-200 rounded-2xl overflow-hidden">
      <el-table :data="tableRows" class="w-full" :header-cell-style="headerStyle">
        <el-table-column prop="name" label="操作名称" min-width="180">
          <template #default="{ row }">
            <div>
              <div class="font-medium text-slate-800">{{ row.name }}</div>
              <div v-if="row.description" class="text-xs text-slate-400 mt-0.5">
                {{ row.description }}
              </div>
              <div class="text-[11px] text-slate-300 mt-0.5 font-mono">
                funcId={{ row.funcId }} · {{ row.funcCode }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="当前热键" width="300" align="right">
          <template #default="{ row }">
            <el-select
              :model-value="row.keyId != null ? String(row.keyId) : ''"
              placeholder="选择热键"
              filterable
              clearable
              style="width: 220px"
              :disabled="savingFuncId === row.funcId"
              @change="(val: string) => onChange(row.funcId, val)"
            >
              <el-option
                v-for="opt in options"
                :key="opt.keyId"
                :label="opt.label"
                :value="String(opt.keyId)"
                :disabled="isOccupied(opt.keyId, row.funcId)"
              >
                <div class="flex justify-between items-center w-full gap-2">
                  <span>{{ opt.label }}</span>
                  <el-tag
                    v-if="isOccupied(opt.keyId, row.funcId)"
                    size="small"
                    type="info"
                    effect="plain"
                  >已占用</el-tag>
                </div>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="mt-6 flex items-center justify-between">
      <el-button :loading="resetting" @click="handleResetAll">清除全部绑定</el-button>
      <div class="text-xs text-slate-400">
        修改后立即同步服务端 ·
        <el-button text type="primary" size="small" @click="reload">刷新</el-button>
      </div>
    </div>

    <el-dialog v-model="conflictVisible" title="热键冲突" width="400px" align-center>
      <p class="text-slate-600">{{ conflictMsg }}</p>
      <template #footer>
        <el-button type="primary" @click="conflictVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useHotkeys } from '@/composables/useHotkeys'

const {
  rows,
  options,
  loading,
  load,
  updateBinding,
  resetAll
} = useHotkeys()

const tableRows = computed(() => [...rows.value])
const savingFuncId = ref<number | null>(null)
const resetting = ref(false)
const conflictVisible = ref(false)
const conflictMsg = ref('')

const headerStyle = {
  background: '#f8fafc',
  color: '#64748b',
  fontWeight: '600',
  fontSize: '13px'
}

function isOccupied(keyId: number, currentFuncId: number): boolean {
  return rows.value.some(
    r => r.funcId !== currentFuncId && r.keyId === keyId
  )
}

async function onChange(funcId: number, val: string) {
  const keyId = val === '' || val == null ? null : Number(val)
  if (keyId != null && !Number.isFinite(keyId)) return

  savingFuncId.value = funcId
  try {
    const result = await updateBinding(funcId, keyId)
    if (!result.ok) {
      conflictMsg.value = result.message
      conflictVisible.value = true
      return
    }
    ElMessage.success(result.message)
  } finally {
    savingFuncId.value = null
  }
}

async function handleResetAll() {
  try {
    await ElMessageBox.confirm(
      '将删除服务端全部热键绑定，各功能回退为系统默认。确定继续？',
      '清除全部绑定',
      { type: 'warning', confirmButtonText: '确定清除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  resetting.value = true
  try {
    const result = await resetAll()
    if (result.ok) ElMessage.success(result.message)
    else ElMessage.warning(result.message)
  } finally {
    resetting.value = false
  }
}

async function reload() {
  try {
    await load()
    ElMessage.success('已刷新')
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败，请确认已登录')
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载热键配置失败，请确认已登录')
  }
})
</script>

<style scoped>
.hotkey-settings :deep(.el-table) {
  --el-table-border-color: #f1f5f9;
}
.hotkey-settings :deep(.el-table th.el-table__cell) {
  border-bottom: 1px solid #e2e8f0;
}
.hotkey-settings :deep(.el-select .el-input__wrapper) {
  border-radius: 10px;
}
</style>
