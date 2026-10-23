<template>
  <header class="admin-topbar">
    <div class="title"><strong>管理中心</strong><span>{{ branding.systemName }}</span></div>
    <div class="actions">
      <button v-if="agentAvailable" class="ghost agent-toggle" type="button" :class="{ active: agentOpen }" aria-label="智能体视图" title="展开或隐藏智能体视图" @click="$emit('toggle-agent')">
        <el-icon><MagicStick /></el-icon><span>智能体视图</span>
      </button>
      <RouterLink class="ghost portal-link" to="/portal">返回门户</RouterLink>
      <div class="user">
        <div class="avatar">{{ avatarText }}</div>
        <span>{{ username || '管理员' }}</span>
      </div>
      <button class="ghost danger" type="button" @click="logout">退出登录</button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { clearAdminTokens, getAdminUsername } from '../../store/auth'
import { industryBranding } from '../../industries/core'

const router = useRouter()
const branding = industryBranding
const username = computed(() => getAdminUsername())
const avatarText = computed(() => String(username.value || '管').slice(0, 1).toUpperCase())
defineProps({ agentOpen: Boolean, agentAvailable: Boolean })
defineEmits(['toggle-agent'])

const logout = () => {
  clearAdminTokens()
  router.replace('/admin/login')
}
</script>
