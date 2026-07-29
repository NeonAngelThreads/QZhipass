<script setup lang="ts">
import {type Component, computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useRouter} from 'vue-router'
import BrandLogo from '../components/BrandLogo.vue'
import CreateAgentDialog from '../components/CreateAgentDialog.vue'
import http, {getErrorMessage} from '../api/http'
import {readLoginInfo, saveInitialConversationId} from '../api/session'
import {
  createAgent as createAgentRequest,
  deleteAgent as deleteAgentRequest,
  invokeAgentStream,
  listAgents,
  type AgentPayload,
} from '../api/agent'
import {sendConversationTurn} from '../api/conversation'
import type {CreateAgentPayload} from '../components/types'
import {useAuthStore} from '../stores/auth'
import {
  Bell,
  ChatDotSquare,
  Download,
  Headset,
  Histogram,
  HomeFilled,
  Paperclip,
  Search,
  Setting,
  Share,
  SwitchButton,
  Upload,
  UserFilled,
} from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

// ========== state ==========
const searchQuery = ref('')
const inputText = ref('')
const selectedModel = ref('gpt4-omni')
const selectedAgentId = ref<string | null>(null)
const selectedChatId = ref<string | null>(null)
const showModelDropdown = ref(false)
const showAgentDropdown = ref(false)
const creatingConversation = ref(false)
const loadingAgents = ref(false)
const sendingMessage = ref(false)
const deletingAgentIds = ref(new Set<string>())
const createAgentDialogVisible = ref(false)
const savingAgent = ref(false)
const compositionActive = ref(false)
const invokeAbortController = ref<AbortController | null>(null)
const agentHotkey = ref('!')

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

const agents = ref<AgentPayload[]>([])

interface ApiResponse<T> {
  success?: boolean
  message?: string
  data?: T
}

interface ConversationPayload {
  id: string
  title?: string
  modelKey?: string | null
}

interface ChatItem {
  id: string
  title: string
  icon: Component
}

interface CreateConversationOptions {
  silent?: boolean
  persistAsInitial?: boolean
}

const chats = ref<ChatItem[]>([])

interface Message {
  id: number
  role: 'user' | 'ai'
  content: string
  timestamp: string
  agentName?: string
  statusText?: string
  actions?: string[]
}
const messages = ref<Message[]>([])

const chatContainer = ref<HTMLElement>()

const currentChat = computed(() => chats.value.find(c => c.id === selectedChatId.value))
const charCount = computed(() => inputText.value.length)
const maxChars = 2000

function selectChat(id: string) {
  selectedChatId.value = id
}

function activateConversation(conversation: ConversationPayload) {
  const title = conversation.title || '新建对话'
  const existing = chats.value.find(chat => chat.id === conversation.id)

  if (existing) {
    existing.title = title
  } else {
    chats.value.unshift({
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

function currentTimestamp() {
  return new Date().toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  })
}

function renderMarkdown(content: string) {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.*)$/gm, '<h4 class="text-base font-semibold mt-2 mb-1">$1</h4>')
    .replace(/\n/g, '<br>')
}

function hotkeyStorageKey() {
  return `agent_hotkey:${readLoginInfo()?.userId ?? 'anonymous'}`
}

function loadAgentHotkey() {
  const saved = window.localStorage.getItem(hotkeyStorageKey())
  agentHotkey.value = saved && Array.from(saved).length === 1 ? saved : '!'
}

function isTypingTargetWithContent(event: KeyboardEvent) {
  const target = event.target
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    return target.value.length > 0
  }
  return target instanceof HTMLElement && target.isContentEditable
}

async function refreshAgents() {
  if (loadingAgents.value) return
  loadingAgents.value = true
  try {
    agents.value = await listAgents()
    if (selectedAgentId.value !== null && !agents.value.some(agent => agent.id === selectedAgentId.value)) {
      selectedAgentId.value = null
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '读取Agent列表失败'))
  } finally {
    loadingAgents.value = false
  }
}

async function openAgentDropdown() {
  showModelDropdown.value = false
  await refreshAgents()
  showAgentDropdown.value = true
}

function closeAgentDropdown() {
  showAgentDropdown.value = false
}

async function configureAgentHotkey() {
  try {
    const result = await ElMessageBox.prompt('请输入一个新的 Agent 调用热键', '设置调用热键', {
      inputValue: agentHotkey.value,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: value => {
        const normalized = value.trim()
        if (Array.from(normalized).length !== 1) return '请输入单个字符'
        if (['Escape', '#', '~', '～', 'Enter'].includes(normalized)) return '该按键已被系统占用'
        return true
      },
    })
    const nextHotkey = result.value.trim()
    window.localStorage.setItem(hotkeyStorageKey(), nextHotkey)
    agentHotkey.value = nextHotkey
    closeAgentDropdown()
    ElMessage.success(`Agent调用热键已设置为 ${nextHotkey}`)
  } catch {
    // User cancelled.
  }
}

function openCreateAgentDialog() {
  closeAgentDropdown()
  createAgentDialogVisible.value = true
}

async function createAgent(payload: CreateAgentPayload) {
  if (savingAgent.value) return
  savingAgent.value = true
  try {
    const created = await createAgentRequest(payload.agentName, payload.promptContent)
    await refreshAgents()
    selectedAgentId.value = created.id
    createAgentDialogVisible.value = false
    ElMessage.success(`Agent「${created.name}」创建成功`)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, 'Agent创建失败'))
  } finally {
    savingAgent.value = false
  }
}

async function confirmDeleteAgent(agent: AgentPayload) {
  if (agent.source !== 'USER' || deletingAgentIds.value.has(agent.id)) return
  try {
    await ElMessageBox.confirm(`确定要删除${agent.name}吗？`, '删除 Agent', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  deletingAgentIds.value = new Set(deletingAgentIds.value).add(agent.id)
  try {
    await deleteAgentRequest(agent.id)
    await refreshAgents()
    if (selectedAgentId.value === agent.id) selectedAgentId.value = null
    ElMessage.success('Agent已删除')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, 'Agent删除失败'))
  } finally {
    const next = new Set(deletingAgentIds.value)
    next.delete(agent.id)
    deletingAgentIds.value = next
  }
}

function handleGlobalKeydown(e: KeyboardEvent) {
  if (e.isComposing || compositionActive.value) return

  if (e.key === '#' && !isTypingTargetWithContent(e)) {
    e.preventDefault()
    toggleModelDropdown()
  }
  if ((e.key === agentHotkey.value || (agentHotkey.value === '!' && e.key === '！'))
      && !isTypingTargetWithContent(e)) {
    e.preventDefault()
    void openAgentDropdown()
  }
  if ((e.key === '~' || e.key === '～') && !isTypingTargetWithContent(e)) {
    e.preventDefault()
    openCreateAgentDialog()
  }
  if (e.key === 'Escape') {
    showModelDropdown.value = false
    closeAgentDropdown()
  }
}

onMounted(() => {
  initializeConversationFromLogin()
  loadAgentHotkey()
  void refreshAgents()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  invokeAbortController.value?.abort()
  window.removeEventListener('keydown', handleGlobalKeydown)
})

function selectAgent(agentId: string | null) {
  selectedAgentId.value = agentId
  closeAgentDropdown()
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sendingMessage.value) return

  if (selectedChatId.value === null) {
    await createNewConversation({ silent: true })
  }
  const conversationId = selectedChatId.value
  if (conversationId === null) {
    ElMessage.error('新建对话失败')
    return
  }

  const selectedAgent = agents.value.find(agent => agent.id === selectedAgentId.value)
  const requestId = typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `agent-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const userMessageId = Date.now()
  const assistantMessageId = userMessageId + 1
  const timestamp = currentTimestamp()
  messages.value.push({
    id: userMessageId,
    role: 'user',
    content: text,
    timestamp,
  }, {
    id: assistantMessageId,
    role: 'ai',
    content: '',
    timestamp,
    agentName: selectedAgent?.name,
    statusText: selectedAgent ? `正在调用${selectedAgent.name}来构思回答...` : '正在生成回答...',
  })
  inputText.value = ''
  sendingMessage.value = true
  await nextTick(scrollToBottom)

  try {
    if (selectedAgent) {
      invokeAbortController.value = new AbortController()
      await invokeAgentStream(
        selectedAgent.id,
        {
          conversationId,
          prompt: text,
          modelKey: selectedModel.value,
          requestId,
        },
        event => {
          const assistant = messages.value.find(message => message.id === assistantMessageId)
          if (!assistant) return
          if (event.message && event.type !== 'error') assistant.statusText = event.message
          if (event.type === 'content' && event.content) {
            assistant.statusText = ''
            assistant.content += event.content
          }
          if (event.type === 'complete') {
            assistant.statusText = ''
            if (!assistant.content && event.turn?.assistantMessage.content) {
              assistant.content = event.turn.assistantMessage.content
            }
            const chat = chats.value.find(item => item.id === conversationId)
            if (chat && event.turn?.conversation.title) chat.title = event.turn.conversation.title
          }
        },
        invokeAbortController.value.signal,
      )
    } else {
      const turn = await sendConversationTurn(
        conversationId,
        text,
        selectedModel.value,
        requestId,
      )
      const assistant = messages.value.find(message => message.id === assistantMessageId)
      if (assistant) {
        assistant.statusText = ''
        assistant.content = turn.assistantMessage.content
      }
      const chat = chats.value.find(item => item.id === conversationId)
      if (chat) chat.title = turn.conversation.title || chat.title
    }
  } catch (error) {
    messages.value = messages.value.filter(message => message.id !== assistantMessageId)
    ElMessage.error(selectedAgent
      ? 'Agent调用失败，请稍后重试。'
      : getErrorMessage(error, 'AI 回复失败'))
  } finally {
    invokeAbortController.value = null
    sendingMessage.value = false
    await nextTick(scrollToBottom)
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void sendMessage()
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

watch(
  () => messages.value.length,
  () => nextTick(scrollToBottom),
)

const modelLabel = computed(() => models.find(m => m.value === selectedModel.value)?.label ?? '')
const selectedAgent = computed(() => agents.value.find(agent => agent.id === selectedAgentId.value))
const agentLabel = computed(() => selectedAgent.value?.name ?? '调用agent')
const existingAgentNames = computed(() =>
  agents.value.filter(agent => agent.source === 'USER').map(agent => agent.name),
)
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
        <div class="mb-2 flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-500 transition hover:bg-gray-50 cursor-pointer">
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
            @click="router.push('/account/settings')"
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
                <p class="mb-1 text-xs font-medium text-gray-500">{{ msg.agentName || 'AI 助手' }}</p>
                <p v-if="msg.statusText" class="mb-1 text-xs text-gray-400">{{ msg.statusText }}</p>
                <!-- AI bubble -->
                <div
                  v-if="msg.content"
                  class="rounded-2xl rounded-tl-sm bg-white px-4 py-3 text-sm text-gray-700 shadow-sm leading-relaxed"
                >
                  <!-- Basic markdown rendering -->
                  <div v-html="renderMarkdown(msg.content)"></div>
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
                @click.stop="showAgentDropdown ? closeAgentDropdown() : openAgentDropdown()"
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
                  class="flex w-full items-center px-3 py-2 text-xs text-gray-600 transition hover:bg-blue-50"
                  @click.stop="selectAgent(null)"
                >
                  不调用 Agent
                </button>
                <button
                  v-for="a in agents"
                  :key="a.id"
                  class="flex w-full items-center px-3 py-2 text-xs transition hover:bg-blue-50"
                  :class="selectedAgentId === a.id ? 'text-blue-600 font-medium bg-blue-50' : 'text-gray-600'"
                  @click.stop="selectAgent(a.id)"
                  @contextmenu.prevent="confirmDeleteAgent(a)"
                >
                  <span class="flex-1 text-left">{{ a.name }}</span>
                  <span
                    v-if="a.source === 'USER'"
                    class="text-gray-400"
                    @click.stop="confirmDeleteAgent(a)"
                  >
                    {{ deletingAgentIds.has(a.id) ? '删除中...' : '删除' }}
                  </span>
                </button>
                <button
                  class="flex w-full items-center px-3 py-2 text-xs text-gray-600 transition hover:bg-blue-50"
                  @click.stop="openCreateAgentDialog"
                >
                  创建 Agent
                </button>
                <button
                  class="flex w-full items-center px-3 py-2 text-xs text-gray-600 transition hover:bg-blue-50"
                  @click.stop="configureAgentHotkey"
                >
                  设置调用热键（{{ agentHotkey }}）
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
              class="w-full resize-none rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 pr-20 text-sm text-gray-800 placeholder-gray-400 outline-none transition focus:border-blue-400 focus:bg-white focus:ring-1 focus:ring-blue-100"
              rows="3"
              placeholder="输入您的问题或指令 (Shift + Enter 换行)..."
              :maxlength="maxChars"
              :disabled="sendingMessage"
              @keydown="handleKeydown"
              @compositionstart="compositionActive = true"
              @compositionend="compositionActive = false"
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
                :disabled="!inputText.trim() || sendingMessage"
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
    <CreateAgentDialog
      v-model="createAgentDialogVisible"
      :existing-names="existingAgentNames"
      :submitting="savingAgent"
      @submit="createAgent"
    />
  </div>
</template>
