<template>
  <aside class="admin-sidebar" :class="{ collapsed }">
    <div class="brand">
      <RouterLink class="admin-brand" to="/admin/overview"><span>C</span><div><strong>Chronos</strong><small>{{ branding.portalSubtitle }}</small></div></RouterLink>
      <button class="collapse-btn" aria-label="收起导航" @click="collapsed = !collapsed">{{ collapsed ? '›' : '‹' }}</button>
    </div>
    <nav class="admin-nav">
      <AdminSidebarNode v-for="item in menuItems" :key="item.id" :item="item"
        :active="isActive" :bubble-key="bubbleKey" @navigate="triggerBubble" />
    </nav>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminSidebarNode from './AdminSidebarNode.vue'
import { currentAdminNavigation } from '../../api/admin'
import { industryBranding } from '../../industries/core'

const route = useRoute()
const branding = industryBranding
const collapsed = ref(false)
const bubbleKey = ref('')

const menuItems = ref([])

const mapAdminPath = (path) => {
  if (!path) return ''
  if (path.startsWith('/admin')) return path
  if (path.startsWith('/system')) return path.replace('/system', '/admin')
  if (path.startsWith('/organization')) return path.replace('/organization', '/admin/organizations')
  if (path.startsWith('/customer')) return path.replace('/customer', '/admin/customers')
  if (path.startsWith('/ai-model')) return `/admin${path}`
  if (path.startsWith('/agent')) return `/admin${path}`
  if (path.startsWith('/workflow')) return `/admin${path}`
  return `/admin${path}`
}

const normalizeMenus = (menus = []) => menus.filter(item => item.menuName).map(item => ({
  id: item.id,
  name: item.menuName,
  path: mapAdminPath(item.path),
  children: normalizeMenus(item.children || []),
}))

onMounted(async () => {
  let menus = []
  try {
    const response = await currentAdminNavigation()
    if (response?.code === '200' && Array.isArray(response.data)) menus = response.data
  } catch {
    // 导航必须来自服务端的菜单与角色授权关系，请求失败时不展示固定菜单。
  }
  const nodes = normalizeMenus(menus)
  menuItems.value = [...nodes.filter(node => !node.children.length), ...nodes.filter(node => node.children.length)]
})

// 菜单路径只匹配自身及其真正的下级路由，避免 /homework 同时匹配 /homework-analysis。
const isActive = (path) => {
  if (!path) return false
  const normalizedPath = path.length > 1 ? path.replace(/\/+$/, '') : path
  return route.path === normalizedPath || route.path.startsWith(`${normalizedPath}/`)
}
const triggerBubble = (key) => {
  bubbleKey.value = key
  setTimeout(() => {
    if (bubbleKey.value === key) {
      bubbleKey.value = ''
    }
  }, 450)
}
</script>
