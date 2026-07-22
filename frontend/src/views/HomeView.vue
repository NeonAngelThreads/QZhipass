<script setup lang="ts">
import { reactive, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  addAgentToLibrary,
  createAgent,
  deleteAgent,
  getAgentDeleteCheck,
  listAgentCatalog,
  listAgents,
  type AgentDeleteCheck,
  type AgentSummary
} from '../api/agent'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const agents = ref<AgentSummary[]>([])
const catalog = ref<AgentSummary[]>([])
const search = ref('')
const catalogSearch = ref('')
const loading = ref(false)
const catalogLoading = ref(false)
const showCatalog = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', prompt: '' })
const notice = ref('')
const noticeKind = ref<'success' | 'error'>('success')
const checkingAgentId = ref<number>()
const addingAgentId = ref<number>()
const deleteCheck = ref<AgentDeleteCheck>()
const deleting = ref(false)

function showNotice(text: string, kind: 'success' | 'error' = 'success') { notice.value = text; noticeKind.value = kind }
function errorText(error: unknown, fallback: string) { return error instanceof Error && error.message ? error.message : fallback }
async function loadAgents() {
  loading.value = true
  try { agents.value = await listAgents(search.value.trim()) }
  catch (error) { showNotice(errorText(error, 'Agent列表加载失败'), 'error') }
  finally { loading.value = false }
}
async function loadCatalog() {
  catalogLoading.value = true
  try { catalog.value = await listAgentCatalog(catalogSearch.value.trim()) }
  catch (error) { showNotice(errorText(error, '系统Agent库加载失败'), 'error') }
  finally { catalogLoading.value = false }
}
async function toggleCatalog() { showCatalog.value = !showCatalog.value; if (showCatalog.value) await loadCatalog() }
async function submitCreate() {
  if (creating.value || !createForm.name.trim() || !createForm.prompt.trim()) return
  creating.value = true
  try {
    await createAgent(createForm.name.trim(), createForm.prompt.trim())
    showNotice(`${createForm.name.trim()} 创建成功`)
    createForm.name = ''; createForm.prompt = ''; showCreate.value = false
    await loadAgents()
  } catch (error) { showNotice(errorText(error, 'Agent创建失败'), 'error') }
  finally { creating.value = false }
}
async function addToLibrary(agent: AgentSummary) {
  if (addingAgentId.value) return
  addingAgentId.value = agent.agentId
  try { await addAgentToLibrary(agent.agentId); showNotice(`${agent.agentName} 已加入我的Agent`); await Promise.all([loadAgents(), loadCatalog()]) }
  catch (error) { showNotice(errorText(error, 'Agent添加失败'), 'error') }
  finally { addingAgentId.value = undefined }
}
async function openDelete(agent: AgentSummary) {
  if (!agent.deletable || checkingAgentId.value) return
  checkingAgentId.value = agent.agentId; notice.value = ''
  try { deleteCheck.value = await getAgentDeleteCheck(agent.agentId) }
  catch (error) { showNotice(errorText(error, '无法检查Agent删除条件'), 'error') }
  finally { checkingAgentId.value = undefined }
}
async function confirmDelete() {
  const check = deleteCheck.value
  if (!check?.canDelete || deleting.value) return
  deleting.value = true
  try {
    await deleteAgent(check.agentId)
    showNotice(check.deleteMode === 'REMOVE_FROM_USER_LIBRARY' ? `${check.agentName} 已移除` : `${check.agentName} 已删除`)
    deleteCheck.value = undefined
    await Promise.all([loadAgents(), showCatalog.value ? loadCatalog() : Promise.resolve()])
  } catch (error) { showNotice(errorText(error, 'Agent删除失败'), 'error') }
  finally { deleting.value = false }
}
async function logout() { await authStore.logout(); await router.push('/login') }
onMounted(loadAgents)
</script>

<template>
  <main class="workspace">
    <header class="topbar"><div><span class="brand-mark">企</span><strong>企智通</strong><small>Agent 工作台</small></div><div class="top-actions"><button class="link" type="button" @click="router.push('/chat')">原生对话</button><span class="role">{{ authStore.profile?.role === 'ADMIN' ? '管理员' : '普通用户' }}</span><button class="ghost" type="button" @click="logout">退出登录</button></div></header>
    <section class="hero"><div><p class="eyebrow">MY AGENT LIBRARY</p><h1>我的 Agent</h1><p>管理系统 Agent 和个人智能助手，并直接发起对话。</p></div><div class="hero-actions"><button type="button" @click="showCreate = true">创建 Agent</button><button type="button" @click="router.push('/conversation')">开始对话</button><button type="button" @click="toggleCatalog">{{ showCatalog ? '返回我的Agent' : '浏览系统Agent库' }}</button></div></section>
    <p v-if="notice" :class="['notice', noticeKind]" role="status">{{ notice }}</p>

    <section class="content-card">
      <div class="section-heading"><div><h2>{{ showCatalog ? '系统 Agent 库' : '已使用的 Agent' }}</h2><span>{{ showCatalog ? catalog.length : agents.length }} 个</span></div><form @submit.prevent="showCatalog ? loadCatalog() : loadAgents()"><input v-if="showCatalog" v-model="catalogSearch" placeholder="搜索系统Agent" /><input v-else v-model="search" placeholder="搜索我的Agent" /><button type="submit">搜索</button></form></div>
      <p v-if="showCatalog ? catalogLoading : loading" class="empty">正在加载…</p>
      <p v-else-if="(showCatalog ? catalog : agents).length === 0" class="empty">{{ showCatalog ? '没有匹配的系统Agent。' : '暂无Agent，可浏览系统Agent库或创建个人Agent。' }}</p>
      <div v-else class="agent-grid">
        <article v-for="agent in (showCatalog ? catalog : agents)" :key="agent.agentId" class="agent-card">
          <div :class="['agent-icon', { system: agent.agentType === 'SYSTEM_PRESET' }]">{{ agent.agentType === 'SYSTEM_PRESET' ? 'S' : 'AI' }}</div>
          <div class="agent-info"><span>{{ agent.agentType === 'SYSTEM_PRESET' ? '系统预置' : '个人创建' }}</span><h3>{{ agent.agentName }}</h3><p>{{ showCatalog ? (agent.inUserLibrary ? '已在我的Agent中' : '可添加到个人工作台') : '可在对话中按 ! 调用' }}</p></div>
          <div class="card-actions"><button v-if="showCatalog && !agent.inUserLibrary" type="button" :disabled="addingAgentId === agent.agentId" @click="addToLibrary(agent)">{{ addingAgentId === agent.agentId ? '添加中…' : '添加' }}</button><button v-if="agent.deletable" class="danger" type="button" :disabled="checkingAgentId === agent.agentId" @click="openDelete(agent)">删除</button></div>
        </article>
      </div>
    </section>

    <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false"><form class="modal" @submit.prevent="submitCreate"><div class="agent-icon">AI</div><h2>创建个人 Agent</h2><label>名称<input v-model="createForm.name" maxlength="20" placeholder="仅中文、字母、数字、下划线或连字符" /></label><label>提示词<textarea v-model="createForm.prompt" rows="7" maxlength="20000" placeholder="描述角色、任务和输出要求" /></label><div class="modal-actions"><button class="ghost" type="button" :disabled="creating" @click="showCreate = false">取消</button><button type="submit" :disabled="creating || !createForm.name.trim() || !createForm.prompt.trim()">{{ creating ? '创建中…' : '创建' }}</button></div></form></div>
    <div v-if="deleteCheck" class="modal-backdrop" @click.self="deleteCheck = undefined"><section class="modal"><div class="warning-icon">!</div><h2>{{ deleteCheck.confirmationMessage }}</h2><p v-if="!deleteCheck.canDelete" class="blocked">{{ deleteCheck.reasonMessage }}</p><div class="modal-actions"><button class="ghost" type="button" :disabled="deleting" @click="deleteCheck = undefined">取消</button><button class="danger" type="button" :disabled="!deleteCheck.canDelete || deleting" @click="confirmDelete">{{ deleting ? '删除中…' : '确认删除' }}</button></div></section></div>
  </main>
</template>

<style scoped>
.workspace { min-height: 100vh; padding-bottom: 64px; background: #f3f6fb; color: #172033; }.topbar { height: 72px; display: flex; align-items: center; justify-content: space-between; padding: 0 clamp(24px, 5vw, 72px); border-bottom: 1px solid #dfe6ef; background: #fff; }.topbar > div { display: flex; align-items: center; gap: 12px; }.topbar small { color: #718096; }.brand-mark { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 11px; color: #fff; background: linear-gradient(135deg, #2563eb, #0891b2); font-weight: 900; }.top-actions { display: flex; align-items: center; gap: 12px; }.role { padding: 7px 11px; border-radius: 999px; color: #1d4ed8; background: #eff6ff; font-size: 13px; font-weight: 750; }.link { border: 0; color: #2563eb; background: transparent; }
.hero { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 54px clamp(24px, 7vw, 104px) 34px; color: #fff; background: radial-gradient(circle at 82% 18%, #2dd4bf55, transparent 32%), linear-gradient(120deg, #122653, #245da5 62%, #0f766e); }.hero h1 { margin: 6px 0 10px; font-size: clamp(40px, 5vw, 64px); }.hero p { margin: 0; color: #dbeafe; }.eyebrow { color: #99f6e4 !important; font-size: 12px; font-weight: 850; letter-spacing: .18em; }.hero-actions { display: flex; flex-wrap: wrap; gap: 10px; }
button { min-height: 40px; padding: 0 15px; border: 1px solid #cbd5e1; border-radius: 10px; color: #244164; background: #fff; font-weight: 750; cursor: pointer; }button:disabled { cursor: not-allowed; opacity: .5; }.ghost { background: #f8fafc; }.hero-actions button { border-color: #ffffff55; color: #fff; background: #ffffff16; }.notice { width: min(1180px, calc(100% - 48px)); margin: 24px auto 0; padding: 12px 15px; border-radius: 12px; font-weight: 700; }.notice.success { color: #116b35; background: #eaf8ef; }.notice.error { color: #b42318; background: #fff0ed; }
.content-card { width: min(1180px, calc(100% - 48px)); margin: 28px auto 0; padding: 28px; border: 1px solid #e0e6ef; border-radius: 20px; background: #fff; box-shadow: 0 18px 44px #1720330d; }.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 22px; }.section-heading > div { display: flex; align-items: center; gap: 12px; }.section-heading h2 { margin: 0; }.section-heading span { color: #64748b; }.section-heading form { display: flex; gap: 8px; }.section-heading input { min-width: 240px; height: 42px; padding: 0 13px; border: 1px solid #cbd5e1; border-radius: 10px; }.agent-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(290px, 1fr)); gap: 14px; }.agent-card { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 15px; min-width: 0; padding: 18px; border: 1px solid #e1e7ef; border-radius: 15px; background: #fbfcfe; }.agent-icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 13px; color: #fff; background: linear-gradient(135deg, #2563eb, #0891b2); font-weight: 900; }.agent-icon.system { background: linear-gradient(135deg, #7c3aed, #2563eb); }.agent-info { min-width: 0; }.agent-info span { color: #64748b; font-size: 12px; font-weight: 750; }.agent-info h3 { margin: 4px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.agent-info p { margin: 0; color: #718096; font-size: 13px; }.card-actions { display: flex; gap: 7px; }.danger { border-color: #fecaca; color: #b42318; background: #fff5f3; }.empty { padding: 42px 0; text-align: center; color: #718096; }
.modal-backdrop { position: fixed; inset: 0; z-index: 10; display: grid; place-items: center; padding: 20px; background: #0f172a99; }.modal { width: min(100%, 520px); display: grid; gap: 15px; padding: 30px; border: 0; border-radius: 20px; background: #fff; box-shadow: 0 32px 80px #0005; }.modal h2 { margin: 0; }.modal label { display: grid; gap: 7px; font-weight: 750; }.modal input, .modal textarea { padding: 12px; border: 1px solid #cbd5e1; border-radius: 10px; font: inherit; }.warning-icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 50%; color: #b42318; background: #fee2e2; font-size: 25px; font-weight: 900; }.blocked { padding: 14px; border: 1px solid #fecaca; border-radius: 12px; color: #991b1b; background: #fff5f3; }.modal-actions { display: flex; justify-content: flex-end; gap: 10px; }
@media (max-width: 720px) { .topbar small, .role, .link { display: none; }.hero { align-items: start; flex-direction: column; }.section-heading { align-items: stretch; flex-direction: column; }.section-heading form { display: grid; grid-template-columns: 1fr auto; }.section-heading input { min-width: 0; }.agent-card { grid-template-columns: auto 1fr; }.card-actions { grid-column: 1 / -1; justify-content: flex-end; } }
</style>
