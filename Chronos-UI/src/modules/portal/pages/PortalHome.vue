<template>
  <div class="portal-page">
    <section class="portal-hero">
      <div><span>{{ greeting }}</span><h1>{{ bootstrap.user?.displayName || '欢迎使用统一门户' }}</h1><p>今天的工作，从清晰的一站式工作台开始。</p></div>
      <button @click="editing = !editing">{{ editing ? '完成设置' : '个性化设置' }}</button>
    </section>

    <section v-if="error" class="portal-state portal-state--error">{{ error }} <button @click="load">重新加载</button></section>
    <section v-else-if="loading" class="portal-state">正在加载工作台…</section>
    <template v-else>
      <div v-if="editing" class="portal-customize">
        <div><strong>工作台组件</strong><span>选择显示内容，并调整顺序</span></div>
        <div class="portal-customize-list">
          <label v-for="widget in bootstrap.widgets" :key="widget.code">
            <input v-model="draftLayout" type="checkbox" :value="widget.code" />{{ widget.name }}
            <button type="button" @click.prevent="move(widget.code, -1)">↑</button><button type="button" @click.prevent="move(widget.code, 1)">↓</button>
          </label>
        </div>
        <button class="portal-primary" @click="saveLayout">保存布局</button>
        <button class="portal-secondary" @click="resetLayout">恢复默认</button>
      </div>

      <section class="portal-favorites">
        <div class="portal-section-heading"><div><span>QUICK ACCESS</span><h2>常用应用</h2></div><RouterLink to="/portal/apps">全部应用 →</RouterLink></div>
        <div class="portal-app-row">
          <button v-for="app in quickApps" :key="app.id" class="portal-app-shortcut" @click="openApp(app)">
            <i>{{ app.name.slice(0, 1) }}</i><span><strong>{{ app.name }}</strong><small>{{ app.description }}</small></span>
          </button>
          <RouterLink v-if="!quickApps.length" class="portal-empty-shortcut" to="/portal/apps">添加常用应用</RouterLink>
        </div>
      </section>

      <section class="portal-grid">
        <article v-for="widget in visibleWidgets" :key="widget.code" class="portal-widget" :class="`portal-widget--${String(widget.defaultSize).toLowerCase()}`">
          <header><div><span>{{ widget.provider }}</span><h3>{{ widget.name }}</h3></div><button aria-label="更多">•••</button></header>
          <template v-if="widget.code === 'quick-entry'">
            <div class="portal-widget-links"><button v-for="app in quickApps.slice(0, 4)" :key="app.id" @click="openApp(app)">{{ app.name }}</button></div>
          </template>
          <template v-else-if="contribution(widget).available">
            <pre>{{ contribution(widget).data }}</pre>
          </template>
          <div v-else class="portal-widget-empty"><b>等待接入</b><p>{{ contribution(widget).message }}</p></div>
        </article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalBootstrap, resetPortalPreference, savePortalPreference, visitApplication } from '../../../api/portal'

const emit = defineEmits(['context'])
const router = useRouter()
const bootstrap = ref({ widgets: [], applications: [], favorites: [], preference: {}, contributions: {} })
const loading = ref(true); const error = ref(''); const editing = ref(false); const draftLayout = ref([])
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const quickApps = computed(() => bootstrap.value.favorites?.length ? bootstrap.value.favorites : bootstrap.value.applications?.filter(a => a.recommended).slice(0, 6))
const visibleWidgets = computed(() => draftLayout.value.map(code => bootstrap.value.widgets.find(w => w.code === code)).filter(Boolean))
const contribution = (widget) => bootstrap.value.contributions?.[widget.provider] || { available: false, message: '模块接口已预留' }
const load = async () => {
  loading.value = true; error.value = ''
  try { const res = await portalBootstrap(); bootstrap.value = res.data; draftLayout.value = [...(res.data.preference?.layout || [])]; emit('context', res.data.user) }
  catch (e) { error.value = e instanceof Error ? e.message : '门户加载失败' }
  finally { loading.value = false }
}
const move = (code, offset) => { const i = draftLayout.value.indexOf(code); const n = i + offset; if (i < 0 || n < 0 || n >= draftLayout.value.length) return; [draftLayout.value[i], draftLayout.value[n]] = [draftLayout.value[n], draftLayout.value[i]]; draftLayout.value = [...draftLayout.value] }
const saveLayout = async () => { await savePortalPreference({ layout: draftLayout.value, theme: bootstrap.value.preference?.theme || 'LIGHT' }); editing.value = false }
const resetLayout = async () => { const res = await resetPortalPreference(); draftLayout.value = [...res.data.layout] }
const openApp = async (app) => { await visitApplication(app.id); if (app.openMode === 'EXTERNAL') window.open(app.routePath, '_blank', 'noopener'); else router.push(app.routePath) }
onMounted(load)
</script>
