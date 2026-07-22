import axios from 'axios'
import { clearLoginInfo, readLoginInfo } from './session'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
  }
}

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const http = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000,
  withCredentials: true
})

http.interceptors.request.use(config => {
  if (apiBaseURL.replace(/\/+$/, '').endsWith('/api') && config.url?.startsWith('/api/')) {
    config.url = config.url.slice('/api'.length)
  }
  const token = readLoginInfo()?.accessToken
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error) && !error.config?.skipAuthRedirect && error.response?.status === 401) {
      clearLoginInfo()
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    return Promise.reject(error)
  }
)

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data
    if (payload && typeof payload === 'object' && 'message' in payload) {
      const message = payload.message
      if (typeof message === 'string' && message.trim()) return message
    }
    if (typeof payload === 'string' && payload.trim()) return payload
    if (error.message) return error.message
  }
  return error instanceof Error && error.message ? error.message : fallback
}

export default http
