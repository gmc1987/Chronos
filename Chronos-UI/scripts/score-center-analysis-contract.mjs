import { readFile } from 'node:fs/promises'

const routerSource = await readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const apiSource = await readFile(new URL('../src/api/admin.js', import.meta.url), 'utf8')

const routes = {
  class: 'AdminScoreClassAnalysis',
  grade: 'AdminScoreGradeAnalysis',
  subject: 'AdminScoreSubjectAnalysis',
  trend: 'AdminScoreTrendAnalysis',
  knowledge: 'AdminScoreKnowledgeAnalysis',
}

for (const [dimension, component] of Object.entries(routes)) {
  const path = `education/score-center/${dimension}-analysis`
  const routePattern = new RegExp(`path:\\s*['"]${path}['"][\\s\\S]*?component:\\s*${component}`)
  if (!routePattern.test(routerSource)) {
    throw new Error(`成绩分析路由未绑定独立页面: ${path} -> ${component}`)
  }
  const apiName = `getScore${dimension[0].toUpperCase()}${dimension.slice(1)}AnalysisCapability`
  if (!apiSource.includes(`export const ${apiName}`)) {
    throw new Error(`缺少成绩分析 API contract: ${apiName}`)
  }
  if (!apiSource.includes(`/admin/education/grades/analysis/${dimension}`)) {
    throw new Error(`成绩分析 API 路径不完整: ${dimension}`)
  }
}

if (/score-(class|grade|subject|trend|knowledge)-analysis'.*component:\s*AdminScoreCenter/s.test(routerSource)) {
  throw new Error('分析菜单不得继续渲染 AdminScoreCenter')
}

console.log(`Score-center analysis contract passed (${Object.keys(routes).length} independent routes)`)
