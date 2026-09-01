<template>
  <header class="admin-topbar">
    <div class="title"><strong>管理中心</strong><span>统一维护组织、人员、权限与门户配置</span></div>
    <div class="actions">
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
import { useRouter } from 'vue-router'
import { clearAdminTokens, getAdminUsername } from '../../store/auth'

const router = useRouter()
const username = computed(() => getAdminUsername())
const avatarText = computed(() => String(username.value || '管').slice(0, 1).toUpperCase())

const logout = () => {
  clearAdminTokens()
  router.replace('/admin/login')
}
</script>
