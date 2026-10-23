<template>
  <div class="admin-layout" :class="{ 'agent-view-open': canUseAgent && agentOpen }">
    <AdminSidebar />
    <div class="admin-content">
      <AdminTopbar :agent-open="agentOpen" :agent-available="canUseAgent" @toggle-agent="toggleAgent" />
      <main class="admin-main">
        <RouterView />
      </main>
    </div>
    <AgentWorkspaceDrawer v-if="canUseAgent" :open="agentOpen" @close="closeAgent" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AdminSidebar from './AdminSidebar.vue'
import AdminTopbar from './AdminTopbar.vue'
import AgentWorkspaceDrawer from '../../modules/agent/components/AgentWorkspaceDrawer.vue'
import { hasAdminPermission } from '../../store/auth'

const canUseAgent = hasAdminPermission('education:operation-agent:view', 'education:operation-agent:manage', 'education:operation-agent:review')
const agentOpen = ref(canUseAgent && localStorage.getItem('chronos.agent.view.open') === 'true')
const setAgentOpen = value => { agentOpen.value = value; localStorage.setItem('chronos.agent.view.open', String(value)) }
const toggleAgent = () => setAgentOpen(!agentOpen.value)
const closeAgent = () => setAgentOpen(false)
</script>
