<template>
  <div class="portal-page">
    <section class="portal-title"><div><span>APPLICATION CENTER</span><h1>应用中心</h1><p>你能够访问的所有医院协同应用。</p></div><input v-model="keyword" placeholder="搜索应用" /></section>
    <section v-if="error" class="portal-state portal-state--error">{{ error }}</section>
    <section class="portal-app-grid">
      <article v-for="app in filtered" :key="app.id" class="portal-app-card">
        <i>{{ app.name.slice(0, 1) }}</i><div><h3>{{ app.name }}</h3><p>{{ app.description }}</p><small>{{ app.code }}</small></div>
        <button class="portal-favorite" :class="{ active: app.favorite }" @click="toggle(app)">{{ app.favorite ? '★' : '☆' }}</button>
        <button class="portal-open" @click="open(app)">进入应用</button>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApplication, portalApplications, unfavoriteApplication, visitApplication } from '../../../api/portal'
const router = useRouter(); const apps = ref([]); const keyword = ref(''); const error = ref('')
const filtered = computed(() => apps.value.filter(a => `${a.name}${a.description}${a.code}`.toLowerCase().includes(keyword.value.toLowerCase())))
const load = async () => { try { apps.value = (await portalApplications()).data || [] } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } }
const toggle = async (app) => { app.favorite ? await unfavoriteApplication(app.id) : await favoriteApplication(app.id); app.favorite = !app.favorite }
const open = async (app) => { await visitApplication(app.id); app.openMode === 'EXTERNAL' ? window.open(app.routePath, '_blank', 'noopener') : router.push(app.routePath) }
onMounted(load)
</script>
