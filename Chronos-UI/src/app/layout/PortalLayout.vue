<template>
  <div class="portal-shell" :class="{ 'agent-view-open': agentOpen }">
    <header class="portal-header">
      <RouterLink class="portal-brand" to="/portal"><span>C</span><strong>Chronos</strong><small>{{ branding.portalSubtitle }}</small></RouterLink>
      <nav>
        <RouterLink to="/portal">工作台</RouterLink>
        <RouterLink to="/portal/apps">应用中心</RouterLink>
        <RouterLink to="/portal/tasks">流程任务</RouterLink>
      </nav>
      <div class="portal-user">
        <button v-if="canUseAgent" class="portal-agent-toggle" type="button" :class="{ active: agentOpen }" aria-label="智能体视图" title="展开或隐藏智能体视图" @click="toggleAgent">
          <el-icon><MagicStick /></el-icon><span>智能体视图</span>
        </button>
        <div class="portal-user-copy"><strong>{{ user.displayName || username }}</strong><span>{{ user.organizationName || `未设置${branding.departmentLabel}` }} · {{ positionLabel }}</span></div>
        <button class="portal-avatar" @click="logout">{{ avatarText }}</button>
      </div>
    </header>
    <main class="portal-main"><RouterView @context="setContext" /></main>
    <AgentWorkspaceDrawer v-if="canUseAgent" :open="agentOpen" @close="closeAgent" />
    <nav class="portal-mobile-nav" aria-label="移动端主导航">
      <RouterLink to="/portal"><span aria-hidden="true">⌂</span><small>工作台</small></RouterLink>
      <RouterLink to="/portal/apps"><span aria-hidden="true">▦</span><small>应用</small></RouterLink>
      <RouterLink to="/portal/tasks"><span aria-hidden="true">✓</span><small>待办</small></RouterLink>
      <button type="button" @click="logout"><span aria-hidden="true">{{ avatarText }}</span><small>退出</small></button>
    </nav>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAdminTokens, getAdminUsername, hasAdminPermission } from '../../store/auth'
import { industryBranding } from '../../industries/core'
import { portalBootstrap } from '../../api/portal'
import AgentWorkspaceDrawer from '../../modules/agent/components/AgentWorkspaceDrawer.vue'

const router = useRouter()
const route = useRoute()
const branding = industryBranding
const username = getAdminUsername()
const user = reactive({})
const canUseAgent = hasAdminPermission('education:operation-agent:view', 'education:operation-agent:manage', 'education:operation-agent:review')
const agentOpen = ref(canUseAgent && localStorage.getItem('chronos.agent.view.open') === 'true')
const setAgentOpen = value => { agentOpen.value = value; localStorage.setItem('chronos.agent.view.open', String(value)) }
const toggleAgent = () => setAgentOpen(!agentOpen.value)
const closeAgent = () => setAgentOpen(false)
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
