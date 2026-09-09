<template>
  <aside class="admin-sidebar" :class="{ collapsed }">
    <div class="brand">
      <RouterLink class="admin-brand" to="/admin/overview"><span>C</span><div><strong>Chronos</strong><small>{{ branding.portalSubtitle }}</small></div></RouterLink>
      <button class="collapse-btn" aria-label="收起导航" @click="collapsed = !collapsed">{{ collapsed ? '›' : '‹' }}</button>
    </div>
    <nav class="admin-nav">
      <template v-for="item in topLinks" :key="item.id || item.path">
        <RouterLink
          v-if="item.path"
          :to="item.path"
          class="admin-nav-item"
          :class="{ bubbling: bubbleKey === item.path }"
          @click="triggerBubble(item.path)"
        >
          <span class="label">{{ item.name }}</span>
          <span class="initial">{{ item.name.charAt(0) }}</span>
        </RouterLink>
        <div
          v-else
          class="admin-nav-item disabled-link"
          title="该菜单尚未配置页面路由"
        >
          <span class="label">{{ item.name }}</span>
          <span class="initial">{{ item.name.charAt(0) }}</span>
        </div>
      </template>

      <div v-for="group in groups" :key="group.name">
        <div
          class="admin-nav-item group-link"
          :class="{ bubbling: group.bubble }"
          @click="toggleGroup(group)"
        >
          <span class="label">{{ group.name }}</span>
          <span class="initial">{{ group.name.charAt(0) }}</span>
          <span class="caret" :class="{ open: group.open }">▾</span>
        </div>
        <div v-if="group.open" class="admin-nav-children">
          <template
            v-for="child in group.children"
            :key="child.id || child.path"
          >
            <RouterLink
              v-if="child.path"
              :to="child.path"
              class="admin-nav-item child"
              :class="{ active: isActive(child.path), bubbling: bubbleKey === child.path }"
              @click="triggerBubble(child.path)"
            >
              <span class="label">{{ child.name }}</span>
              <span class="initial">{{ child.name.charAt(0) }}</span>
            </RouterLink>
            <div
              v-else
              class="admin-nav-item child disabled-link"
              title="该菜单尚未配置页面路由"
            >
              <span class="label">{{ child.name }}</span>
              <span class="initial">{{ child.name.charAt(0) }}</span>
            </div>
          </template>
        </div>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { currentAdminNavigation } from '../../api/admin'
import { industryBranding } from '../../industries/core'

const route = useRoute()
const branding = industryBranding
const collapsed = ref(false)
const bubbleKey = ref('')

const topLinks = ref([])

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

const leafLinks = (nodes = []) => nodes.flatMap((item) => {
  const children = item.children || []
  if (children.length) return leafLinks(children)
  const path = mapAdminPath(item.path)
  return item.menuName ? [{ id: item.id, name: item.menuName, path }] : []
})

const normalizeMenus = (menus = []) => {
  const menuGroups = []
  const rootLinks = []
  menus.forEach((item) => {
    const children = leafLinks(item.children || [])
    if ((item.children || []).length) {
      menuGroups.push({ id: item.id, name: item.menuName, path: mapAdminPath(item.path), children })
    }
    else {
      const path = mapAdminPath(item.path)
      if (item.menuName) rootLinks.push({ id: item.id, name: item.menuName, path })
    }
  })
  return { menuGroups, rootLinks }
}

const groups = ref([])

onMounted(async () => {
  let menus = []
  try {
    const response = await currentAdminNavigation()
    if (response?.code === '200' && Array.isArray(response.data)) menus = response.data
  } catch {
    // 导航必须来自服务端的菜单与角色授权关系，请求失败时不展示固定菜单。
  }
  const normalized = normalizeMenus(menus)
  topLinks.value = normalized.rootLinks
  groups.value = normalized.menuGroups.map((group) => ({
    ...group,
    open: true,
    bubble: false,
    children: group.children || [],
  }))
})

const isActive = (path) => route.path.startsWith(path)
const triggerBubble = (key) => {
  bubbleKey.value = key
  setTimeout(() => {
    if (bubbleKey.value === key) {
      bubbleKey.value = ''
    }
  }, 450)
}
const toggleGroup = (group) => {
  group.open = !group.open
  group.bubble = true
  setTimeout(() => {
    group.bubble = false
  }, 450)
}
</script>

<style scoped>
.disabled-link {
  cursor: not-allowed;
  opacity: 0.58;
}
</style>
