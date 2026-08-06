import {createRouter, createWebHistory, type RouteRecordRaw} from 'vue-router'
import {readLoginInfo} from '../api/session'
import {
  ADMIN_ENTRY_PATH,
  getDefaultPathForRole,
  resolvePostLoginPath,
  USER_HOME_PATH
} from './access'

// 说明：按 views 文件夹里的页面逐个登记，全部用“懒加载”，
// 这样任何一个页面写错，只坏它自己，不会拖垮整站白屏。
const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue'), meta: { title: '登录', public: true } },
  { path: '/', name: 'Home', component: () => import('../views/HomeView.vue'), meta: { title: '首页', requiresAuth: true } },
  { path: '/chat', name: 'Chat', component: () => import('../views/ChatView.vue'), meta: { title: '对话', requiresAuth: true } },
  { path: '/admin', redirect: ADMIN_ENTRY_PATH, meta: { requiresAuth: true, adminOnly: true } },
  { path: '/admin/sensitive-words', name: 'SensitiveWords', component: () => import('../views/SensitiveWordsView.vue'), meta: { title: '敏感词监管', requiresAuth: true, adminOnly: true } },
  { path: '/admin/security-logs', name: 'SecurityLogs', component: () => import('../views/SecurityLogView.vue'), meta: { title: '触发日志', requiresAuth: true, adminOnly: true } },
  { path: '/admin/alerts', name: 'Alerts', component: () => import('../views/AlertCenterView.vue'), meta: { title: '告警中心', requiresAuth: true, adminOnly: true } },
  { path: '/admin/users', name: 'Users', component: () => import('../views/UserManagementView.vue'), meta: { title: '用户管理', requiresAuth: true, adminOnly: true } },
  { path: '/token', name: 'TokenQuota', component: () => import('../views/TokenQuotaView.vue'), meta: { title: 'Token 配额主控台', requiresAuth: true, adminOnly: true } },
  { path: '/:pathMatch(.*)*', redirect: (to) => {
    const loginInfo = readLoginInfo()
    return loginInfo
      ? getDefaultPathForRole(loginInfo.role)
      : { path: '/login', query: { redirect: to.fullPath } }
  } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL), // 报红就改成 createWebHistory()
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const loginInfo = readLoginInfo()

  if (to.path === '/login') {
    if (!loginInfo) return true
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : undefined
    return resolvePostLoginPath(loginInfo.role, redirect)
  }

  if (to.meta.requiresAuth && !loginInfo) {
    if (to.fullPath === USER_HOME_PATH) return '/login'
    return { path: '/login', query: { redirect: to.fullPath }, replace: true }
  }

  if (to.meta.adminOnly && loginInfo?.role !== 'ADMIN') {
    return USER_HOME_PATH
  }

  return true
})

router.afterEach((to) => {
  const t = to.meta?.title as string | undefined
  document.title = t ? `${t} · QZ-Intelipass` : 'QZ-Intelipass'
})

export default router
