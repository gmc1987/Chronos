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
          <header><div><span>{{ widget.provider }}</span><h3>{{ widget.name }}</h3></div><button aria-label="更多" @click="openContribution(widget)">•••</button></header>
          <template v-if="widget.code === 'quick-entry'">
            <div class="portal-widget-links"><button v-for="app in quickApps.slice(0, 4)" :key="app.id" @click="openApp(app)">{{ app.name }}</button></div>
          </template>
          <template v-else-if="widget.code === 'todo' && contribution(widget).available">
            <div class="workflow-summary">
              <strong>{{ contribution(widget).data.total || 0 }}</strong><span>条待办</span>
              <em v-if="contribution(widget).data.overdue">{{ contribution(widget).data.overdue }} 条逾期</em>
              <em v-if="contribution(widget).data.claimable">{{ contribution(widget).data.claimable }} 条待认领</em>
            </div>
            <button
              v-for="item in contribution(widget).data.items || []"
              :key="item.taskId"
              class="workflow-item"
              @click="openTodo(item)"
            >
              <span><b>{{ item.flowName }}</b><small>{{ item.nodeName }} · {{ item.businessKey || '无业务编号' }}</small></span>
              <i :class="{ overdue: ['OVERDUE', 'ESCALATED'].includes(item.slaStatus) }">{{ item.claimable ? '待认领' : item.slaStatus === 'NORMAL' ? '待处理' : item.slaStatus }}</i>
            </button>
            <div v-if="!(contribution(widget).data.items || []).length" class="workflow-empty">暂无待办任务</div>
          </template>
          <template v-else-if="widget.code === 'supervision' && contribution(widget).available">
            <div class="workflow-summary">
              <strong>{{ contribution(widget).data.total || 0 }}</strong><span>个运行中流程</span>
              <em v-if="contribution(widget).data.overdue">{{ contribution(widget).data.overdue }} 个已逾期</em>
            </div>
            <div v-for="item in contribution(widget).data.items || []" :key="item.instanceId" class="workflow-item workflow-item--supervision" @click="router.push(item.route)">
              <span><b>{{ item.flowName }}</b><small>{{ item.businessKey || '无业务编号' }} · {{ item.pendingCount }} 个当前任务</small></span>
              <button v-if="canRemind && item.canRemind" @click.stop="remind(item)">催办</button>
            </div>
            <div v-if="!(contribution(widget).data.items || []).length" class="workflow-empty">暂无运行中的发起流程</div>
          </template>
          <template v-else-if="['announcement', 'notice'].includes(widget.code) && contribution(widget).available">
            <div class="workflow-summary">
              <strong>{{ contribution(widget).data.total || 0 }}</strong><span>条内容</span>
              <em v-if="contribution(widget).data.unread">{{ contribution(widget).data.unread }} 条未读</em>
            </div>
            <button
              v-for="item in contribution(widget).data.items || []"
              :key="item.id"
              class="workflow-item"
              @click="router.push(`/portal/publications/${item.id}`)"
            >
              <span><b>{{ item.title }}</b><small>{{ item.summary || '暂无摘要' }}</small></span>
              <i :class="{ overdue: !item.read }">{{ item.read ? '已读' : '未读' }}</i>
            </button>
            <div v-if="!(contribution(widget).data.items || []).length" class="workflow-empty">暂无可见内容</div>
          </template>
          <template v-else-if="widget.code === 'data-card' && contribution(widget).available">
            <div class="education-metrics">
              <div v-for="metric in contribution(widget).data.metrics || []" :key="metric.label">
                <strong>{{ metric.value }}</strong><span>{{ metric.label }}</span>
              </div>
            </div>
            <div v-if="(contribution(widget).data.mySchedule || []).length" class="education-schedule">
              <b>我的课表 · {{ contribution(widget).data.termName }}</b>
              <button v-for="course in contribution(widget).data.mySchedule.slice(0, 5)" :key="course.id" @click="router.push('/admin/education/scheduling')">
                <span>{{ weekday(course.dayOfWeek) }} 第 {{ course.periodNo }} 节</span>
                <strong>{{ course.courseName }}</strong>
                <small>{{ course.teachingClassName }}</small>
              </button>
            </div>
          </template>
          <template v-else-if="widget.code === 'ai-assistant' && contribution(widget).available">
            <div class="education-ai">
              <strong>{{ contribution(widget).data.title }}</strong>
              <p>{{ contribution(widget).data.description }}</p>
              <button v-for="example in contribution(widget).data.examples || []" :key="example" @click="router.push(contribution(widget).data.allRoute)">{{ example }}</button>
              <button class="education-ai__open" @click="router.push(contribution(widget).data.allRoute)">打开知识助手</button>
            </div>
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
import { ElMessage } from 'element-plus'
import { portalBootstrap, resetPortalPreference, savePortalPreference, visitApplication } from '../../../api/portal'
import { remindWorkflowInstance } from '../../../api/admin'

const emit = defineEmits(['context'])
const router = useRouter()
const bootstrap = ref({ widgets: [], applications: [], favorites: [], preference: {}, contributions: {} })
const loading = ref(true); const error = ref(''); const editing = ref(false); const draftLayout = ref([])
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const quickApps = computed(() => bootstrap.value.favorites?.length ? bootstrap.value.favorites : bootstrap.value.applications?.filter(a => a.recommended).slice(0, 6))
const visibleWidgets = computed(() => draftLayout.value.map(code => bootstrap.value.widgets.find(w => w.code === code)).filter(Boolean))
const canRemind = computed(() => (bootstrap.value.user?.permissions || []).includes('workflow:task:remind'))
const contribution = (widget) => bootstrap.value.contributions?.[widget.provider] || { available: false, message: '模块接口已预留' }
const weekday = value => ['一', '二', '三', '四', '五', '六', '日'][Number(value) - 1] ? `星期${['一', '二', '三', '四', '五', '六', '日'][Number(value) - 1]}` : '未排时间'
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
const openContribution = widget => {
  const route = contribution(widget).data?.allRoute
  if (route) router.push(route)
}
// 候选任务在认领前不能查看业务表单，首页必须先引导到待办页完成认领。
const openTodo = item => {
  router.push(item.claimable ? '/portal/tasks?tab=pending' : item.route)
}
const remind = async item => {
  await remindWorkflowInstance(item.instanceId)
  ElMessage.success('催办通知已发送')
  await load()
}
onMounted(load)
</script>

<style scoped>
.workflow-summary { display:flex; align-items:baseline; gap:7px; padding:16px 2px 10px; color:#71808a; }
.workflow-summary strong { color:#172b3a; font-size:28px; }
.workflow-summary em { margin-left:auto; color:#c05c45; font-size:12px; font-style:normal; }
.workflow-summary em + em { margin-left:4px; color:#267b70; }
.workflow-item { width:100%; display:flex; align-items:center; justify-content:space-between; gap:12px; padding:10px 2px; border:0; border-top:1px solid #f0f2f3; background:transparent; text-align:left; cursor:pointer; }
.workflow-item span { min-width:0; display:grid; gap:3px; }
.workflow-item b { overflow:hidden; color:#344a58; font-size:13px; text-overflow:ellipsis; white-space:nowrap; }
.workflow-item small { overflow:hidden; color:#8b979f; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.workflow-item i { flex:none; padding:3px 7px; border-radius:10px; background:#edf7f5; color:#217a6d; font-size:10px; font-style:normal; }
.workflow-item i.overdue { background:#fff0ed; color:#bd503b; }
.workflow-item--supervision > button { flex:none; border:0; border-radius:10px; padding:6px 10px; background:#edf7f5; color:#087b68; cursor:pointer; }
.workflow-empty { display:grid; min-height:100px; place-content:center; color:#9aa4aa; font-size:12px; }
.education-metrics { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:10px; padding:14px 0; }
.education-metrics > div { display:grid; gap:2px; padding:12px; border-radius:12px; background:#f5f8f8; text-align:center; }
.education-metrics strong { color:#173b43; font-size:24px; }
.education-metrics span { color:#7c8d93; font-size:11px; }
.education-schedule { display:grid; gap:7px; border-top:1px solid #edf0f1; padding-top:12px; }
.education-schedule > b { color:#38505a; font-size:12px; }
.education-schedule button { display:grid; grid-template-columns:92px 1fr auto; gap:10px; align-items:center; border:0; border-radius:9px; padding:8px 10px; background:#fbfcfc; color:#75858c; text-align:left; cursor:pointer; }
.education-schedule button strong { color:#2e444e; font-size:12px; }
.education-schedule button small { font-size:11px; }
.education-ai { display:grid; gap:9px; padding:14px 0; }
.education-ai > strong { color:#254751; font-size:16px; }
.education-ai > p { margin:0; color:#7a898f; font-size:12px; line-height:1.7; }
.education-ai > button { border:1px solid #e3e9e9; border-radius:9px; padding:9px 11px; background:#fafcfc; color:#46616a; text-align:left; cursor:pointer; }
.education-ai > button.education-ai__open { border:0; background:#eaf6f3; color:#147765; text-align:center; }
</style>
