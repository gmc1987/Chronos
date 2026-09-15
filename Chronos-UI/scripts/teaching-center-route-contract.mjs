import { readFile } from 'node:fs/promises'

const routerSource = await readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8')

const menuRoutes = {
  '/admin/education/teaching-center/plan': 'AdminTeachingPlan',
  '/admin/education/teaching-center/lesson-plan': 'AdminLessonPlans',
  '/admin/education/teaching-center/preparation': 'AdminPreparation',
  '/admin/education/teaching-center/courseware': 'AdminCourseware',
  '/admin/education/teaching-center/material': 'AdminMaterials',
  '/admin/education/teaching-center/homework': 'AdminHomework',
  '/admin/education/teaching-center/question-bank': 'AdminQuestionBank',
  '/admin/education/teaching-center/knowledge-point': 'AdminKnowledgePoints',
  '/admin/education/teaching-center/error-book': 'AdminMistakes',
  '/admin/education/teaching-center/research': 'AdminResearch',
}

for (const [path, component] of Object.entries(menuRoutes)) {
  const nestedPath = path.replace(/^\/admin\//, '')
  const routePattern = new RegExp(`path:\\s*['"]${nestedPath.replaceAll('/', '\\/')}['"][\\s\\S]*?component:\\s*${component}`)
  if (!routePattern.test(routerSource)) {
    throw new Error(`教学中心菜单缺少真实页面路由: ${path} -> ${component}`)
  }
}

const routePaths = [...routerSource.matchAll(/path:\s*['"]([^'"]*education\/teaching-center[^'"]*)['"]/g)]
  .map(match => match[1])
const duplicatePaths = routePaths.filter((path, index) => routePaths.indexOf(path) !== index)
if (duplicatePaths.length) {
  throw new Error(`发现重复路由路径: ${[...new Set(duplicatePaths)].join(', ')}`)
}

const aliasRoutes = {
  'education/teaching-center/materials': 'admin-education-material',
  'education/materials': 'admin-education-material',
  'education/teaching-center/knowledge-points': 'admin-education-knowledge-point',
  'education/knowledge-points': 'admin-education-knowledge-point',
  'education/teaching-center/mistakes': 'admin-education-error-book',
}
for (const [path, name] of Object.entries(aliasRoutes)) {
  const aliasPattern = new RegExp(`path:\\s*['"]${path}['"][\\s\\S]*?redirect:\\s*\\{\\s*name:\\s*['"]${name}['"]`)
  if (!aliasPattern.test(routerSource)) {
    throw new Error(`兼容路径未统一重定向: ${path} -> ${name}`)
  }
}

console.log(`Teaching-center route contract passed (${Object.keys(menuRoutes).length} menu routes, ${Object.keys(aliasRoutes).length} aliases)`)
