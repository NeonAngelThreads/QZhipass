import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginByEmailPassword, loginByPhonePassword, loginBySms, logoutPortal } from '../api/auth'
import { clearLoginInfo, readLoginInfo, type LoginInfo } from '../api/session'

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<LoginInfo | null>(readLoginInfo())

  async function phonePasswordLogin(phone: string, password: string) {
    profile.value = await loginByPhonePassword(phone, password)
  }

  async function emailPasswordLogin(email: string, password: string) {
    profile.value = await loginByEmailPassword(email, password)
  }

  async function smsLogin(phone: string, code: string) {
    profile.value = await loginBySms(phone, code)
  }

  async function logout() {
    await logoutPortal()
    clearLoginInfo()
    profile.value = null
  }

  return { profile, phonePasswordLogin, emailPasswordLogin, smsLogin, logout }
})
