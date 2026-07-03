<script setup>
import { ref, reactive, computed, watch } from 'vue'

// ==================== 配置 ====================
// 开发时指向后端地址；构建后若前后端同源可改为 ''
const API_BASE = 'http://localhost:7510'

// ==================== 状态定义 ====================
const showDialog = ref(true)
const submitLoading = ref(false)

//  密码显示/隐藏控制（默认 false = 隐藏 = 带斜杠眼睛）
const showPassword = ref(false)
const showConfirmPassword = ref(false)

// 表单数据
const form = reactive({
  name: '',
  department: '',
  email: '',
  phone: '',
  wechat: '',
  password: '',
  confirmPassword: ''
})

// 校验状态
const errors = reactive({
  name: '',
  department: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

// 手机号查重相关
const phoneCheckStatus = ref('') // '' | 'checking' | 'exists' | 'available' | 'restorable'
let phoneCheckTimer = null

// 恢复账号确认弹窗
const showRestoreConfirm = ref(false)
const restoreUserInfo = ref(null)

// ==================== 密码强度计算 ====================
const passwordStrength = computed(() => {
  const pwd = form.password
  if (!pwd) return { level: 0, text: '', color: '' }

  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z\d]/.test(pwd)) score++

  if (score <= 2) return { level: 1, text: '弱', color: '#ff4d4f' }
  if (score <= 3) return { level: 2, text: '中', color: '#faad14' }
  return { level: 3, text: '强', color: '#52c41a' }
})

const pwdRules = computed(() => ({
  length: form.password.length >= 8,
  lower: /[a-z]/.test(form.password),
  upper: /[A-Z]/.test(form.password),
  special: /[^a-zA-Z\d]/.test(form.password),
  number: /\d/.test(form.password)
}))

const isPasswordValid = computed(() =>
  pwdRules.value.length && pwdRules.value.lower && pwdRules.value.upper &&
  pwdRules.value.special && pwdRules.value.number
)

// ==================== 校验函数 ====================
const validateField = (field) => {
  switch (field) {
    case 'name':
      errors.name = form.name.trim() ? '' : '请输入姓名'
      break
    case 'department':
      errors.department = form.department ? '' : '请选择部门'
      break
    case 'email':
      if (!form.email) { errors.email = ''; return }
      errors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '' : '邮箱格式不正确'
      break
    case 'phone':
      if (!form.phone) { errors.phone = '请输入手机号'; return }
      errors.phone = /^1[3-9]\d{9}$/.test(form.phone) ? '' : '请输入有效的11位手机号'
      break
    case 'password':
      if (!form.password) { errors.password = '请输入密码'; return }
      errors.password = isPasswordValid.value ? '' : '密码需包含大小写字母、数字、特殊字符且≥8位'
      break
    case 'confirmPassword':
      if (!form.confirmPassword) { errors.confirmPassword = '请再次输入密码'; return }
      errors.confirmPassword = form.password === form.confirmPassword ? '' : '两次输入的密码不一致'
      break
  }
}

const validateAll = () => {
  Object.keys(errors).forEach(validateField)
  return !Object.values(errors).some(e => e !== '')
}

// ==================== 手机号防抖查重（真实 API） ====================
watch(() => form.phone, (val) => {
  clearTimeout(phoneCheckTimer)
  phoneCheckStatus.value = ''
  errors.phone = ''

  if (!/^1[3-9]\d{9}$/.test(val)) return

  phoneCheckStatus.value = 'checking'
  phoneCheckTimer = setTimeout(async () => {
    try {
      const resp = await fetch(`${API_BASE}/api/v1/account/check-phone?phone=${encodeURIComponent(val)}`)
      const data = await resp.json()
      if (data.cancelled === true) {
        // 已注销用户，可恢复
        phoneCheckStatus.value = 'restorable'
        restoreUserInfo.value = {
          name: data.name,
          department: data.department,
          deactivatedDate: data.cancelledAt ? data.cancelledAt.substring(0, 10) : ''
        }
        showRestoreConfirm.value = true
      } else if (data.exists === true) {
        // 已存在正常/冻结账户
        phoneCheckStatus.value = 'exists'
      } else {
        // 全新用户
        phoneCheckStatus.value = 'available'
      }
    } catch (e) {
      console.error('手机号查重失败', e)
      phoneCheckStatus.value = '' // 网络错误时不阻止用户操作
    }
  }, 500)
})

const confirmRestore = () => {
  if (restoreUserInfo.value) {
    form.name = restoreUserInfo.value.name || ''
    // 根据部门标签反查 value
    const dept = departmentOptions.find(d => d.label === restoreUserInfo.value.department)
    form.department = dept ? dept.value : ''
  }
  showRestoreConfirm.value = false
}

const cancelRestore = () => {
  showRestoreConfirm.value = false
  restoreUserInfo.value = null
  phoneCheckStatus.value = ''
}

// ==================== 部门选项 ====================
const departmentOptions = [
  { label: '技术研发部', value: 'tech' },
  { label: '产品设计部', value: 'product' },
  { label: '市场运营部', value: 'marketing' },
  { label: '人力资源部', value: 'hr' }
]

// 根据 value 获取 label（提交时把 value 转成后端需要的部门名称）
const getDepartmentLabel = (value) => {
  return departmentOptions.find(d => d.value === value)?.label || value
}

// ==================== 提交（真实 API） ====================
const handleSubmit = async () => {
  validateAll()
  if (!validateAll()) return
  if (phoneCheckStatus.value === 'exists') {
    errors.phone = '该手机号已创建账户'
    return
  }

  submitLoading.value = true
  try {
    const payload = {
      name: form.name.trim(),
      department: getDepartmentLabel(form.department),
      email: form.email.trim(),
      phone: form.phone.trim(),
      wechat: form.wechat.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword
    }

    const resp = await fetch(`${API_BASE}/api/v1/account/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    const data = await resp.json()

    if (resp.ok) {
      alert('✅ ' + (data.message || '创建用户成功！'))
      showDialog.value = false
    } else {
      // 根据后端错误响应显示提示
      const msg = data.message || (data.success === false ? data.message : '创建失败，请重试')
      alert('❌ ' + msg)
    }
  } catch (e) {
    console.error('提交失败', e)
    alert('❌ 网络错误，请检查后端服务是否启动')
  } finally {
    submitLoading.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" v-if="showDialog">
    <div class="modal-container">
      <!-- ====== 头部 ====== -->
      <div class="modal-header">
        <h2>添加新用户</h2>
        <span class="close-btn" @click="showDialog = false">×</span>
      </div>

      <!-- ====== 表单内容 ====== -->
      <div class="modal-body">
        <!-- 姓名 & 部门 -->
        <div class="form-row">
          <div class="form-item" :class="{ 'has-error': errors.name }">
            <label><span class="required">*</span> 姓名</label>
            <input v-model="form.name" type="text" placeholder="请输入姓名"
              @blur="validateField('name')" />
            <span class="error-msg" v-if="errors.name">{{ errors.name }}</span>
          </div>
          <div class="form-item" :class="{ 'has-error': errors.department }">
            <label><span class="required">*</span> 所在部门</label>
            <select v-model="form.department" @change="validateField('department')">
              <option value="" disabled>请选择部门</option>
              <option v-for="dept in departmentOptions" :key="dept.value" :value="dept.value">
                {{ dept.label }}
              </option>
            </select>
            <span class="error-msg" v-if="errors.department">{{ errors.department }}</span>
          </div>
        </div>

        <!-- 电子邮箱 -->
        <div class="form-item full-width" :class="{ 'has-error': errors.email }">
          <label>电子邮箱</label>
          <input v-model="form.email" type="email" placeholder="example@company.com（选填）"
            @blur="validateField('email')" />
          <span class="error-msg" v-if="errors.email">{{ errors.email }}</span>
        </div>

        <!-- 手机号 & 微信号 -->
        <div class="form-row">
          <div class="form-item" :class="{ 'has-error': errors.phone }">
            <label><span class="required">*</span> 手机号</label>
            <input v-model="form.phone" type="tel" placeholder="请输入11位手机号" maxlength="11"
              @blur="validateField('phone')" />
            <span class="error-msg" v-if="errors.phone">{{ errors.phone }}</span>
            <span class="status-msg checking" v-if="phoneCheckStatus === 'checking'">检查中...</span>
            <span class="status-msg exists" v-if="phoneCheckStatus === 'exists'">❌ 该用户已创建</span>
            <span class="status-msg available" v-if="phoneCheckStatus === 'available'">✅ 可用</span>
          </div>
          <div class="form-item">
            <label>微信号</label>
            <input v-model="form.wechat" type="text" placeholder="选填，仅用于联系" />
          </div>
        </div>

        <!--  登录密码 + 线性风格小眼睛图标（已反转：默认显示斜杠眼） -->
        <div class="form-item full-width" :class="{ 'has-error': errors.password }">
          <label><span class="required">*</span> 登录密码</label>
          <div class="password-input-wrapper">
            <input 
              v-model="form.password" 
              :type="showPassword ? 'text' : 'password'" 
              placeholder="请设置密码"
              @blur="validateField('password')" 
            />
            <!-- ✅ 未点击时 = 带斜杠的眼睛（隐藏态）；点击后 = 睁开的眼睛（明文态） -->
            <span class="eye-icon" @click="showPassword = !showPassword">
              <!-- 隐藏态：带斜杠的眼睛 -->
              <svg v-if="!showPassword" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
              </svg>
              <!-- 明文态：睁开的眼睛 -->
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </span>
          </div>
          <!-- 密码强度 -->
          <div class="strength-bar" v-if="form.password">
            <div class="strength-fill"
              :style="{ width: (passwordStrength.level / 3 * 100) + '%', backgroundColor: passwordStrength.color }">
            </div>
          </div>
          <span class="strength-text" v-if="form.password"
            :style="{ color: passwordStrength.color }">
            密码强度：{{ passwordStrength.text }}
          </span>
          <div class="password-rules">
            <span :class="{ passed: pwdRules.length }">✓ ≥8位</span>
            <span :class="{ passed: pwdRules.lower }">✓ 小写</span>
            <span :class="{ passed: pwdRules.upper }">✓ 大写</span>
            <span :class="{ passed: pwdRules.number }">✓ 数字</span>
            <span :class="{ passed: pwdRules.special }">✓ 特殊字符</span>
          </div>
          <span class="error-msg" v-if="errors.password">{{ errors.password }}</span>
        </div>

        <!-- 确认密码 + 同样线性风格小眼睛（已反转） -->
        <div class="form-item full-width" :class="{ 'has-error': errors.confirmPassword }">
          <label><span class="required">*</span> 确认密码</label>
          <div class="password-input-wrapper">
            <input 
              v-model="form.confirmPassword" 
              :type="showConfirmPassword ? 'text' : 'password'" 
              placeholder="请再次输入密码"
              @blur="validateField('confirmPassword')" 
            />
            <span class="eye-icon" @click="showConfirmPassword = !showConfirmPassword">
              <!-- 隐藏态：带斜杠的眼睛 -->
              <svg v-if="!showConfirmPassword" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
              </svg>
              <!-- 明文态：睁开的眼睛 -->
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </span>
          </div>
          <span class="error-msg" v-if="errors.confirmPassword">{{ errors.confirmPassword }}</span>
        </div>
      </div>

      <!-- ====== 底部按钮 ====== -->
      <div class="modal-footer">
        <button class="btn-cancel" @click="showDialog = false" :disabled="submitLoading">取消</button>
        <button class="btn-submit" @click="handleSubmit" :disabled="submitLoading">
          {{ submitLoading ? '创建中...' : '创建账户' }}
        </button>
      </div>
    </div>

    <!-- ====== 恢复账号确认弹窗 ====== -->
    <div class="restore-overlay" v-if="showRestoreConfirm" @click.self="cancelRestore">
      <div class="restore-dialog">
        <h3>⚠️ 检测到已注销账户</h3>
        <p>
          手机号 <strong>{{ form.phone }}</strong> 曾于
          <strong>{{ restoreUserInfo?.deactivatedDate }}</strong> 注销，
          历史数据仍被保留。
        </p>
        <p class="restore-hint">是否恢复该账户及其历史数据？此操作不可撤销。</p>
        <div class="restore-actions">
          <button class="btn-cancel" @click="cancelRestore">作为新用户创建</button>
          <button class="btn-restore" @click="confirmRestore">恢复历史账户</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========== 基础重置 & 遮罩 ========== */
* { box-sizing: border-box; margin: 0; padding: 0; }

.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* ========== 弹窗主体 ========== */
.modal-container {
  background: #fff; width: 620px; max-height: 90vh;
  border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  display: flex; flex-direction: column; overflow: hidden;
}

.modal-header {
  padding: 16px 24px; border-bottom: 1px solid #f0f0f0;
  display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;
}
.modal-header h2 { font-size: 16px; color: #333; font-weight: 600; }
.close-btn { cursor: pointer; font-size: 22px; color: #999; line-height: 1; }
.close-btn:hover { color: #333; }

.modal-body { padding: 24px; overflow-y: auto; flex: 1; }

/* ========== 表单布局 ========== */
.form-row { display: flex; gap: 20px; margin-bottom: 0; }
.form-item { flex: 1; display: flex; flex-direction: column; margin-bottom: 20px; position: relative; }
.full-width { margin-bottom: 20px; }

.required { color: #ff4d4f; margin-right: 2px; }

label { font-size: 14px; color: #333; margin-bottom: 6px; font-weight: 500; }

input, select {
  height: 36px; padding: 0 12px;
  border: 1px solid #d9d9d9; border-radius: 4px;
  font-size: 14px; outline: none; transition: all 0.3s;
  width: 100%; background: #fff;
}
input:focus, select:focus { border-color: #1890ff; box-shadow: 0 0 0 2px rgba(24,144,255,0.2); }
input::placeholder { color: #bbb; }

/* 错误状态 */
.has-error input, .has-error select { border-color: #ff4d4f; }
.has-error input:focus, .has-error select:focus { box-shadow: 0 0 0 2px rgba(255,77,79,0.2); }
.error-msg { font-size: 12px; color: #ff4d4f; margin-top: 4px; min-height: 18px; }

/* 手机号查重状态 */
.status-msg { font-size: 12px; margin-top: 4px; min-height: 18px; }
.status-msg.checking { color: #1890ff; }
.status-msg.exists { color: #ff4d4f; }
.status-msg.available { color: #52c41a; }

/* ========== 密码小眼睛（线性风格） ========== */
.password-input-wrapper {
  position: relative;
  width: 100%;
}

.password-input-wrapper input {
  padding-right: 32px; /* 为图标留出空间 */
}

.eye-icon {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;           /* 浅灰色线性风格 */
  width: 18px;
  height: 18px;
  transition: color 0.2s;
}

.eye-icon:hover {
  color: #595959;        /* hover 稍深，提升反馈 */
}

/* ========== 密码强度指示器 ========== */
.strength-bar {
  height: 4px; background: #f0f0f0; border-radius: 2px;
  margin-top: 8px; overflow: hidden;
}
.strength-fill { height: 100%; border-radius: 2px; transition: all 0.3s ease; }
.strength-text { font-size: 12px; margin-top: 4px; font-weight: 500; }

.password-rules {
  display: flex; gap: 12px; margin-top: 6px; flex-wrap: wrap;
}
.password-rules span {
  font-size: 12px; color: #bbb; transition: color 0.3s;
}
.password-rules span.passed { color: #52c41a; }

/* ========== 底部按钮 ========== */
.modal-footer {
  padding: 12px 24px 20px; display: flex;
  justify-content: flex-end; gap: 12px; flex-shrink: 0;
}

button {
  height: 36px; padding: 0 24px; border-radius: 4px;
  font-size: 14px; cursor: pointer; border: 1px solid transparent;
  transition: all 0.3s;
}
button:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-cancel { background: #fff; border-color: #d9d9d9; color: #333; }
.btn-cancel:hover:not(:disabled) { color: #1890ff; border-color: #1890ff; }

.btn-submit { background: #1890ff; color: #fff; }
.btn-submit:hover:not(:disabled) { background: #40a9ff; }

/* ========== 恢复确认弹窗 ========== */
.restore-overlay {
  position: absolute; inset: 0;
  background: rgba(0,0,0,0.3); border-radius: 8px;
  display: flex; justify-content: center; align-items: center;
  z-index: 10;
}
.restore-dialog {
  background: #fff; border-radius: 8px; padding: 24px;
  width: 400px; box-shadow: 0 4px 20px rgba(0,0,0,0.2);
}
.restore-dialog h3 { font-size: 16px; color: #faad14; margin-bottom: 12px; }
.restore-dialog p { font-size: 14px; color: #333; line-height: 1.8; }
.restore-hint { color: #ff4d4f !important; font-size: 13px !important; margin-top: 8px; }
.restore-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }
.btn-restore { background: #faad14; color: #fff; border: none; }
.btn-restore:hover { background: #ffc53d; }
</style>
