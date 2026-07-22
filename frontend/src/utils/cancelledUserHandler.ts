import { ElMessageBox } from 'element-plus'

/**
 * 处理登录失败错误
 * - 注销用户（message 包含 "cancelled"）→ 弹出注销提示弹窗
 * - 其他错误 → 重新抛出，交由调用方处理
 *
 * 用法（在 LoginView.vue 的 catch 块中）：
 *   try {
 *     await login(...)
 *   } catch (error) {
 *     try {
 *       handleLoginError(error)
 *     } catch {
 *       ElMessage.error(error instanceof Error ? error.message : '登录失败')
 *     }
 *   }
 */
export function handleLoginError(error: unknown): void {
  const msg = error instanceof Error ? error.message : '登录失败'
  if (msg.toLowerCase().includes('cancelled')) {
    ElMessageBox.alert(
      '您的账号已被注销，如需恢复使用请联系管理员。',
      '账号已注销',
      {
        confirmButtonText: '我知道了',
        type: 'error'
      }
    )
  } else {
    throw error
  }
}
