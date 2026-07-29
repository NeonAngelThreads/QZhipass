import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearLoginInfo,
  isAdmin,
  readLoginInfo,
  saveLoginInfo
} from './session'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()

  get length() {
    return this.values.size
  }

  clear() {
    this.values.clear()
  }

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  key(index: number) {
    return Array.from(this.values.keys())[index] ?? null
  }

  removeItem(key: string) {
    this.values.delete(key)
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }
}

describe('登录角色会话', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {
        localStorage: new MemoryStorage()
      }
    })
  })

  it('普通用户登录后不是管理员', () => {
    saveLoginInfo({ userId: '1', accessToken: 'token', role: 'USER' })
    expect(isAdmin()).toBe(false)
  })

  it('管理员登录并重新读取后仍是管理员', () => {
    saveLoginInfo({ userId: '2', accessToken: 'token', role: 'ADMIN' })
    expect(readLoginInfo()?.role).toBe('ADMIN')
    expect(isAdmin()).toBe(true)
  })

  it('退出登录后清除管理员角色', () => {
    saveLoginInfo({ userId: '2', accessToken: 'token', role: 'ADMIN' })
    clearLoginInfo()
    expect(isAdmin()).toBe(false)
  })

  it('非法角色字符串不会获得管理员权限', () => {
    window.localStorage.setItem('user_id', '3')
    window.localStorage.setItem('access_token', 'token')
    window.localStorage.setItem('user_role', 'ROOT')
    expect(readLoginInfo()?.role).toBeUndefined()
    expect(isAdmin()).toBe(false)
  })

  it('缺少角色字段时不会报错或获得管理员权限', () => {
    saveLoginInfo({ userId: '4', accessToken: 'token' })
    expect(() => isAdmin()).not.toThrow()
    expect(isAdmin()).toBe(false)
  })
})
