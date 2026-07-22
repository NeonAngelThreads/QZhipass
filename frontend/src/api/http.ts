import axios from 'axios'
import { clearLoginInfo } from './session'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  withCredentials: true
})

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data

    if (responseData && typeof responseData === 'object' && 'message' in responseData) {
      const message = responseData.message

      if (typeof message === 'string' && message.trim()) {
        return message
      }
    }

    if (typeof responseData === 'string' && responseData.trim()) {
      return responseData
    }

    if (error.message) {
      return error.message
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

http.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearLoginInfo()

      // DEV: 临时禁用登录跳转
      // if (window.location.pathname !== '/login') {
      //   window.location.assign('/login')
      // }
    }

    return Promise.reject(error)
  }
)

export default http
