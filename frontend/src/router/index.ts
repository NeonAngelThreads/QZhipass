import {createRouter, createWebHistory} from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ChatView from '../views/ChatView.vue'
import SensitiveWordsView from '../views/SensitiveWordsView.vue'
import SecurityLogView from '../views/SecurityLogView.vue'
import AlertCenterView from '../views/AlertCenterView.vue'
import UserManagementView from '../views/UserManagementView.vue'
import AccountSettingsView from '../views/AccountSettingsView.vue'
import { isLoggedIn } from '../api/session'

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
      path: '/account/settings',
      name: 'account-settings',
      component: AccountSettingsView,
      beforeEnter: () => isLoggedIn()
        ? true
        : {
            name: 'login',
            query: { redirect: '/account/settings' }
          }
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
      path: '/admin/alerts',
      name: 'alerts',
      component: AlertCenterView
    },
    {
      path: '/admin/users',
      name: 'users',
      component: UserManagementView
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/chat'
    }
  ]
})

export default router
