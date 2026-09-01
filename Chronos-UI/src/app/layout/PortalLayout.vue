<template>
  <div class="portal-shell">
    <header class="portal-header">
      <RouterLink class="portal-brand" to="/portal"><span>C</span><strong>Chronos</strong><small>医院智慧办公</small></RouterLink>
      <nav>
        <RouterLink to="/portal">工作台</RouterLink>
        <RouterLink to="/portal/apps">应用中心</RouterLink>
        <RouterLink to="/portal/tasks">流程任务</RouterLink>
      </nav>
      <div class="portal-user">
        <div class="portal-user-copy"><strong>{{ user.displayName || username }}</strong><span>{{ user.organizationName || '未设置部门' }} · {{ user.positionName || '员工' }}</span></div>
        <button class="portal-avatar" @click="logout">{{ avatarText }}</button>
      </div>
    </header>
    <main class="portal-main"><RouterView @context="setContext" /></main>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { clearAdminTokens, getAdminUsername } from '../../store/auth'

const router = useRouter()
const username = getAdminUsername()
const user = reactive({})
const avatarText = computed(() => String(user.displayName || username || 'U').slice(0, 1).toUpperCase())
const setContext = (value) => Object.assign(user, value || {})
const logout = () => { clearAdminTokens(); router.replace('/login') }
</script>
