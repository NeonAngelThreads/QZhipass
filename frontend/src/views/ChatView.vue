<script setup lang="ts">
import {type Component, computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useRouter} from 'vue-router'
import BrandLogo from '../components/BrandLogo.vue'
import http, {getErrorMessage} from '../api/http'
import {checkAgentDeletion, deleteAgent} from '../api/agent'
import {changePassword} from '../api/auth'
import {readLoginInfo, saveInitialConversationId} from '../api/session'
import {useAuthStore} from '../stores/auth'

import {
  Bell,
  ChatDotSquare,
  Document,
  EditPen,
  Headset,
  Hide,
  Lock,
  Paperclip,
  Promotion,
  Search,
  Setting,
  SwitchButton,
  UserFilled,
  View,
} from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const PASSWORD_POLICY_MESSAGE = '密码必须包含大写字母、小写字母、特殊字符、数字，长度至少为8个字符'
const PASSWORD_SAME_MESSAGE = '新密码不能与原密码相同，请重试'
const PASSWORD_MISMATCH_MESSAGE = '两次输入的新密码不一致'

interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

type PasswordField = keyof ChangePasswordForm
type PasswordFieldState<T> = Record<PasswordField, T>

const PASSWORD_ERROR_FIELD_BY_MESSAGE: Record<string, PasswordField> = {
  原密码不能为空: 'oldPassword',
  原密码错误: 'oldPassword',
  新密码不能为空: 'newPassword',
  [PASSWORD_POLICY_MESSAGE]: 'newPassword',
  [PASSWORD_SAME_MESSAGE]: 'newPassword',
  确认新密码不能为空: 'confirmPassword',
  [PASSWORD_MISMATCH_MESSAGE]: 'confirmPassword'
}

const PASSWORD_FIELDS = [
  {
    key: 'oldPassword',
    label: '原密码',
    autocomplete: 'current-password',
    inputId: 'change-password-old',
    inputTestId: 'old-password-input',
    toggleTestId: 'toggle-old-password'
  },
  {
    key: 'newPassword',
    label: '新密码',
    autocomplete: 'new-password',
    inputId: 'change-password-new',
    inputTestId: 'new-password-input',
    toggleTestId: 'toggle-new-password'
  },
  {
    key: 'confirmPassword',
    label: '确认新密码',
    autocomplete: 'new-password',
    inputId: 'change-password-confirm',
    inputTestId: 'confirm-password-input',
    toggleTestId: 'toggle-confirm-password'
  }
] as const satisfies ReadonlyArray<{
  key: PasswordField
  label: string
  autocomplete: string
  inputId: string
  inputTestId: string
  toggleTestId: string
}>

// ========== state ==========
const searchQuery = ref('')
const inputText = ref('')
const selectedModel = ref('gpt4-omni')
const selectedAgent = ref('data-analyst')

const selectedChatId = ref<number | null>(null)
const showModelDropdown = ref(false)
const showAgentDropdown = ref(false)
const creatingConversation = ref(false)
const accountSettingsVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordRequestError = ref('')
const passwordForm = reactive<ChangePasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordVisibility = reactive<PasswordFieldState<boolean>>({
  oldPassword: false,
  newPassword: false,
  confirmPassword: false
})
const passwordTouched = reactive<PasswordFieldState<boolean>>({
  oldPassword: false,
  newPassword: false,
  confirmPassword: false
})
const passwordServerErrors = reactive<PasswordFieldState<string>>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const tokenLimit = 100000
const tokenUsed = 64000
const tokenPercent = computed(() => Math.round((tokenUsed / tokenLimit) * 100))

const models = [
  { value: 'gpt4-omni', label: 'GPT-4 Omni' },
  { value: 'gpt4-turbo', label: 'GPT-4 Turbo' },
  { value: 'claude-3.5', label: 'Claude 3.5 Sonnet' },
  { value: 'qwen3', label: '千问3' },
  { value: 'deepseek-v4', label: 'DeepSeek-V4' },
]

const agents = ref([
  { value: 'data-analyst', label: 'Data Analyst Agent' },
  { value: 'copywriter', label: 'Copywriter Agent' },
  { value: 'coder', label: 'Code Assistant Agent' },
])
const deletingAgentId = ref<string | null>(null)
const blockedAgentMessages = new Map<string, string>()

const chats = [
  { id: 1, title: 'Q4 数据分析报告撰写', icon: Document },
  { id: 2, title: '品牌营销文案优化', icon: Promotion },
  { id: 3, title: '产品需求文档梳理', icon: EditPen },
  { id: 4, title: '用户反馈情绪分析', icon: ChatDotSquare },
  { id: 5, title: '竞品市场调研总结', icon: Search },
]
interface ApiResponse<T> {
  success?: boolean
  message?: string
  data?: T
}

interface ConversationPayload {
  id: number
  title?: string
  modelKey?: string | null
}

interface ChatItem {
  id: number
  title: string
  icon: Component
}

interface CreateConversationOptions {
  silent?: boolean
  persistAsInitial?: boolean
}


interface Message {
  id: number
  role: 'user' | 'ai'
  content: string
  timestamp: string
  actions?: string[]
}
const messages = ref<Message[]>([
  {
    id: 1,
    role: 'user',
    content: '请帮我分析 Q4 销售数据，生成一份综合报告，包含趋势图和关键指标。',
    timestamp: '10:28 AM',
  },
  {
    id: 2,
    role: 'ai',
    content:
      '好的，我已经完成了 **Q4 销售数据的分析**。以下是主要发现：\n\n1. **总销售额**：¥8,420万，同比增长 12.4%\n2. **线上渠道占比**：首次突破 45%\n3. **华东地区** 增长最快，达到 18.7%\n4. **客单价** 提升至 ¥2,840（+5.2%）\n\n建议重点关注以下数据维度进行深入分析。',
    timestamp: '10:28 AM',
    actions: ['生成柱状图', '导出 PPT 提纲', '查看原始数据'],
  },
  {
    id: 3,
    role: 'user',
    content: '好的，请帮我生成趋势图和导出 PPT 提纲。另外把华东地区的细节数据给我看看。',
    timestamp: '10:35 AM',
  },
  {
    id: 4,
    role: 'ai',
    content:
      '已为您生成趋势图并导出 PPT 提纲。\n\n### 📊 趋势图已生成\n- **月度销售趋势图**：显示 10-12 月逐月增长\n- **渠道分布饼图**：线上 45%、线下 55%\n- **区域对比柱状图**：华东领跑\n\n### 📄 PPT 提纲\n1. Q4 整体业绩概览\n2. 各渠道销售表现\n3. 区域市场分析\n4. 产品品类 TOP 10\n5. 2025 Q1 展望\n\n华东地区详细数据已整理如下表...',
    timestamp: '10:35 AM',
    actions: ['下载 PPT', '分享报告'],
  },
  {
    id: 5,
    role: 'user',
    content: '非常好，请帮我把这个报告分享给管理层，并添加一段简短的总结。',
    timestamp: '10:42 AM',
  },
  {
    id: 6,
    role: 'ai',
    content:
      '报告已准备完毕，分享链接已生成。\n\n### 📋 执行摘要\n\nQ4 业绩表现强劲，总销售额达 ¥8,420 万，同比增长 12.4%。线上渠道贡献显著提升，华东市场持续引领增长。建议 Q1 重点加大线上投入，并借鉴华东成功经验推广至其他区域。\n\n已为您生成分享链接，有效期 7 天。',
    timestamp: '10:42 AM',
    actions: ['复制分享链接', '预览报告'],
  },
])


const chatContainer = ref<HTMLElement>()

const currentChat = computed(() => chats.find(c => c.id === selectedChatId.value))
const charCount = computed(() => inputText.value.length)
const maxChars = 2000

const passwordErrors = computed<PasswordFieldState<string>>(() => ({
  oldPassword: passwordTouched.oldPassword
    ? validateOldPassword() || passwordServerErrors.oldPassword
    : '',
  newPassword: passwordTouched.newPassword
    ? validateNewPassword() || passwordServerErrors.newPassword
    : '',
  confirmPassword: passwordTouched.confirmPassword
    ? validateConfirmPassword() || passwordServerErrors.confirmPassword
    : ''
}))

function isBlankPassword(password: string) {
  return password.trim().length === 0
}

function isStrongPassword(password: string) {
  return password.length >= 8
    && /[A-Z]/.test(password)
    && /[a-z]/.test(password)
    && /[0-9]/.test(password)
    && /[^\p{L}\p{N}\s]/u.test(password)
}

function validateOldPassword() {
  return isBlankPassword(passwordForm.oldPassword) ? '原密码不能为空' : ''
}

function validateNewPassword() {
  if (isBlankPassword(passwordForm.newPassword)) {
    return '新密码不能为空'
  }
  if (
    !isBlankPassword(passwordForm.oldPassword)
    && passwordForm.newPassword === passwordForm.oldPassword
  ) {
    return PASSWORD_SAME_MESSAGE
  }
  return isStrongPassword(passwordForm.newPassword) ? '' : PASSWORD_POLICY_MESSAGE
}

function validateConfirmPassword() {
  if (isBlankPassword(passwordForm.confirmPassword)) {
    return '确认新密码不能为空'
  }
  return passwordForm.confirmPassword === passwordForm.newPassword
    ? ''
    : PASSWORD_MISMATCH_MESSAGE
}

function clearPasswordServerErrors() {
  passwordServerErrors.oldPassword = ''
  passwordServerErrors.newPassword = ''
  passwordServerErrors.confirmPassword = ''
  passwordRequestError.value = ''
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisibility.oldPassword = false
  passwordVisibility.newPassword = false
  passwordVisibility.confirmPassword = false
  passwordTouched.oldPassword = false
  passwordTouched.newPassword = false
  passwordTouched.confirmPassword = false
  clearPasswordServerErrors()
}

function openAccountSettings() {
  resetPasswordForm()
  accountSettingsVisible.value = true
}

function handlePasswordInput(field: PasswordField) {
  passwordTouched[field] = true
  clearPasswordServerErrors()
}

function togglePasswordVisibility(field: PasswordField) {
  passwordVisibility[field] = !passwordVisibility[field]
}

function markAllPasswordFieldsTouched() {
  passwordTouched.oldPassword = true
  passwordTouched.newPassword = true
  passwordTouched.confirmPassword = true
}

function setPasswordRequestError(message: string) {
  const field = PASSWORD_ERROR_FIELD_BY_MESSAGE[message]
  if (field) {
    passwordServerErrors[field] = message
    return
  }
  passwordRequestError.value = message
}

async function handleChangePassword() {
  if (passwordSubmitting.value) {
    return
  }

  markAllPasswordFieldsTouched()
  clearPasswordServerErrors()
  if (validateOldPassword() || validateNewPassword() || validateConfirmPassword()) {
    return
  }

  passwordSubmitting.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    resetPasswordForm()
    ElMessage.success('修改成功')
  } catch (error) {
    setPasswordRequestError(error instanceof Error ? error.message : '修改密码失败')
  } finally {
    passwordSubmitting.value = false
  }
}

function selectChat(id: number) {
  selectedChatId.value = id
}

function activateConversation(conversation: ConversationPayload) {
  const title = conversation.title || '新建对话'
  const existing = chats.find(chat => chat.id === conversation.id)

  if (existing) {
    existing.title = title
  } else {
    chats.unshift({
      id: conversation.id,
      title,
      icon: ChatDotSquare
    })
  }

  selectedChatId.value = conversation.id
  messages.value = []
  inputText.value = ''
}

function initializeConversationFromLogin() {
  const loginInfo = readLoginInfo()
  if (!loginInfo?.initialConversationId) {
    void createNewConversation({ silent: true, persistAsInitial: true })
    return
  }

  activateConversation({
    id: loginInfo.initialConversationId,
    title: '新建对话'
  })
}

async function createNewConversation(options: CreateConversationOptions = {}) {
  if (creatingConversation.value) return

  creatingConversation.value = true
  try {
    const { data } = await http.post<ApiResponse<ConversationPayload>>('/v1/conversations', {
      modelKey: selectedModel.value
    })
    const conversation = data.data

    if (!conversation?.id) {
      throw new Error(data.message || '新建对话失败')
    }

    activateConversation(conversation)
    if (options.persistAsInitial) {
      saveInitialConversationId(conversation.id)
    }
    await nextTick(scrollToBottom)
    if (!options.silent) {
      ElMessage.success('已创建新对话')
    }
  } catch (error) {
    if (!options.silent) {
      ElMessage.error(getErrorMessage(error, '新建对话失败'))
    }
  } finally {
    creatingConversation.value = false
  }
}

function selectModel(val: string) {
  selectedModel.value = val
  showModelDropdown.value = false
}

function toggleModelDropdown() {
  showModelDropdown.value = !showModelDropdown.value
}

function handleGlobalKeydown(e: KeyboardEvent) {
  if (e.key === '#') {
    e.preventDefault()
    toggleModelDropdown()
  }
  if (e.key === 'Escape' && showModelDropdown.value) {
    showModelDropdown.value = false
  }
}

onMounted(() => {
  initializeConversationFromLogin()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})

function selectAgent(val: string) {
  selectedAgent.value = val
  showAgentDropdown.value = false
}

async function handleDeleteAgent(agent: {value: string; label: string}) {
  if (deletingAgentId.value) {
    return
  }

  const blockedMessage = blockedAgentMessages.get(agent.value)
  if (blockedMessage) {
    ElMessage.warning(blockedMessage)
    return
  }

  deletingAgentId.value = agent.value
  try {
    const check = await checkAgentDeletion(agent.value)
    if (!check.canDelete) {
      blockedAgentMessages.set(agent.value, check.message)
      ElMessage.warning(check.message)
      return
    }

    await ElMessageBox.confirm(
      `确定要删除“${agent.label}”吗？`,
      '删除 Agent',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const result = await deleteAgent(agent.value)
    agents.value = agents.value.filter(item => item.value !== agent.value)
    if (selectedAgent.value === agent.value) {
      selectedAgent.value = agents.value[0]?.value || ''
    }
    showAgentDropdown.value = false
    ElMessage.success(result.message)
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : 'Agent 删除失败')
  } finally {
    deletingAgentId.value = null
  }
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }),
  })
  inputText.value = ''
  nextTick(scrollToBottom)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

function logout() {
  authStore.logout()
  router.replace('/login')
}

function openAccountSettingsPage() {
  router.push('/account/settings')
}

watch(
  () => messages.value.length,
  () => nextTick(scrollToBottom),
)

const modelLabel = computed(() => models.find(m => m.value === selectedModel.value)?.label ?? '')
const agentLabel = computed(() => agents.value.find(a => a.value === selectedAgent.value)?.label ?? '')
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-white">
    <!-- ========== Sidebar ========== -->
    <aside class="flex w-72 shrink-0 flex-col border-r border-gray-200 bg-white">
      <!-- Logo area -->
      <div class="border-b border-gray-100 px-5 py-5">
        <BrandLogo tone="dark" size="md" />
        <p class="mt-1 text-xs text-gray-400">企业智能协作平台</p>
      </div>

      <!-- Token card -->
      <div class="mx-4 mt-4 rounded-xl bg-gradient-to-br from-blue-50 to-indigo-50 p-4">
        <div class="flex items-center justify-between text-xs text-gray-500">
          <span>Daily Token Limit</span>
          <span class="font-semibold text-gray-700">{{ tokenPercent }}%</span>
        </div>
        <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200">
          <div
            class="h-full rounded-full bg-gradient-to-r from-blue-500 to-indigo-600 transition-all duration-500"
            :style="{ width: tokenPercent + '%' }"
          ></div>
        </div>
        <p class="mt-2 text-xs text-gray-400">
          {{ tokenUsed.toLocaleString() }} / {{ tokenLimit.toLocaleString() }} tokens
        </p>
      </div>

      <!-- New chat button -->
      <div class="px-4 pt-4">
        <button
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="creatingConversation"
          @click="createNewConversation()"
        >
          <el-icon :size="16"><ChatDotSquare /></el-icon>
          {{ creatingConversation ? '创建中...' : '+ 开启新会话' }}
        </button>
      </div>

      <!-- Chat history -->
      <div class="mt-5 flex-1 overflow-y-auto px-3">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-400">对话历史</p>
        <ul class="space-y-0.5">
          <li v-for="chat in chats" :key="chat.id">
            <button
              class="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition"
              :class="
                selectedChatId === chat.id
                  ? 'bg-blue-50 text-blue-700 font-medium'
                  : 'text-gray-600 hover:bg-gray-50'
              "
              @click="selectChat(chat.id)"
            >
              <el-icon :size="16"><component :is="chat.icon" /></el-icon>
              <span class="truncate">{{ chat.title }}</span>
            </button>
          </li>
        </ul>
      </div>

      <!-- Bottom user area -->
      <div class="border-t border-gray-100 px-4 py-3">
        <div
          class="mb-2 flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-500 transition hover:bg-gray-50 cursor-pointer"
          @click="openAccountSettingsPage"
        >
          <el-icon :size="16"><Setting /></el-icon>
          <span>系统设置</span>
        </div>
        <div class="flex items-center gap-3">
          <div
            class="flex h-9 w-9 items-center justify-center rounded-full bg-blue-600 text-sm font-bold text-white"
          >
            张
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-800">张经理</p>
            <p class="truncate text-xs text-gray-400">企业管理员</p>
          </div>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-red-500"
            title="退出登录"
            @click="logout"
          >
            <el-icon :size="16"><SwitchButton /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <!-- ========== Main Content ========== -->
    <div class="flex flex-1 flex-col min-w-0">
      <!-- Top nav bar -->
      <header class="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div class="flex items-center gap-3 min-w-0">
          <h2 class="truncate text-base font-semibold text-gray-800">
            {{ currentChat?.title ?? '选择对话' }}
          </h2>
          <span
            class="shrink-0 rounded-full bg-purple-50 px-2.5 py-0.5 text-xs font-medium text-purple-600"
          >
            GPT-4 Omni
          </span>
        </div>
        <div class="flex items-center gap-2">
          <div class="relative hidden sm:block">
            <el-input
              v-model="searchQuery"
              placeholder="搜索对话内容..."
              :prefix-icon="Search"
              size="small"
              class="w-56"
            />
          </div>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            title="通知"
          >
            <el-icon :size="18"><Bell /></el-icon>
          </button>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            title="帮助"
          >
            <el-icon :size="18"><Headset /></el-icon>
          </button>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            title="用户设置"
            @click="openAccountSettings"
          >
            <el-icon :size="18"><UserFilled /></el-icon>
          </button>
        </div>
      </header>

      <!-- Chat area -->
      <div ref="chatContainer" class="flex-1 overflow-y-auto bg-gray-50 px-4 py-5 sm:px-8">
        <div class="mx-auto max-w-3xl space-y-5">
          <div v-for="msg in messages" :key="msg.id">
            <!-- Timestamp separator -->
            <div class="mb-4 text-center">
              <span class="inline-block rounded-full bg-gray-200 px-3 py-0.5 text-xs text-gray-500">
                {{ msg.timestamp }}
              </span>
            </div>

            <!-- User message (right-aligned) -->
            <div v-if="msg.role === 'user'" class="flex justify-end">
              <div class="max-w-[75%] rounded-2xl rounded-br-md bg-blue-50 px-4 py-2.5 text-sm text-gray-800 shadow-sm">
                {{ msg.content }}
              </div>
            </div>

            <!-- AI message (left-aligned) -->
            <div v-else class="flex gap-3">
              <!-- AI Avatar -->
              <div
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-500 to-indigo-600 text-xs font-bold text-white"
              >
                AI
              </div>
              <div class="min-w-0 max-w-[80%]">
                <!-- AI name -->
                <p class="mb-1 text-xs font-medium text-gray-500">Data Analyst Agent</p>
                <!-- AI bubble -->
                <div
                  class="rounded-2xl rounded-tl-sm bg-white px-4 py-3 text-sm text-gray-700 shadow-sm leading-relaxed"
                >
                  <!-- Basic markdown rendering -->
                  <div v-html="
                    msg.content
                      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                      .replace(/### (.*)/g, '<h4 class=\'text-base font-semibold mt-2 mb-1\'>$1</h4>')
                      .replace(/\n/g, '<br>')
                  "></div>
                </div>
                <!-- Action buttons -->
                <div v-if="msg.actions && msg.actions.length" class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="action in msg.actions"
                    :key="action"
                    class="rounded-full border border-gray-200 bg-white px-3 py-1 text-xs text-gray-600 transition hover:border-blue-300 hover:text-blue-600 hover:bg-blue-50"
                  >
                    {{ action }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Bottom input area -->
      <div class="border-t border-gray-200 bg-white px-4 pb-3 pt-2 sm:px-8">
        <div class="mx-auto max-w-3xl">
          <!-- Hotkey hint bar -->
          <div class="mb-2 flex items-center gap-2">
            <span class="text-xs text-gray-400">当前模型：</span>
            <div class="relative">
              <button
                class="text-xs font-medium text-blue-600 hover:underline cursor-pointer"
                @click.stop="toggleModelDropdown"
              >
                {{ modelLabel }}
              </button>
              <div
                v-if="showModelDropdown"
                class="absolute bottom-full left-0 mb-1 w-52 rounded-lg border border-gray-200 bg-white py-1 shadow-xl z-20"
              >
                <button
                  v-for="m in models"
                  :key="m.value"
                  class="flex w-full items-center gap-3 px-3 py-2.5 text-sm transition hover:bg-blue-50"
                  :class="selectedModel === m.value ? 'text-blue-600 font-medium bg-blue-50' : 'text-gray-600'"
                  @click.stop="selectModel(m.value)"
                >
                  <span class="flex-1 text-left">{{ m.label }}</span>
                  <svg v-if="selectedModel === m.value" class="h-4 w-4 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
                  </svg>
                </button>
              </div>
            </div>
            <span class="mx-1 text-gray-300">|</span>
            <span class="text-xs text-gray-400">当前 Agent：</span>
            <div class="relative">
              <button
                class="flex items-center gap-1 rounded-md border border-gray-200 px-2 py-1 text-xs font-medium text-blue-600 transition hover:bg-blue-50"
                @click.stop="showAgentDropdown = !showAgentDropdown"
              >
                {{ agentLabel }}
                <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              <div
                v-if="showAgentDropdown"
                class="absolute bottom-full left-0 mb-1 w-48 rounded-lg border border-gray-200 bg-white py-1 shadow-lg z-10"
              >
                <button
                  v-for="a in agents"
                  :key="a.value"
                  class="flex w-full items-center px-3 py-2 text-xs transition hover:bg-blue-50"
                  :class="selectedAgent === a.value ? 'text-blue-600 font-medium bg-blue-50' : 'text-gray-600'"
                  :title="`右键删除 ${a.label}`"
                  @click.stop="selectAgent(a.value)"
                  @contextmenu.stop.prevent="handleDeleteAgent(a)"
                >
                  {{ a.label }}
                </button>
              </div>
            </div>
            <span class="ml-auto inline-flex items-center gap-1 rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-400">
              <kbd class="rounded border border-gray-300 bg-white px-1 py-px text-[10px] font-semibold text-gray-500">#</kbd>
              <span>唤起模型选择</span>
            </span>
          </div>

          <!-- Textarea row -->
          <div class="relative">
            <textarea
              v-model="inputText"
              class="w-full resize-none rounded-xl border border-gray-200 bg-gray-50 py-3 pl-11 pr-20 text-sm text-gray-800 placeholder-gray-400 outline-none transition focus:border-blue-400 focus:bg-white focus:ring-1 focus:ring-blue-100"
              rows="3"
              placeholder="输入您的问题或指令 (Shift + Enter 换行)..."
              :maxlength="maxChars"
              @keydown="handleKeydown"
            ></textarea>
            <!-- Bottom-left: attach icon -->
            <button
              class="absolute bottom-3 left-3 flex h-7 w-7 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
              title="上传附件"
            >
              <el-icon :size="16"><Paperclip /></el-icon>
            </button>
            <!-- Bottom-right: char count + send -->
            <div class="absolute bottom-3 right-3 flex items-center gap-2">
              <span
                class="text-xs"
                :class="charCount > maxChars * 0.9 ? 'text-red-400' : 'text-gray-400'"
              >
                {{ charCount }}/{{ maxChars }}
              </span>
              <button
                class="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white transition hover:bg-blue-700 active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed"
                :disabled="!inputText.trim()"
                @click="sendMessage"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="accountSettingsVisible"
      align-center
      append-to-body
      class="!overflow-hidden !rounded-xl"
      :close-on-click-modal="!passwordSubmitting"
      :close-on-press-escape="!passwordSubmitting"
      :show-close="!passwordSubmitting"
      title="账号设置"
      width="min(520px, calc(100vw - 32px))"
      @closed="resetPasswordForm"
    >
      <div class="mb-6 border-l-4 border-blue-600 bg-blue-50 px-4 py-3">
        <p class="m-0 text-sm font-semibold text-blue-800">账户安全</p>
        <p class="mb-0 mt-1 text-xs leading-5 text-blue-700">
          修改成功后，下次登录必须使用新密码。
        </p>
      </div>

      <div class="mb-5">
        <h3 class="m-0 text-lg font-semibold text-gray-800">修改密码</h3>
        <p class="mb-0 mt-1 text-sm text-gray-500">请确认原密码并设置符合安全规则的新密码。</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleChangePassword">
        <el-form-item
          v-for="field in PASSWORD_FIELDS"
          :key="field.key"
          :error="passwordErrors[field.key]"
          :label="field.label"
        >
          <el-input
            :id="field.inputId"
            v-model="passwordForm[field.key]"
            :autocomplete="field.autocomplete"
            :data-testid="field.inputTestId"
            :prefix-icon="Lock"
            size="large"
            :type="passwordVisibility[field.key] ? 'text' : 'password'"
            @blur="passwordTouched[field.key] = true"
            @input="handlePasswordInput(field.key)"
          >
            <template #suffix>
              <button
                :aria-label="`${passwordVisibility[field.key] ? '隐藏' : '显示'}${field.label}`"
                class="flex h-7 w-7 items-center justify-center border-0 bg-transparent p-0 text-gray-400 transition hover:text-blue-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                :data-testid="field.toggleTestId"
                type="button"
                @click="togglePasswordVisibility(field.key)"
                @mousedown.prevent
              >
                <el-icon :size="17">
                  <Hide v-if="passwordVisibility[field.key]" />
                  <View v-else />
                </el-icon>
              </button>
            </template>
          </el-input>
        </el-form-item>

        <p
          v-if="passwordRequestError"
          class="-mt-1 mb-4 text-sm text-red-600"
          role="alert"
        >
          {{ passwordRequestError }}
        </p>

        <div class="mt-6 flex justify-end gap-3 border-t border-gray-100 pt-5">
          <el-button
            :disabled="passwordSubmitting"
            size="large"
            @click="accountSettingsVisible = false"
          >
            取消
          </el-button>
          <el-button
            color="#002fa7"
            data-testid="change-password-submit"
            :disabled="passwordSubmitting"
            :loading="passwordSubmitting"
            native-type="submit"
            size="large"
            type="primary"
          >
            保存修改
          </el-button>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>
