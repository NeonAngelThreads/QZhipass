/**
 * 自定义热键管理 — 对接后端 Hotkey 配置接口
 * GET  registry/hotkeys、registry/functions
 * GET/POST/PUT/DELETE user/config/hotkey
 */

import { ref, readonly, computed } from 'vue'
import {
  fetchHotkeyRegistry,
  fetchFunctionRegistry,
  getHotkeyBinding,
  bindOrUpdateHotkey,
  resetHotkeyBinding,
  FUNCTION_LABELS,
  FUNCTION_DESCRIPTIONS,
  DEFAULT_FUNC_KEY,
  normalizeKeyLabel,
  displayKeyLabel
} from '@/api/hotkey'

export interface HotkeyRow {
  /** 功能 id（后端） */
  funcId: number
  /** 功能 code，如 model.list */
  funcCode: string
  name: string
  description?: string
  /** 当前绑定的 keyId，未绑定为 null */
  keyId: number | null
  /** 当前热键展示字符串，如 #、Ctrl+Shift+A */
  keyLabel: string
  /** 是否已在服务端有绑定记录 */
  bound: boolean
}

export interface HotkeyOption {
  keyId: number
  label: string
  /** 规范化后的按键，用于匹配 */
  value: string
}

const rows = ref<HotkeyRow[]>([])
const keyOptions = ref<HotkeyOption[]>([])
const loading = ref(false)
const loaded = ref(false)

/** keyId → 原始 label */
const keyIdToLabel = ref<Record<number, string>>({})
/** 原始 label → keyId */
const labelToKeyId = ref<Record<string, number>>({})

export function displayKey(key: string): string {
  return displayKeyLabel(key)
}

export function eventMatchesKey(e: KeyboardEvent, binding: string): boolean {
  if (!binding) return false
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) {
    if (binding !== 'Escape' && e.key !== 'Escape') return false
  }

  const normalized = normalizeKeyLabel(binding)
  const parts = normalized.split('+').map(s => s.trim().toLowerCase())
  const needCtrl = parts.includes('control') || parts.includes('ctrl')
  const needAlt = parts.includes('alt')
  const needShift = parts.includes('shift')
  const needMeta = parts.includes('meta') || parts.includes('cmd')
  const main = parts.filter(
    p => !['control', 'ctrl', 'alt', 'shift', 'meta', 'cmd'].includes(p)
  )[0]

  if (!!e.ctrlKey !== needCtrl) return false
  if (!!e.altKey !== needAlt) return false
  if (!!e.shiftKey !== needShift) return false
  if (!!e.metaKey !== needMeta) return false

  if (main === 'escape' || main === 'esc') return e.key === 'Escape'
  if (main?.startsWith('f') && main.length <= 3) return e.key.toLowerCase() === main
  // 单字符 # ~ ! 等
  if (normalized.length === 1) return e.key === normalized
  return e.key.toLowerCase() === main?.toLowerCase()
}

async function loadRegistries() {
  const [hotkeysMap, funcsMap] = await Promise.all([
    fetchHotkeyRegistry(),
    fetchFunctionRegistry()
  ])

  const k2l: Record<number, string> = {}
  const l2k: Record<string, number> = {}
  const opts: HotkeyOption[] = []

  Object.entries(hotkeysMap).forEach(([idStr, label]) => {
    const id = Number(idStr)
    if (!Number.isFinite(id)) return
    k2l[id] = label
    l2k[label] = id
    l2k[normalizeKeyLabel(label)] = id
    opts.push({
      keyId: id,
      label: displayKeyLabel(label),
      value: label
    })
  })

  keyIdToLabel.value = k2l
  labelToKeyId.value = l2k
  keyOptions.value = opts.sort((a, b) => a.keyId - b.keyId)

  // 功能行
  const funcEntries = Object.entries(funcsMap)
    .map(([idStr, code]) => ({ funcId: Number(idStr), funcCode: code }))
    .filter(f => Number.isFinite(f.funcId))
    .sort((a, b) => a.funcId - b.funcId)

  // 并行查询每个功能的绑定
  const bindings = await Promise.all(
    funcEntries.map(async f => {
      try {
        return await getHotkeyBinding(f.funcId)
      } catch {
        return null
      }
    })
  )

  rows.value = funcEntries.map((f, i) => {
    const binding = bindings[i]
    const keyId = binding?.keyId ?? null
    let keyLabel = ''
    if (keyId != null && k2l[keyId]) {
      keyLabel = k2l[keyId]
    } else if (!binding) {
      // 未绑定：展示系统默认（仅 UI 提示，实际匹配仍以服务端为准）
      keyLabel = DEFAULT_FUNC_KEY[f.funcCode] || ''
    }
    return {
      funcId: f.funcId,
      funcCode: f.funcCode,
      name: FUNCTION_LABELS[f.funcCode] || f.funcCode,
      description: FUNCTION_DESCRIPTIONS[f.funcCode],
      keyId,
      keyLabel,
      bound: !!binding
    }
  })
}

export function useHotkeys() {
  async function load() {
    loading.value = true
    try {
      await loadRegistries()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * 修改绑定
   * @param funcId 功能 id
   * @param keyId 热键 id；null / 0 表示重置（DELETE）
   */
  async function updateBinding(funcId: number, keyId: number | null) {
    const row = rows.value.find(r => r.funcId === funcId)
    if (!row) return { ok: false as const, message: '未找到该功能' }

    // 冲突：同一 keyId 已被其他功能占用
    if (keyId != null) {
      const conflict = rows.value.find(
        r => r.funcId !== funcId && r.keyId === keyId
      )
      if (conflict) {
        return {
          ok: false as const,
          message: `热键「${displayKeyLabel(keyIdToLabel.value[keyId] || String(keyId))}」已被「${conflict.name}」占用`
        }
      }
    }

    try {
      if (keyId == null || keyId === 0) {
        await resetHotkeyBinding(funcId)
        row.keyId = null
        row.keyLabel = DEFAULT_FUNC_KEY[row.funcCode] || ''
        row.bound = false
        return { ok: true as const, message: '已重置该功能热键' }
      }

      await bindOrUpdateHotkey(funcId, keyId)
      row.keyId = keyId
      row.keyLabel = keyIdToLabel.value[keyId] || String(keyId)
      row.bound = true
      return { ok: true as const, message: '热键已保存' }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '保存失败'
      // 后端 409 Conflict Key Id
      if (/Conflict Key Id/i.test(msg)) {
        return { ok: false as const, message: '该热键已被其他功能占用' }
      }
      if (/already exists/i.test(msg)) {
        return { ok: false as const, message: '绑定已存在，请刷新后重试' }
      }
      return { ok: false as const, message: msg }
    }
  }

  /** 按功能 code 取当前热键字符串（供全局 keydown） */
  function getHotkeyByCode(funcCode: string): string {
    const row = rows.value.find(r => r.funcCode === funcCode)
    if (row?.keyLabel) return normalizeKeyLabel(row.keyLabel)
    return normalizeKeyLabel(DEFAULT_FUNC_KEY[funcCode] || '')
  }

  /** 兼容旧 id：model-list → model.list */
  const CODE_ALIAS: Record<string, string> = {
    'model-list': 'model.list',
    'create-agent': 'agent.create',
    'call-agent': 'agent.call',
    'close-agent-list': 'agent.list.close'
  }

  function matchEvent(e: KeyboardEvent, idOrCode: string): boolean {
    const code = CODE_ALIAS[idOrCode] || idOrCode
    return eventMatchesKey(e, getHotkeyByCode(code))
  }

  async function resetAll() {
    const results = await Promise.allSettled(
      rows.value.map(r => resetHotkeyBinding(r.funcId))
    )
    const failed = results.filter(r => r.status === 'rejected').length
    await loadRegistries()
    if (failed) {
      return { ok: false as const, message: `${failed} 项重置失败` }
    }
    return { ok: true as const, message: '已恢复默认（清除服务端绑定）' }
  }

  const options = computed(() =>
    keyOptions.value.map(o => ({
      label: o.label,
      value: String(o.keyId),
      keyId: o.keyId,
      raw: o.value
    }))
  )

  return {
    rows: readonly(rows),
    /** @deprecated 兼容旧视图名 */
    hotkeys: readonly(rows),
    options,
    keyOptions: readonly(keyOptions),
    loading: readonly(loading),
    loaded: readonly(loaded),
    load,
    updateBinding,
    resetAll,
    getHotkeyByCode,
    matchEvent,
    displayKey,
    keyIdToLabel: readonly(keyIdToLabel)
  }
}

// 重新导出，便于 ModelHotkeyPicker 等使用
export { eventMatchesKey, normalizeKeyLabel }
