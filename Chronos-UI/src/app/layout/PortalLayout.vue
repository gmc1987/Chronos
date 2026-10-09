<template>
  <div class="portal-shell">
    <header class="portal-header">
      <RouterLink class="portal-brand" to="/portal"><span>C</span><strong>Chronos</strong><small>{{ branding.portalSubtitle }}</small></RouterLink>
      <nav>
        <RouterLink to="/portal">工作台</RouterLink>
        <RouterLink to="/portal/apps">应用中心</RouterLink>
        <RouterLink to="/portal/tasks">流程任务</RouterLink>
      </nav>
      <div class="portal-user">
        <div class="portal-user-copy"><strong>{{ user.displayName || username }}</strong><span>{{ user.organizationName || `未设置${branding.departmentLabel}` }} · {{ positionLabel }}</span></div>
        <button class="portal-avatar" @click="logout">{{ avatarText }}</button>
      </div>
    </header>
    <main class="portal-main"><RouterView @context="setContext" /></main>
    <nav class="portal-mobile-nav" aria-label="移动端主导航">
      <RouterLink to="/portal"><span aria-hidden="true">⌂</span><small>工作台</small></RouterLink>
      <RouterLink to="/portal/apps"><span aria-hidden="true">▦</span><small>应用</small></RouterLink>
      <RouterLink to="/portal/tasks"><span aria-hidden="true">✓</span><small>待办</small></RouterLink>
      <button type="button" @click="logout"><span aria-hidden="true">{{ avatarText }}</span><small>退出</small></button>
    </nav>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAdminTokens, getAdminUsername } from '../../store/auth'
import { industryBranding } from '../../industries/core'
import { portalBootstrap } from '../../api/portal'

const router = useRouter()
const route = useRoute()
const branding = industryBranding
const username = getAdminUsername()
const user = reactive({})
const avatarText = computed(() => String(user.displayName || username || 'U').slice(0, 1).toUpperCase())
const positionLabel = computed(() => {
  if (user.positionName) return user.positionName
  if (user.profileTypes?.includes('STUDENT')) return '学生'
  if (user.profileTypes?.includes('PARENT')) return '家长'
  if (user.profileTypes?.includes('TEACHER')) return '教师'
  return branding.employeeLabel
})
const setContext = (value) => Object.assign(user, value || {})
onMounted(async () => {
  // The home page supplies this context itself. A direct link to a portal feature does not.
  if (route.path === '/portal') return
  try {
    const { data } = await portalBootstrap()
    setContext({
      ...data.user,
      profileTypes: data.contributions?.DATA?.data?.profileTypes || [],
    })
  } catch {
    // Feature pages handle their own load errors; keep the portal shell available.
  }
})
const logout = () => { clearAdminTokens(); router.replace('/login') }
</script>
