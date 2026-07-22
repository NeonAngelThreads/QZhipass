<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const userId = computed(() => authStore.profile?.userId || '已登录用户')

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <main class="home-page">
    <header class="home-header">
      <BrandLogo tone="dark" size="md" />
      <el-button :icon="SwitchButton" plain @click="logout">退出登录</el-button>
    </header>

    <section class="home-content">
      <p class="eyebrow">QZhipass</p>
      <h1>企智通工作台</h1>
      <p class="welcome-text">欢迎，{{ userId }}。登录状态已保存。</p>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 28px clamp(20px, 5vw, 72px) 54px;
  color: #17233c;
  background:
    linear-gradient(90deg, rgba(0, 47, 167, 0.08) 1px, transparent 1px),
    linear-gradient(rgba(0, 47, 167, 0.08) 1px, transparent 1px),
    #f7f7f8;
  background-size: 48px 48px;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.home-content {
  width: min(100%, 780px);
  margin-top: clamp(86px, 14vw, 160px);
}

.eyebrow {
  margin: 0 0 14px;
  color: #002fa7;
  font-size: 14px;
  font-weight: 850;
}

.home-content h1 {
  margin: 0;
  font-size: clamp(48px, 9vw, 96px);
  line-height: 0.98;
  letter-spacing: 0;
}

.welcome-text {
  margin: 28px 0 0;
  color: #526176;
  font-size: 18px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .home-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
