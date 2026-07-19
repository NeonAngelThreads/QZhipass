import {createRouter, createWebHistory} from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ChatView from '../views/ChatView.vue'
import SensitiveWordsView from '../views/SensitiveWordsView.vue'
import SecurityLogView from '../views/SecurityLogView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat'
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView
    },
    {
      path: '/chat',
      name: 'chat',
      component: ChatView
    },
    {
      path: '/admin/sensitive-words',
      name: 'sensitive-words',
      component: SensitiveWordsView
    },
    {
      path: '/admin/security-logs',
      name: 'security-logs',
      component: SecurityLogView
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/chat'
    }
  ]
})

export default router
