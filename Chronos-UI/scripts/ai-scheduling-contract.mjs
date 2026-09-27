import { readFile } from 'node:fs/promises'

const apiSource = await readFile(new URL('../src/api/admin.js', import.meta.url), 'utf8')
const pageSource = await readFile(new URL('../src/modules/education/pages/AdminSchedulingAi.vue', import.meta.url), 'utf8')
const industrySource = await readFile(new URL('../src/industries/education/index.js', import.meta.url), 'utf8')

const endpoints = [
  ['/admin/education/scheduling/ai/runs', 'createAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}', 'getAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/reply', 'replyAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/confirm', 'confirmAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/generate', 'generateAiSchedulingRun'],
  ['/admin/education/scheduling/ai/runs/${encodeURIComponent(id)}/candidates', 'listAiSchedulingCandidates'],
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

for (const forbiddenAction of [/\bapply\b/i, /\bpublish\b/i, /\brollback\b/i, /应用/, /发布/, /回滚/]) {
  if (forbiddenAction.test(pageSource)) throw new Error(`AI 排课页面包含禁止操作: ${forbiddenAction}`)
}

if (!industrySource.includes("path: '/admin/education/scheduling/ai'") || !industrySource.includes('component: AdminSchedulingAi')) {
  throw new Error('教育行业路由缺少 AI 排课工作台')
}

for (const requiredText of ['自然语言需求', 'GLOBAL 全量', 'LOCAL 局部', '需求澄清', '候选对比', '候选预览（只读）', '取消 Run']) {
  if (!pageSource.includes(requiredText)) throw new Error(`AI 排课页面缺少关键文案: ${requiredText}`)
}

console.log(`AI scheduling contract passed (${endpoints.length} endpoints, 10 statuses)`)
