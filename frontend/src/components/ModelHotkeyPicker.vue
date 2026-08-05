<template>
  <!--
    大模型热键选择器
    - 数据来自 GET /api/v1/models/available（ModelController）
    - 由自定义热键「大模型列表」(默认 #) 唤起
  -->
  <div class="model-hotkey-picker relative inline-block">
    <button
      type="button"
      class="model-trigger"
      @click.stop="toggle"
    >
      {{ currentLabel }}
      <el-icon class="ml-1" :size="12"><ArrowDown /></el-icon>
    </button>

    <transition name="fade">
      <div
        v-if="visible"
        class="model-dropdown"
        @click.stop
      >
        <div class="dropdown-head">
          <span>选择模型</span>
          <el-button text size="small" :loading="loading" @click="refresh">
            刷新
          </el-button>
        </div>

        <div v-if="error" class="dropdown-error">{{ error }}</div>
        <div v-else-if="loading && models.length === 0" class="dropdown-empty">加载中…</div>
        <div v-else-if="models.length === 0" class="dropdown-empty">暂无可用模型</div>

        <ul v-else class="model-list">
          <li
            v-for="m in models"
            :key="keyOf(m)"
            class="model-item"
            :class="{ active: keyOf(m) === modelValue }"
            @click="select(m)"
          >
            <span class="label">{{ labelOf(m) }}</span>
            <el-icon v-if="keyOf(m) === modelValue" class="check" :size="14"><Check /></el-icon>
          </li>
        </ul>

        <div class="dropdown-foot">
          <kbd>{{ hotkeyLabel }}</kbd>
          <span>唤起 / 关闭</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDown, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  listAvailableModels,
  modelKeyOf,
  modelLabelOf,
  type ModelResponse
} from '@/api/models'
import { useHotkeys, displayKey } from '@/composables/useHotkeys'

const props = defineProps<{
  /** 当前选中的 modelKey */
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [model: ModelResponse]
}>()

const { matchEvent, getHotkeyByCode, load: loadHotkeys, loaded } = useHotkeys()

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const models = ref<ModelResponse[]>([])

const hotkeyLabel = computed(() =>
  displayKey(getHotkeyByCode('model.list') || '#')
)

const currentLabel = computed(() => {
  const found = models.value.find(m => modelKeyOf(m) === props.modelValue)
  return found ? modelLabelOf(found) : props.modelValue || '选择模型'
})

function keyOf(m: ModelResponse) {
  return modelKeyOf(m)
}
function labelOf(m: ModelResponse) {
  return modelLabelOf(m)
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    models.value = await listAvailableModels()
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '获取模型失败'
    models.value = []
  } finally {
    loading.value = false
  }
}

function toggle() {
  visible.value = !visible.value
  if (visible.value && models.value.length === 0) {
    void refresh()
  }
}

function open() {
  visible.value = true
  if (models.value.length === 0) void refresh()
}

function close() {
  visible.value = false
}

function select(m: ModelResponse) {
  const key = modelKeyOf(m)
  emit('update:modelValue', key)
  emit('change', m)
  visible.value = false
  ElMessage.success(`已切换至 ${modelLabelOf(m)}`)
}

function onKeydown(e: KeyboardEvent) {
  // 自定义热键：打开/关闭大模型列表（func: model.list）
  if (matchEvent(e, 'model.list') || matchEvent(e, 'model-list')) {
    e.preventDefault()
    if (visible.value) close()
    else open()
    return
  }
  // 关闭列表热键（func: agent.list.close）
  if (
    visible.value &&
    (matchEvent(e, 'agent.list.close') || matchEvent(e, 'close-agent-list'))
  ) {
    e.preventDefault()
    close()
  }
}

function onDocClick() {
  if (visible.value) close()
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', onDocClick)
  // 预拉一次，便于展示当前模型名
  void refresh()
  if (!loaded.value) {
    void loadHotkeys().catch(() => {
      /* 未登录时仍可用默认 # */
    })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocClick)
})

watch(
  () => props.modelValue,
  () => {
    /* label 由 computed 自动更新 */
  }
)

defineExpose({ open, close, refresh, toggle })
</script>

<style scoped>
.model-trigger {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.model-trigger:hover {
  text-decoration: underline;
  background: #eff6ff;
}

.model-dropdown {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 6px;
  width: 240px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
  z-index: 50;
  overflow: hidden;
}

.dropdown-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #f3f4f6;
}

.dropdown-error {
  padding: 16px 12px;
  font-size: 12px;
  color: #ef4444;
}

.dropdown-empty {
  padding: 20px 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

.model-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  max-height: 260px;
  overflow-y: auto;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
}
.model-item:hover {
  background: #eff6ff;
}
.model-item.active {
  color: #2563eb;
  font-weight: 600;
  background: #eff6ff;
}
.model-item .check {
  color: #2563eb;
}

.dropdown-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-top: 1px solid #f3f4f6;
  font-size: 11px;
  color: #9ca3af;
}
.dropdown-foot kbd {
  display: inline-block;
  padding: 1px 6px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #f9fafb;
  font-size: 10px;
  font-weight: 600;
  color: #6b7280;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
