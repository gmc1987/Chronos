<script setup>
import { ref } from 'vue'

defineProps({
  item: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  active: { type: Function, required: true },
  bubbleKey: String,
})
const emit = defineEmits(['navigate'])
const open = ref(false)
</script>

<template>
  <div v-if="item.children.length">
    <button type="button" class="admin-nav-item group-link" :class="{ child: depth > 0 }"
      :aria-expanded="open" :aria-label="item.name" @click="open = !open">
      <span class="label">{{ item.name }}</span>
      <span class="initial">{{ item.name.charAt(0) }}</span>
      <span class="caret" :class="{ open }">▾</span>
    </button>
    <div v-if="open" class="admin-nav-children">
      <AdminSidebarNode v-for="child in item.children" :key="child.id" :item="child" :depth="depth + 1"
        :active="active" :bubble-key="bubbleKey" @navigate="emit('navigate', $event)" />
    </div>
  </div>
  <RouterLink v-else-if="item.path" :to="item.path" class="admin-nav-item"
    active-class="admin-route-active" exact-active-class="admin-route-exact-active"
    :class="{ child: depth > 0, active: active(item.path), bubbling: bubbleKey === item.path }"
    @click="emit('navigate', item.path)">
    <span class="label">{{ item.name }}</span>
    <span class="initial">{{ item.name.charAt(0) }}</span>
  </RouterLink>
  <div v-else class="admin-nav-item disabled-link" :class="{ child: depth > 0 }" title="该菜单尚未配置页面路由">
    <span class="label">{{ item.name }}</span>
    <span class="initial">{{ item.name.charAt(0) }}</span>
  </div>
</template>

<style scoped>
.group-link { width: 100%; border: 0; background: transparent; text-align: left; }
.admin-nav-children { padding-inline-start: 8px; }
.disabled-link { cursor: not-allowed; opacity: 0.58; }
</style>
