<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isValidMobile, sendSmsCode } from '../api/auth'
import { useAuthStore } from '../stores/auth'

type LoginTab = 'phone' | 'email' | 'sms'
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref<LoginTab>('phone')
const submitting = ref(false)
const smsSending = ref(false)
const countdown = ref(0)
const message = ref('')
const messageKind = ref<'error' | 'success'>('error')
const showPhonePassword = ref(false)
const showEmailPassword = ref(false)
let timer: number | undefined

const phoneForm = reactive({ phone: '', password: '' })
const emailForm = reactive({ email: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })
const canSubmitPhone = computed(() => isValidMobile(phoneForm.phone.trim()) && Boolean(phoneForm.password) && !submitting.value)
const canSubmitEmail = computed(() => EMAIL_PATTERN.test(emailForm.email.trim()) && Boolean(emailForm.password) && !submitting.value)
const canSendSms = computed(() => isValidMobile(smsForm.phone.trim()) && countdown.value === 0 && !smsSending.value)
const canSubmitSms = computed(() => isValidMobile(smsForm.phone.trim()) && /^\d{6}$/.test(smsForm.code.trim()) && !submitting.value)

function showMessage(text: string, kind: 'error' | 'success' = 'error') {
  message.value = text
  messageKind.value = kind
}
function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
async function finishLogin() {
  showMessage('登录成功', 'success')
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
  await router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/home')
}
async function submitPhone() {
  if (!canSubmitPhone.value) return
  submitting.value = true; message.value = ''
  try { await authStore.phonePasswordLogin(phoneForm.phone.trim(), phoneForm.password); await finishLogin() }
  catch (error) { showMessage(errorText(error, '账号或密码错误')) }
  finally { submitting.value = false }
}
async function submitEmail() {
  if (!canSubmitEmail.value) return
  submitting.value = true; message.value = ''
  try { await authStore.emailPasswordLogin(emailForm.email.trim(), emailForm.password); await finishLogin() }
  catch (error) { showMessage(errorText(error, '账号或密码错误')) }
  finally { submitting.value = false }
}
async function submitSms() {
  if (!canSubmitSms.value) return
  submitting.value = true; message.value = ''
  try { await authStore.smsLogin(smsForm.phone.trim(), smsForm.code.trim()); await finishLogin() }
  catch (error) { showMessage(errorText(error, '手机号或验证码错误')) }
  finally { submitting.value = false }
}
async function requestSmsCode() {
  if (!canSendSms.value) return
  smsSending.value = true; message.value = ''
  try {
    await sendSmsCode(smsForm.phone.trim())
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) { window.clearInterval(timer); timer = undefined }
    }, 1000)
    showMessage('验证码已发送', 'success')
  } catch (error) { showMessage(errorText(error, '验证码发送失败')) }
  finally { smsSending.value = false }
}
onBeforeUnmount(() => { if (timer) window.clearInterval(timer) })
</script>

<template>
  <main class="login-page">
    <section class="brand-panel">
      <div class="brand-content"><div class="brand-mark">企</div><p class="eyebrow">ENTERPRISE AI WORKSPACE</p><h1>企智通</h1><p class="slogan">让企业知识、智能 Agent 与每一次协作自然连接。</p><div class="brand-tags"><span>安全访问</span><span>统一智能体</span><span>知识协作</span></div></div>
    </section>
    <section class="form-side">
      <div class="login-card">
        <header><span class="mini-brand">企智通</span><h2>欢迎回来</h2><p>请选择登录方式进入企业智能工作台</p></header>
        <div class="tabs" role="tablist">
          <button :class="{ active: activeTab === 'phone' }" type="button" @click="activeTab = 'phone'; message = ''">手机密码</button>
          <button :class="{ active: activeTab === 'email' }" type="button" @click="activeTab = 'email'; message = ''">邮箱密码</button>
          <button :class="{ active: activeTab === 'sms' }" type="button" @click="activeTab = 'sms'; message = ''">验证码</button>
        </div>
        <form v-if="activeTab === 'phone'" @submit.prevent="submitPhone">
          <label>手机号<input v-model="phoneForm.phone" maxlength="11" autocomplete="username" placeholder="请输入手机号" type="tel" /></label>
          <label>密码<span class="password-field"><input v-model="phoneForm.password" autocomplete="current-password" placeholder="请输入密码" :type="showPhonePassword ? 'text' : 'password'" /><button type="button" @click="showPhonePassword = !showPhonePassword">{{ showPhonePassword ? '隐藏' : '显示' }}</button></span></label>
          <button class="primary" :disabled="!canSubmitPhone" type="submit">{{ submitting ? '登录中…' : '登录' }}</button>
        </form>
        <form v-else-if="activeTab === 'email'" @submit.prevent="submitEmail">
          <label>邮箱<input v-model="emailForm.email" autocomplete="username" placeholder="请输入邮箱" type="email" /></label>
          <label>密码<span class="password-field"><input v-model="emailForm.password" autocomplete="current-password" placeholder="请输入密码" :type="showEmailPassword ? 'text' : 'password'" /><button type="button" @click="showEmailPassword = !showEmailPassword">{{ showEmailPassword ? '隐藏' : '显示' }}</button></span></label>
          <button class="primary" :disabled="!canSubmitEmail" type="submit">{{ submitting ? '登录中…' : '登录' }}</button>
        </form>
        <form v-else @submit.prevent="submitSms">
          <label>手机号<input v-model="smsForm.phone" maxlength="11" autocomplete="tel" placeholder="请输入手机号" type="tel" /></label>
          <label>验证码<span class="sms-field"><input v-model="smsForm.code" maxlength="6" inputmode="numeric" placeholder="六位验证码" /><button type="button" :disabled="!canSendSms" @click="requestSmsCode">{{ smsSending ? '发送中…' : countdown ? `${countdown}s` : '发送验证码' }}</button></span></label>
          <button class="primary" :disabled="!canSubmitSms" type="submit">{{ submitting ? '登录中…' : '登录' }}</button>
        </form>
        <p v-if="message" :class="['message', messageKind]" role="status">{{ message }}</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: 1.08fr .92fr; background: #f6f8fc; }.brand-panel { position: relative; display: grid; place-items: center; overflow: hidden; padding: 64px; color: #fff; background: radial-gradient(circle at 22% 18%, #2dd4bf55, transparent 30%), radial-gradient(circle at 78% 78%, #60a5fa55, transparent 32%), linear-gradient(145deg, #0b1739, #173f8a 58%, #0f766e); }.brand-panel::after { position: absolute; inset: 0; content: ''; opacity: .22; background-image: linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px); background-size: 54px 54px; mask-image: linear-gradient(120deg, black, transparent 78%); }.brand-content { position: relative; z-index: 1; max-width: 620px; }.brand-mark { width: 58px; height: 58px; display: grid; place-items: center; border: 1px solid #ffffff99; border-radius: 16px; background: #ffffff16; font-size: 28px; font-weight: 900; }.eyebrow { margin: 40px 0 8px; color: #bae6fd; font-size: 13px; font-weight: 800; letter-spacing: .18em; }h1 { margin: 0; font-size: clamp(62px, 8vw, 104px); line-height: 1; }.slogan { max-width: 590px; margin: 28px 0 0; font-size: clamp(24px, 2.8vw, 40px); font-weight: 750; line-height: 1.35; }.brand-tags { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 36px; }.brand-tags span { padding: 10px 16px; border: 1px solid #dbeafe66; border-radius: 999px; background: #0f172a33; font-weight: 700; }
.form-side { display: grid; place-items: center; padding: 40px; }.login-card { width: min(100%, 470px); padding: 42px; border: 1px solid #dfe5ef; border-radius: 24px; background: #fff; box-shadow: 0 28px 70px #17203318; }.mini-brand { color: #2563eb; font-weight: 900; }header h2 { margin: 22px 0 8px; font-size: 34px; }header p { margin: 0; color: #64748b; line-height: 1.6; }.tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 28px 0 24px; padding: 5px; border-radius: 12px; background: #eef2f7; }.tabs button { min-height: 42px; border: 0; border-radius: 9px; color: #64748b; background: transparent; font-weight: 750; cursor: pointer; }.tabs button.active { color: #1d4ed8; background: #fff; box-shadow: 0 3px 12px #17203316; }form { display: grid; gap: 18px; }label { display: grid; gap: 8px; color: #26334c; font-size: 14px; font-weight: 750; }input { width: 100%; min-width: 0; height: 52px; padding: 0 14px; border: 1px solid #cbd5e1; border-radius: 11px; color: #172033; background: #fff; box-sizing: border-box; }input:focus { border-color: #2563eb; outline: 3px solid #2563eb1f; }.password-field, .sms-field { display: grid; grid-template-columns: 1fr auto; gap: 8px; }.password-field button, .sms-field button { min-width: 74px; border: 1px solid #bfdbfe; border-radius: 11px; color: #1d4ed8; background: #eff6ff; font-weight: 750; cursor: pointer; }.sms-field button { min-width: 112px; }button:disabled { cursor: not-allowed; opacity: .55; }.primary { min-height: 52px; margin-top: 4px; border: 0; border-radius: 12px; color: #fff; background: linear-gradient(135deg, #2563eb, #0891b2); font-weight: 850; cursor: pointer; box-shadow: 0 14px 28px #2563eb38; }.primary:disabled { color: #64748b; background: #cbd5e1; box-shadow: none; }.message { margin: 18px 0 0; padding: 11px 13px; border-radius: 10px; font-size: 14px; font-weight: 700; }.message.error { color: #b42318; background: #fff1ef; }.message.success { color: #11723b; background: #edf9f1; }
@media (max-width: 900px) { .login-page { grid-template-columns: 1fr; }.brand-panel { min-height: 380px; padding: 36px 24px; }.form-side { padding: 34px 20px 52px; }h1 { font-size: 58px; }.slogan { font-size: 27px; } }@media (max-width: 520px) { .login-card { padding: 28px 20px; border-radius: 18px; }.tabs { grid-template-columns: 1fr; } }
</style>
