import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../api/session'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ConversationView from '../views/ConversationView.vue'
import ChatView from '../views/ChatView.vue'
import SensitiveWordsView from '../views/SensitiveWordsView.vue'
import SecurityLogView from '../views/SecurityLogView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: () => (isLoggedIn() ? '/home' : '/login') },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
    {
      path: '/conversation/:conversationId?',
      name: 'conversation',
      component: ConversationView,
      meta: { requiresAuth: true }
    },
    { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } },
    {
      path: '/admin/sensitive-words',
      name: 'sensitive-words',
      component: SensitiveWordsView,
      meta: { requiresAuth: true }
    },
    {
      path: '/admin/security-logs',
      name: 'security-logs',
      component: SecurityLogView,
      meta: { requiresAuth: true }
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(to => {
  const authenticated = isLoggedIn()
  if (to.matched.some(record => record.meta.requiresAuth) && !authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && authenticated) return '/home'
  return true
})

export default router
