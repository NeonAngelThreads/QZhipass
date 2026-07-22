<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  invokeAgent,
  listAgents,
  listConversationMessages,
  type AgentSummary,
  type ConversationMessage
} from '../api/agent'

const route = useRoute()
const router = useRouter()
const agents = ref<AgentSummary[]>([])
const messages = ref<ConversationMessage[]>([])
const selectedAgent = ref<AgentSummary>()
const showAgentPicker = ref(false)
const loadingAgents = ref(false)
const calling = ref(false)
const input = ref('')
const error = ref('')
const conversationId = ref<number>()

function errorText(value: unknown, fallback: string) {
  return value instanceof Error && value.message ? value.message : fallback
}

async function loadAvailableAgents() {
  loadingAgents.value = true
  error.value = ''
  try {
    agents.value = await listAgents()
  } catch (value) {
    error.value = errorText(value, 'Agent列表加载失败')
  } finally {
    loadingAgents.value = false
  }
}

async function loadHistory(id: number) {
  try {
    messages.value = await listConversationMessages(id)
  } catch (value) {
    error.value = errorText(value, '历史消息加载失败')
  }
}

function chooseAgent(agent: AgentSummary) {
  selectedAgent.value = agent
  showAgentPicker.value = false
}

function handleShortcut(event: KeyboardEvent) {
  if (event.key === 'Escape') showAgentPicker.value = false
  if (event.key === '!' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    event.preventDefault()
    showAgentPicker.value = true
  }
}

async function send() {
  const agent = selectedAgent.value
  const text = input.value.trim()
  if (calling.value || !text) return
  if (!agent) {
    error.value = '请先按“!”选择要调用的Agent'
    return
  }

  error.value = ''
  calling.value = true
  messages.value.push({ messageId: -Date.now(), role: 'USER', content: text })
  input.value = ''
  try {
    const result = await invokeAgent(agent.agentId, text, conversationId.value)
    conversationId.value = result.conversationId
    await router.replace(`/conversation/${result.conversationId}`)
    messages.value.push({
      messageId: result.messageId,
      role: 'ASSISTANT',
      content: result.content,
      agentId: result.agentId,
      createTime: result.invokedAt
    })
  } catch (value) {
    messages.value = messages.value.filter(message => message.messageId >= 0)
    input.value = text
    error.value = errorText(value, 'Agent调用失败')
  } finally {
    calling.value = false
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleShortcut)
  await loadAvailableAgents()
  const rawId = Array.isArray(route.params.conversationId)
    ? route.params.conversationId[0]
    : route.params.conversationId
  const id = Number(rawId)
  if (Number.isInteger(id) && id > 0) {
    conversationId.value = id
    await loadHistory(id)
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', handleShortcut))
</script>

<template>
  <main class="conversation-page">
    <header class="conversation-header">
      <button class="back" type="button" @click="router.push('/home')">← Agent 工作台</button>
      <div><strong>企智通对话</strong><span>按“!”调用 Agent，按 Esc 关闭列表</span></div>
      <button class="agent-trigger" type="button" @click="showAgentPicker = true">! 选择 Agent</button>
    </header>

    <section class="message-list" aria-live="polite">
      <div v-if="messages.length === 0" class="welcome">
        <span>AI</span><h1>开始一段 Agent 对话</h1>
        <p>先从工作台添加 Agent，再按“!”选择；预设提示词会随请求发送给模型。</p>
      </div>
      <article v-for="message in messages" :key="message.messageId" :class="['message', message.role.toLowerCase()]">
        <small>{{ message.role === 'USER' ? '你' : 'AI' }}</small><p>{{ message.content }}</p>
      </article>
      <p v-if="calling && selectedAgent" class="invoke-status">正在调用 {{ selectedAgent.agentName }}…</p>
    </section>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <footer class="composer">
      <div v-if="selectedAgent" class="selected-agent">
        <span>正在使用 {{ selectedAgent.agentName }}</span>
        <button type="button" :disabled="calling" @click="selectedAgent = undefined">取消选择</button>
      </div>
      <form @submit.prevent="send">
        <textarea v-model="input" rows="3" placeholder="输入消息；按 ! 选择 Agent" :disabled="calling" @keydown.ctrl.enter="send" />
        <button type="submit" :disabled="calling || !input.trim()">{{ calling ? '调用中…' : '发送' }}</button>
      </form>
    </footer>

    <div v-if="showAgentPicker" class="picker-backdrop" @click.self="showAgentPicker = false">
      <section class="agent-picker" role="dialog" aria-modal="true" aria-labelledby="picker-title">
        <header><div><h2 id="picker-title">选择要调用的 Agent</h2><p>Esc 关闭</p></div><button type="button" @click="showAgentPicker = false">×</button></header>
        <p v-if="loadingAgents" class="picker-empty">正在加载…</p>
        <p v-else-if="agents.length === 0" class="picker-empty">暂无可调用 Agent，请先返回工作台添加。</p>
        <button v-for="agent in agents" v-else :key="agent.agentId" class="agent-option" type="button" @click="chooseAgent(agent)">
          <span>{{ agent.agentType === 'SYSTEM_PRESET' ? '系统' : '个人' }}</span>
          <div><strong>{{ agent.agentName }}</strong><small>选择后预设提示词将加入模型请求</small></div>
        </button>
      </section>
    </div>
  </main>
</template>

<style scoped>
.conversation-page { min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; color: #172033; background: #f4f7fb; }
.conversation-header { min-height: 72px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 20px; padding: 0 clamp(20px, 5vw, 72px); border-bottom: 1px solid #dfe6ef; background: #fff; }
.conversation-header > div { display: grid; justify-items: center; gap: 3px; }.conversation-header span { color: #7a8799; font-size: 12px; }.conversation-header button { width: max-content; min-height: 40px; padding: 0 14px; border: 1px solid #cdd7e5; border-radius: 10px; background: #fff; cursor: pointer; }.back { justify-self: start; color: #34506f; }.agent-trigger { justify-self: end; color: #fff; border-color: #2563eb !important; background: #2563eb !important; font-weight: 800; }
.message-list { width: min(900px, calc(100% - 40px)); margin: 0 auto; padding: 42px 0 180px; }.welcome { padding: 80px 20px; text-align: center; color: #66758a; }.welcome > span { width: 58px; height: 58px; display: inline-grid; place-items: center; border-radius: 18px; color: #fff; background: linear-gradient(135deg, #2563eb, #0f766e); font-weight: 900; }.welcome h1 { margin: 18px 0 8px; color: #1d2b3f; }.welcome p { margin: 0 auto; max-width: 560px; line-height: 1.7; }
.message { width: min(78%, 680px); margin: 18px 0; padding: 15px 18px; border-radius: 16px; box-shadow: 0 8px 22px #1720330a; }.message small { color: #738097; font-weight: 800; }.message p { margin: 7px 0 0; white-space: pre-wrap; line-height: 1.75; }.message.user { margin-left: auto; color: #fff; background: #2563eb; }.message.user small { color: #dbeafe; }.message.assistant { border: 1px solid #e1e7ef; background: #fff; }.invoke-status { color: #64748b; }
.error { position: fixed; right: 24px; bottom: 160px; z-index: 4; max-width: min(440px, calc(100% - 48px)); margin: 0; padding: 12px 15px; border: 1px solid #fecaca; border-radius: 12px; color: #a61b1b; background: #fff1f0; box-shadow: 0 14px 36px #17203322; }
.composer { position: fixed; right: 0; bottom: 0; left: 0; padding: 14px max(20px, calc((100% - 900px) / 2)); border-top: 1px solid #dfe6ef; background: #fffffff2; backdrop-filter: blur(12px); }.composer form { display: grid; grid-template-columns: 1fr auto; gap: 10px; }.composer textarea { resize: none; padding: 13px 15px; border: 1px solid #cbd5e1; border-radius: 14px; outline: none; font: inherit; }.composer form > button { min-width: 90px; border: 0; border-radius: 13px; color: #fff; background: #2563eb; font-weight: 800; cursor: pointer; }.composer button:disabled { cursor: not-allowed; opacity: .55; }.selected-agent { display: flex; justify-content: space-between; margin-bottom: 9px; color: #475569; font-size: 13px; }.selected-agent span { padding: 5px 9px; border-radius: 999px; color: #1d4ed8; background: #eff6ff; font-weight: 750; }.selected-agent button { border: 0; color: #64748b; background: transparent; cursor: pointer; }
.picker-backdrop { position: fixed; inset: 0; z-index: 10; display: grid; place-items: center; padding: 20px; background: #0f172a99; }.agent-picker { width: min(100%, 560px); max-height: min(680px, calc(100vh - 40px)); overflow: auto; padding: 24px; border-radius: 20px; background: #fff; box-shadow: 0 30px 90px #0006; }.agent-picker header { display: flex; justify-content: space-between; margin-bottom: 18px; }.agent-picker h2 { margin: 0; }.agent-picker header p { margin: 4px 0 0; color: #8a96a8; font-size: 13px; }.agent-picker header button { width: 36px; height: 36px; border: 1px solid #d7dee9; border-radius: 10px; background: #fff; font-size: 22px; cursor: pointer; }.agent-option { width: 100%; display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 13px; margin-top: 10px; padding: 14px; border: 1px solid #e0e6ef; border-radius: 13px; text-align: left; background: #fbfcfe; cursor: pointer; }.agent-option > span { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 12px; color: #fff; background: linear-gradient(135deg, #2563eb, #0f766e); font-size: 12px; font-weight: 850; }.agent-option div { display: grid; gap: 4px; }.agent-option small, .picker-empty { color: #7a8799; }
@media (max-width: 680px) { .conversation-header { grid-template-columns: auto 1fr auto; padding: 0 14px; }.conversation-header span { display: none; }.message { width: 88%; }.composer { padding-right: 14px; padding-left: 14px; }.composer form { grid-template-columns: 1fr; }.composer form > button { min-height: 42px; }.error { bottom: 220px; } }
</style>
