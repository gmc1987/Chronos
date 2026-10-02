import { readFile } from 'node:fs/promises'

const apiSource = await readFile(new URL('../src/api/admin.js', import.meta.url), 'utf8')
const pageSource = await readFile(new URL('../src/modules/education/pages/AdminSchedulingAi.vue', import.meta.url), 'utf8')
const industrySource = await readFile(new URL('../src/industries/education/index.js', import.meta.url), 'utf8')
const classicSource = await readFile(new URL('../src/modules/education/pages/AdminClassScheduling.vue', import.meta.url), 'utf8')
const switchSource = await readFile(new URL('../src/modules/education/components/SchedulingModeSwitch.vue', import.meta.url), 'utf8')
const menuMigration = await readFile(new URL('../../Chronos/education-app/src/main/resources/db/migration/V20270106__education_ai_scheduling_single_menu.sql', import.meta.url), 'utf8')

const endpoints = [
  ['/admin/education/scheduling/ai/runs', 'createAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}', 'getAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/reply', 'replyAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/confirm', 'confirmAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/generate', 'generateAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/candidates', 'listAiSchedulingCandidates'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/compare', 'compareAiSchedulingCandidates'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/candidates/${encodeURIComponent(candidateId)}/preview', 'previewAiSchedulingCandidate'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/candidates/${encodeURIComponent(candidateId)}/explanation', 'explainAiSchedulingCandidate'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/cancel', 'cancelAiSchedulingRun'],
]

for (const [endpoint, functionName] of endpoints) {
  if (!apiSource.includes(endpoint) || !apiSource.includes(`export const ${functionName}`)) {
    throw new Error(`AI 排课 API 契约缺失: ${functionName} -> ${endpoint}`)
  }
}

for (const status of ['DRAFT', 'NEEDS_CLARIFICATION', 'READY_FOR_CONFIRMATION', 'CONFIRMED', 'QUEUED', 'RUNNING', 'CANDIDATES_READY', 'FAILED', 'CANCELLED', 'EXPIRED']) {
  if (!pageSource.includes(status)) throw new Error(`AI 排课页面缺少状态: ${status}`)
}

// 只禁止 AI 页面执行这些写操作；前往普通排课审核页的说明和按钮属于只读导航。
for (const forbiddenAction of [/\bapplyScheduleCandidate\b/i, /\bpublishSchedule\b/i, /\brollbackSchedule\b/i]) {
  if (forbiddenAction.test(pageSource)) throw new Error(`AI 排课页面包含禁止操作: ${forbiddenAction}`)
}
if (!pageSource.includes('前往审核与发布') || !pageSource.includes("path: '/admin/education/scheduling'")) {
  throw new Error('AI 排课页面缺少前往普通排课审核的入口')
}

if (!industrySource.includes("path: '/admin/education/scheduling/ai'") || !industrySource.includes('component: AdminSchedulingAi')) {
  throw new Error('教育行业路由缺少 AI 排课工作台')
}
if (!industrySource.includes("path: '/admin/education/scheduling'") || !industrySource.includes('component: AdminClassScheduling')) {
  throw new Error('教育行业路由缺少原走班排课界面')
}
if (!classicSource.includes('<SchedulingModeSwitch') || !pageSource.includes('<SchedulingModeSwitch')) {
  throw new Error('两个排课界面必须共用模式开关')
}
if (!switchSource.includes("const SCHEDULING_PATH = '/admin/education/scheduling'")
  || !switchSource.includes('const AI_PATH = `${SCHEDULING_PATH}/ai`')
  || !switchSource.includes('path: enabled ? AI_PATH : SCHEDULING_PATH')) {
  throw new Error('模式开关未连接普通排课与 AI 排课路由')
}
if (!switchSource.includes('aiRunId: runId') || !switchSource.includes('{ semesterCode }')
  || !pageSource.includes('await loadRun(runId)')
  || !pageSource.includes('runRequestSequence.value += 1')) {
  throw new Error('排课模式切换未保留学期和运行中的 Run')
}
for (const permission of ['education:ai:agent:use', 'education:scheduling:manage', 'education:scheduling:ai:use']) {
  if (!switchSource.includes(permission)) throw new Error(`模式开关缺少权限校验: ${permission}`)
}
for (const table of ['t_permission', 't_role_menu_permission', 't_role_menu', 't_menu']) {
  if (!menuMigration.includes(table)) throw new Error(`菜单迁移未处理 ${table}`)
}
if (!menuMigration.includes("path = '/admin/education/scheduling/ai'")
  || !menuMigration.includes("path = '/admin/education/scheduling'")
  || !menuMigration.includes('SET menu_id = retired.parent_id')
  || !menuMigration.includes('INSERT INTO t_role_menu_permission')
  || !menuMigration.includes('DELETE FROM t_menu menu')) {
  throw new Error('菜单迁移没有保留原菜单与 AI 权限关联')
}

for (const requiredText of ['自然语言需求', 'GLOBAL 全量', 'LOCAL 局部', '需求澄清', '候选对比', '候选预览（只读）', '取消 Run']) {
  if (!pageSource.includes(requiredText)) throw new Error(`AI 排课页面缺少关键文案: ${requiredText}`)
}
if (!pageSource.includes('unsupportedItems') || !pageSource.includes(':max="5"')
  || !pageSource.includes('await previewAiSchedulingCandidate(')
  || !pageSource.includes('await compareAiSchedulingCandidates(')) {
  throw new Error('AI 排课页面缺少不支持规则提示、候选数量门禁或真实候选比较/预览')
}
if (!pageSource.includes('candidateExplanation(scope.row)')
  || !pageSource.includes('metrics.unscheduledLessons')
  || !pageSource.includes('metrics.preferredSlotHits')
  || !pageSource.includes('metrics.consecutiveBlockHits')) {
  throw new Error('候选说明必须取自真实排课指标，并显示未排课时与偏好命中')
}
if (!pageSource.includes('await explainAiSchedulingCandidate(')
  || !pageSource.includes('fact.text')
  || !pageSource.includes('modelExplanation.value = null')) {
  throw new Error('模型候选解读必须独立请求并显示服务端受控指标')
}

console.log(`AI scheduling contract passed (${endpoints.length} endpoints, 10 statuses)`)
