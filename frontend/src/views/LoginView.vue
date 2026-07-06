<script setup lang="ts">
import {computed, onBeforeUnmount, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {Lock, Message, Phone, User} from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import {isValidMobile, sendSmsCode} from '../api/auth'
import {useAuthStore} from '../stores/auth'
// 注销用户弹窗处理：检测到 cancelled 错误时弹出注销提示，其他错误抛出交由原逻辑处理
import {handleLoginError} from '../utils/cancelledUserHandler'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loginMode = ref<'password' | 'sms'>('password')
const submitting = ref(false)
const smsSending = ref(false)
const countdown = ref(0)
const smsTimer = ref<number>()

const passwordForm = reactive({
  mobile: '',
  password: ''
})

const smsForm = reactive({
  mobile: '',
  smsCode: ''
})

const normalizedPasswordMobile = computed(() => passwordForm.mobile.trim())
const normalizedSmsMobile = computed(() => smsForm.mobile.trim())
const canSubmitPassword = computed(
  () => isValidMobile(normalizedPasswordMobile.value) && passwordForm.password.length > 0 && !submitting.value
)
const canSendSms = computed(() => isValidMobile(normalizedSmsMobile.value) && !smsSending.value && countdown.value === 0)
const canSubmitSms = computed(
  () => isValidMobile(normalizedSmsMobile.value) && smsForm.smsCode.trim().length === 6 && !submitting.value
)
const smsCodeButtonText = computed(() => (countdown.value > 0 ? `${countdown.value}s` : '获取验证码'))

async function redirectAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
  await router.push(redirect)
}

async function handlePasswordLogin() {
  if (!canSubmitPassword.value) {
    ElMessage.warning('请输入有效手机号和密码')
    return
  }

  submitting.value = true
  try {
    await authStore.passwordLogin(normalizedPasswordMobile.value, passwordForm.password)
    await redirectAfterLogin()
  } catch (error) {
    // 先交给注销用户处理器判断，是注销错误则弹窗，否则抛出走原逻辑
    try {
      handleLoginError(error)
    } catch {
      ElMessage.error(error instanceof Error ? error.message : '手机号或密码登录失败')
    }
  } finally {
    submitting.value = false
  }
}

function startCountdown() {
  countdown.value = 60

  if (smsTimer.value) {
    window.clearInterval(smsTimer.value)
  }

  smsTimer.value = window.setInterval(() => {
    countdown.value -= 1

    if (countdown.value <= 0 && smsTimer.value) {
      window.clearInterval(smsTimer.value)
      smsTimer.value = undefined
    }
  }, 1000)
}

async function handleSendSmsCode() {
  if (!canSendSms.value) {
    ElMessage.warning('请输入有效手机号')
    return
  }

  smsSending.value = true
  try {
    await sendSmsCode(normalizedSmsMobile.value)
    startCountdown()
    ElMessage.success('验证码已发送')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证码发送失败')
  } finally {
    smsSending.value = false
  }
}

async function handleSmsLogin() {
  if (!canSubmitSms.value) {
    ElMessage.warning('请输入有效手机号和 6 位验证码')
    return
  }

  submitting.value = true
  try {
    await authStore.smsLogin(normalizedSmsMobile.value, smsForm.smsCode.trim())
    await redirectAfterLogin()
  } catch (error) {
    // 先交给注销用户处理器判断，是注销错误则弹窗，否则抛出走原逻辑
    try {
      handleLoginError(error)
    } catch {
      ElMessage.error(error instanceof Error ? error.message : '验证码登录失败')
    }
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  if (smsTimer.value) {
    window.clearInterval(smsTimer.value)
  }
})
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="企智通平台">
      <div class="grid-layer" aria-hidden="true"></div>
      <div class="brand-content">
        <BrandLogo tone="light" size="lg" />
        <p class="brand-kicker">QZhipass</p>
        <h1>企业智能通行入口</h1>
        <p class="brand-description">连接企业账号与智能工作台，登录后进入企智通系统首页。</p>
      </div>
    </section>

    <section class="login-side" aria-label="登录区域">
      <div class="login-card" data-testid="login-card">
        <header class="login-header">
          <BrandLogo tone="dark" size="sm" />
          <h2>登录企智通</h2>
          <p>使用手机号登录您的账号</p>
        </header>

        <div class="mode-switch" aria-label="登录方式">
          <button type="button" :class="{ active: loginMode === 'password' }" @click="loginMode = 'password'">
            密码登录
          </button>
          <button type="button" :class="{ active: loginMode === 'sms' }" @click="loginMode = 'sms'">
            验证码登录
          </button>
        </div>

        <el-form v-if="loginMode === 'password'" class="login-form" @submit.prevent="handlePasswordLogin">
          <label class="field-label" for="password-mobile">手机号</label>
          <el-input
            id="password-mobile"
            v-model="passwordForm.mobile"
            :prefix-icon="Phone"
            autocomplete="username"
            clearable
            maxlength="11"
            placeholder="请输入手机号"
            size="large"
          />

          <label class="field-label" for="password-value">密码</label>
          <el-input
            id="password-value"
            v-model="passwordForm.password"
            :prefix-icon="Lock"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            size="large"
            type="password"
          />

          <el-button
            class="login-button"
            color="#002fa7"
            data-testid="password-login-button"
            :disabled="!canSubmitPassword"
            :loading="submitting"
            native-type="submit"
            size="large"
            type="primary"
          >
            登录
          </el-button>
        </el-form>

        <el-form v-else class="login-form" @submit.prevent="handleSmsLogin">
          <label class="field-label" for="sms-mobile">手机号</label>
          <el-input
            id="sms-mobile"
            v-model="smsForm.mobile"
            :prefix-icon="User"
            autocomplete="tel"
            clearable
            maxlength="11"
            placeholder="请输入手机号"
            size="large"
          />

          <label class="field-label" for="sms-code">验证码</label>
          <div class="sms-row">
            <el-input
              id="sms-code"
              v-model="smsForm.smsCode"
              :prefix-icon="Message"
              maxlength="6"
              placeholder="请输入验证码"
              size="large"
            />
            <el-button
              class="sms-code-button"
              :disabled="!canSendSms"
              :loading="smsSending"
              size="large"
              type="primary"
              plain
              @click="handleSendSmsCode"
            >
              {{ smsCodeButtonText }}
            </el-button>
          </div>

          <el-button
            class="login-button"
            color="#002fa7"
            data-testid="sms-login-button"
            :disabled="!canSubmitSms"
            :loading="submitting"
            native-type="submit"
            size="large"
            type="primary"
          >
            登录
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 0.9fr);
  overflow-x: hidden;
  color: #17233c;
  background: #f7f7f8;
}

.brand-panel {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(40px, 7vw, 88px);
  overflow: hidden;
  color: #ffffff;
  background: #002fa7;
}

.brand-panel::after {
  position: absolute;
  inset: 0;
  content: '';
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.12) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.12) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.92), rgba(0, 0, 0, 0.22));
}

.grid-layer {
  position: absolute;
  inset: 10% 8%;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 8px;
}

.brand-content {
  position: relative;
  z-index: 1;
  width: min(100%, 560px);
  text-align: left;
}

.brand-kicker {
  margin: 40px 0 14px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  font-weight: 800;
}

.brand-content h1 {
  margin: 0;
  font-size: clamp(40px, 6vw, 76px);
  line-height: 1.02;
  letter-spacing: 0;
}

.brand-description {
  max-width: 520px;
  margin: 24px 0 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 17px;
  line-height: 1.75;
}

.login-side {
  min-width: 0;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(34px, 6vw, 72px);
}

.login-card {
  width: min(100%, 430px);
  min-width: 0;
}

.login-header {
  margin-bottom: 28px;
}

.login-header h2 {
  margin: 28px 0 8px;
  font-size: 36px;
  line-height: 1.12;
  letter-spacing: 0;
}

.login-header p {
  margin: 0;
  color: #64748b;
  font-size: 15px;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 24px;
  padding: 4px;
  border: 1px solid #d8e2ee;
  border-radius: 8px;
  background: #edf2f7;
}

.mode-switch button {
  min-width: 0;
  min-height: 42px;
  border: 0;
  border-radius: 6px;
  color: #526176;
  background: transparent;
  font: inherit;
  font-weight: 760;
  cursor: pointer;
}

.mode-switch button.active {
  color: #002fa7;
  background: #ffffff;
  box-shadow: 0 0 0 1px rgba(0, 47, 167, 0.12);
}

.login-form {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.field-label {
  margin: 0 0 8px;
  color: #233149;
  font-size: 14px;
  font-weight: 760;
}

.field-label:not(:first-child) {
  margin-top: 18px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 52px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d8e2ee inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #002fa7 inset;
}

.login-button {
  width: 100%;
  min-height: 52px;
  margin-top: 26px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 850;
}

.sms-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 124px;
  gap: 10px;
}

.sms-code-button {
  min-height: 52px;
  border-radius: 8px;
  font-weight: 760;
  white-space: normal;
}

@media (max-width: 820px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .brand-panel {
    min-height: 280px;
    place-items: end start;
    padding: 34px 24px;
  }

  .brand-content h1 {
    font-size: 36px;
  }

  .brand-description {
    max-width: 620px;
    margin-top: 14px;
    font-size: 15px;
  }

  .login-side {
    min-height: auto;
    place-items: start center;
    padding: 34px 20px 44px;
  }
}

@media (max-width: 460px) {
  .login-header h2 {
    font-size: 30px;
  }

  .mode-switch {
    grid-template-columns: minmax(0, 1fr);
  }

  .sms-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .sms-code-button {
    width: 100%;
  }
}
</style>
