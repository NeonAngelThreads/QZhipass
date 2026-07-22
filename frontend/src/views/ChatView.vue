<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Bell,
  ChatDotSquare,
  Paperclip,
  Search,
  Setting,
  SwitchButton,
} from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { getErrorMessage } from '../api/http'
import {
  getConversation,
  listConversations,
  sendConversationTurn,
  type ConversationMessagePayload,
  type ConversationPayload,
} from '../api/conversation'
import { readLoginInfo } from '../api/session'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

// ========== state ==========
const searchQuery = ref('')
const inputText = ref('')
const selectedModel = ref('deepseek-v4')
const selectedAgent = ref('data-analyst')
const selectedChatId = ref<number | null>(null)
const showModelDropdown = ref(false)
const showAgentDropdown = ref(false)
const sendingMessage = ref(false)
const loadingConversation = ref(false)
const loadingHistory = ref(false)
const historyPage = ref(0)
const historyHasMore = ref(true)
const pendingTurn = ref<{ conversationId: number | null; prompt: string; requestId: string } | null>(null)
const historyPageSize = 20
let conversationLoadVersion = 0
let temporaryMessageId = 0

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

const agents = [
  { value: 'data-analyst', label: 'Data Analyst Agent' },
  { value: 'copywriter', label: 'Copywriter Agent' },
  { value: 'coder', label: 'Code Assistant Agent' },
]

interface ChatItem {
  id: number
  title: string
  createdAt: string
}

const chats = ref<ChatItem[]>([])

interface Message {
  id: number
  role: 'user' | 'ai'
  content: string
  timestamp: string
  actions?: string[]
}
const messages = ref<Message[]>([])

const chatContainer = ref<HTMLElement>()

const currentChat = computed(() => chats.value.find(c => c.id === selectedChatId.value))
const charCount = computed(() => Array.from(inputText.value).length)
const maxChars = 2000
const showReplyLoading = computed(() =>
  sendingMessage.value && pendingTurn.value?.conversationId === selectedChatId.value
)

async function selectChat(id: number) {
  const loadVersion = ++conversationLoadVersion
  selectedChatId.value = id
  loadingConversation.value = true
  messages.value = []
  try {
    const detail = await getConversation(id)
    if (loadVersion !== conversationLoadVersion || selectedChatId.value !== id) return
    selectedModel.value = detail.conversation.modelKey || selectedModel.value
    messages.value = detail.messages
      .filter(message => message.role !== 'SYSTEM')
      .map(toViewMessage)
    activateConversationHeader(detail.conversation)
    await nextTick(scrollToBottom)
  } catch (error) {
    if (loadVersion === conversationLoadVersion) {
      ElMessage.error(getErrorMessage(error, '读取对话失败'))
    }
  } finally {
    if (loadVersion === conversationLoadVersion) {
      loadingConversation.value = false
    }
  }
}

function toViewMessage(message: ConversationMessagePayload): Message {
  return {
    id: message.id,
    role: message.role === 'USER' ? 'user' : 'ai',
    content: message.content,
    timestamp: new Date(message.createdAt).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
  }
}

function activateConversationHeader(conversation: ConversationPayload, moveToTop = false) {
  const title = conversation.title || '新建对话'
  const existingIndex = chats.value.findIndex(chat => chat.id === conversation.id)
  if (existingIndex < 0) {
    chats.value.unshift({
      id: conversation.id,
      title,
      createdAt: conversation.createdAt
    })
    return
  }

  const existing = chats.value[existingIndex]
  existing.title = title
  existing.createdAt = conversation.createdAt || existing.createdAt
  if (moveToTop && existingIndex > 0) {
    chats.value.splice(existingIndex, 1)
    chats.value.unshift(existing)
  }
}

function toChatItem(conversation: ConversationPayload): ChatItem {
  return {
    id: conversation.id,
    title: conversation.title || '新建对话',
    createdAt: conversation.createdAt
  }
}

function formatConversationTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

async function loadHistoryPage(reset = false) {
  if (loadingHistory.value || (!reset && !historyHasMore.value)) return
  loadingHistory.value = true
  const page = reset ? 0 : historyPage.value
  try {
    const recent = await listConversations(page, historyPageSize)
    const incoming = recent.map(toChatItem)
    if (reset) {
      chats.value = incoming
    } else {
      const knownIds = new Set(chats.value.map(chat => chat.id))
      chats.value.push(...incoming.filter(chat => !knownIds.has(chat.id)))
    }
    historyPage.value = page + 1
    historyHasMore.value = recent.length === historyPageSize
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '读取历史对话失败'))
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryScroll(event: Event) {
  const target = event.currentTarget as HTMLElement
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  if (distanceToBottom < 48) void loadHistoryPage()
}

async function initializeConversationFromLogin() {
  const loginInfo = readLoginInfo()
  await loadHistoryPage(true)
  if (!loginInfo?.initialConversationId) {
    startNewConversation()
    return
  }
  await selectChat(loginInfo.initialConversationId)
}

function startNewConversation() {
  conversationLoadVersion += 1
  loadingConversation.value = false
  selectedChatId.value = null
  messages.value = []
  inputText.value = ''
  pendingTurn.value = null
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
  void initializeConversationFromLogin()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})

function selectAgent(val: string) {
  selectedAgent.value = val
  showAgentDropdown.value = false
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sendingMessage.value) return
  if (Array.from(text).length > maxChars) {
    ElMessage.warning('单次输入不能超过 2000 个字符')
    return
  }
  const conversationId = selectedChatId.value
  const retry = pendingTurn.value?.conversationId === conversationId
    && pendingTurn.value.prompt === text
  const requestId = retry
    ? pendingTurn.value!.requestId
    : (typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`)
  pendingTurn.value = { conversationId, prompt: text, requestId }
  sendingMessage.value = true
  inputText.value = ''
  const optimisticMessageId = --temporaryMessageId
  messages.value.push({
    id: optimisticMessageId,
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
  })
  await nextTick(scrollToBottom)
  try {
    const turn = await sendConversationTurn(conversationId, text, selectedModel.value, requestId)
    const isStillCurrentConversation = selectedChatId.value === conversationId
    if (isStillCurrentConversation) {
      selectedChatId.value = turn.conversation.id
      const optimisticIndex = messages.value.findIndex(message => message.id === optimisticMessageId)
      const completedMessages = [toViewMessage(turn.userMessage), toViewMessage(turn.assistantMessage)]
      if (optimisticIndex >= 0) {
        messages.value.splice(optimisticIndex, 1, ...completedMessages)
      } else {
        messages.value.push(...completedMessages)
      }
    }
    activateConversationHeader(turn.conversation, true)
    pendingTurn.value = null
    if (isStillCurrentConversation) await nextTick(scrollToBottom)
  } catch (error) {
    messages.value = messages.value.filter(message => message.id !== optimisticMessageId)
    if (selectedChatId.value === conversationId) inputText.value = text
    ElMessage.error(getErrorMessage(error, 'AI 回复失败，请稍后重试'))
  } finally {
    sendingMessage.value = false
  }
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

watch(
  () => messages.value.length,
  () => nextTick(scrollToBottom),
)

const modelLabel = computed(() => models.find(m => m.value === selectedModel.value)?.label ?? '')
const agentLabel = computed(() => agents.find(a => a.value === selectedAgent.value)?.label ?? '')
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
          :disabled="sendingMessage"
          @click="startNewConversation"
        >
          <el-icon :size="16"><ChatDotSquare /></el-icon>
          + 开启新会话
        </button>
      </div>

      <!-- Chat history -->
      <div class="mt-5 flex-1 overflow-y-auto px-3" @scroll.passive="handleHistoryScroll">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-400">对话历史</p>
        <div class="mb-3 px-1">
          <el-input
            v-model="searchQuery"
            placeholder="搜索对话内容..."
            :prefix-icon="Search"
            size="small"
            clearable
          />
        </div>
        <ul class="space-y-0.5">
          <li v-for="chat in chats" :key="chat.id">
            <button
              class="flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition"
              :class="
                selectedChatId === chat.id
                  ? 'bg-blue-50 text-blue-700 font-medium'
                  : 'text-gray-600 hover:bg-gray-50'
              "
              @click="selectChat(chat.id)"
            >
              <el-icon :size="16" class="mt-0.5 shrink-0"><ChatDotSquare /></el-icon>
              <span class="min-w-0 flex-1">
                <span class="block truncate">{{ chat.title }}</span>
                <span class="mt-0.5 block truncate text-xs font-normal text-gray-400">
                  {{ formatConversationTime(chat.createdAt) }}
                </span>
              </span>
            </button>
          </li>
          <li v-if="historyHasMore || loadingHistory" class="px-2 py-2">
            <button
              class="w-full rounded-md py-1.5 text-xs text-blue-600 transition hover:bg-blue-50 disabled:text-gray-400"
              :disabled="loadingHistory"
              @click="loadHistoryPage()"
            >
              {{ loadingHistory ? '加载中...' : '加载更多历史' }}
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
            {{ currentChat?.title ?? '新建对话' }}
          </h2>
          <span
            class="shrink-0 rounded-full bg-purple-50 px-2.5 py-0.5 text-xs font-medium text-purple-600"
          >
            {{ modelLabel }}
          </span>
        </div>
        <div class="flex items-center gap-2">
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-600"
            title="通知"
          >
            <el-icon :size="18"><Bell /></el-icon>
          </button>
        </div>
      </header>

      <!-- Chat area -->
      <div
        ref="chatContainer"
        class="flex-1 overflow-y-auto px-4 py-5 sm:px-8"
        :class="loadingConversation ? 'bg-white' : 'bg-gray-50'"
      >
        <div v-if="!loadingConversation" class="mx-auto max-w-3xl space-y-5">
          <div v-for="msg in messages" :key="msg.id">
            <!-- Timestamp separator -->
            <div v-if="msg.role === 'user'" class="mb-4 text-center">
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
                  <div class="whitespace-pre-wrap break-words">{{ msg.content }}</div>
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

          <!-- AI reply loading state -->
          <div v-if="showReplyLoading" class="flex gap-3" aria-live="polite" aria-label="AI 正在回复">
            <div
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-500 to-indigo-600 text-xs font-bold text-white"
            >
              AI
            </div>
            <div class="min-w-0 max-w-[80%]">
              <p class="mb-1 text-xs font-medium text-gray-500">Data Analyst Agent</p>
              <div class="flex items-center gap-1.5 rounded-2xl rounded-tl-sm bg-white px-4 py-3.5 shadow-sm">
                <span class="h-2 w-2 animate-bounce rounded-full bg-indigo-400"></span>
                <span class="h-2 w-2 animate-bounce rounded-full bg-indigo-400 [animation-delay:150ms]"></span>
                <span class="h-2 w-2 animate-bounce rounded-full bg-indigo-400 [animation-delay:300ms]"></span>
                <span class="ml-1 text-xs text-gray-400">AI 正在回复</span>
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
                  @click.stop="selectAgent(a.value)"
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
              class="w-full resize-none rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 pr-20 text-sm text-gray-800 placeholder-gray-400 outline-none transition focus:border-blue-400 focus:bg-white focus:ring-1 focus:ring-blue-100"
              rows="3"
              placeholder="输入您的问题或指令 (Shift + Enter 换行)..."
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
                :disabled="!inputText.trim() || charCount > maxChars || sendingMessage || loadingConversation"
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
  </div>
</template>
