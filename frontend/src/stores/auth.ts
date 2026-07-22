import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clearLoginInfo, isLoggedIn, readLoginInfo, type LoginInfo } from '../api/session'
import { loginByPassword, loginBySms } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<LoginInfo | null>(readLoginInfo())
  const loggedIn = ref(isLoggedIn())

  function setLoginState(data: LoginInfo) {
    profile.value = data
    loggedIn.value = true
  }

  async function passwordLogin(mobile: string, password: string) {
    const data = await loginByPassword(mobile, password)
    setLoginState(data)
    return data
  }

  async function smsLogin(mobile: string, smsCode: string) {
    const data = await loginBySms(mobile, smsCode)
    setLoginState(data)
    return data
  }

  function refreshLoginState() {
    profile.value = readLoginInfo()
    loggedIn.value = isLoggedIn()
  }

  function logout() {
    clearLoginInfo()
    profile.value = null
    loggedIn.value = false
  }

  return {
    profile,
    loggedIn,
    passwordLogin,
    smsLogin,
    refreshLoginState,
    logout
  }
})
