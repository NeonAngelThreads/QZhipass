<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import BrandLogo from '../components/BrandLogo.vue'
import {
  EMAIL_BINDING_MESSAGES,
  EmailBindingApiError,
  getEmailBindingStatus,
  sendEmailBindingCode,
  verifyEmailBinding
} from '../api/emailBinding'
import { useCountdown } from '../composables/useCountdown'

const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const verifying = ref(false)
const dialogVisible = ref(false)
const bound = ref(false)
const maskedEmail = ref<string | null>(null)
const { seconds: cooldownSeconds, start: startCountdown } = useCountdown()
const form = reactive({
  email: '',
  code: ''
})

const bindingStateText = computed(() => bound.value ? '邮箱已绑定' : '未绑定')
const codeButtonText = computed(() => (
  cooldownSeconds.value > 0
    ? `${cooldownSeconds.value}秒后重新获取`
    : '获取验证码'
))
const canSendCode = computed(() => (
  form.email.trim().length > 0 &&
  cooldownSeconds.value === 0 &&
  !sendingCode.value
))
const canVerify = computed(() => (
  form.email.trim().length > 0 &&
  /^\d{6}$/.test(form.code) &&
  !verifying.value
))

async function loadStatus(showError = true) {
  loading.value = true
  try {
    const result = await getEmailBindingStatus()
    bound.value = result.data.bound
    maskedEmail.value = result.data.email
    startCountdown(result.data.cooldownSeconds)
  } catch (error) {
    if (showError) {
      ElMessage.error(error instanceof Error ? error.message : EMAIL_BINDING_MESSAGES.statusFailed)
    }
  } finally {
    loading.value = false
  }
}

function openBindingDialog() {
  form.email = ''
  form.code = ''
  dialogVisible.value = true
}

async function handleSendCode() {
  if (!canSendCode.value) {
    ElMessage.warning('请输入邮箱地址')
    return
  }

  sendingCode.value = true
  try {
    const result = await sendEmailBindingCode(form.email.trim())
    startCountdown(result.data.cooldownSeconds)
    ElMessage.success(result.message)
  } catch (error) {
    if (error instanceof EmailBindingApiError && error.cooldownSeconds > 0) {
      startCountdown(error.cooldownSeconds)
    }
    ElMessage.error(error instanceof Error ? error.message : EMAIL_BINDING_MESSAGES.sendFailed)
  } finally {
    sendingCode.value = false
  }
}

async function handleVerify() {
  if (!canVerify.value) {
    ElMessage.warning('请输入邮箱地址和6位邮箱验证码')
    return
  }

  verifying.value = true
  try {
    const result = await verifyEmailBinding(form.email.trim(), form.code)
    ElMessage.success(result.message)
    dialogVisible.value = false
    await loadStatus(false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : EMAIL_BINDING_MESSAGES.bindFailed)
  } finally {
    verifying.value = false
  }
}

function handleCodeInput(value: string) {
  form.code = value.replace(/\D/g, '').slice(0, 6)
}

onMounted(() => loadStatus())
</script>

<template>
  <el-config-provider :locale="zhCn">
    <main class="min-h-screen bg-gray-100 text-gray-800">
    <header class="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
      <BrandLogo tone="dark" size="sm" />
      <el-button plain @click="router.push('/chat')">返回对话</el-button>
    </header>

    <section class="mx-auto w-full max-w-3xl p-5 sm:p-6">
      <h1 class="mb-5 text-xl font-semibold text-gray-800">账号设置</h1>

      <div
        v-loading="loading"
        class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p class="text-sm font-semibold text-gray-800">邮箱绑定</p>
            <p class="mt-1 text-sm text-gray-500">
              {{ bindingStateText }}
              <span v-if="maskedEmail" class="ml-2">{{ maskedEmail }}</span>
            </p>
          </div>
          <el-button type="primary" @click="openBindingDialog">绑定邮箱</el-button>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      title="绑定邮箱"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" @submit.prevent="handleVerify">
        <el-form-item label="邮箱地址">
          <el-input
            v-model="form.email"
            maxlength="254"
            placeholder="请输入邮箱地址"
          />
        </el-form-item>

        <el-form-item label="邮箱验证码">
          <div class="flex w-full flex-col gap-3 sm:flex-row">
            <el-input
              :model-value="form.code"
              class="min-w-0 flex-1"
              inputmode="numeric"
              maxlength="6"
              placeholder="请输入邮箱验证码"
              @update:model-value="handleCodeInput"
            />
            <el-button
              :disabled="!canSendCode"
              :loading="sendingCode"
              @click="handleSendCode"
            >
              {{ codeButtonText }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!canVerify"
          :loading="verifying"
          @click="handleVerify"
        >
          确认绑定
        </el-button>
      </template>
    </el-dialog>
    </main>
  </el-config-provider>
</template>
