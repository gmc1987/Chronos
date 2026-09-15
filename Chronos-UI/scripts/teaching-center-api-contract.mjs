import { readFile } from 'node:fs/promises'

const uiApi = await readFile(new URL('../src/modules/education/api/teachingCenter.js', import.meta.url), 'utf8')
const domainController = await readFile(new URL('../../Chronos/education-class-scheduling/src/main/java/com/chronos/education/scheduling/controller/TeachingDomainApiController.java', import.meta.url), 'utf8')
const collaborationController = await readFile(new URL('../../Chronos/education-class-scheduling/src/main/java/com/chronos/education/scheduling/controller/TeachingCollaborationController.java', import.meta.url), 'utf8')
const researchErrorController = await readFile(new URL('../../Chronos/education-class-scheduling/src/main/java/com/chronos/education/scheduling/controller/ResearchErrorController.java', import.meta.url), 'utf8')
const questionController = await readFile(new URL('../../Chronos/education-class-scheduling/src/main/java/com/chronos/education/scheduling/controller/QuestionKnowledgeController.java', import.meta.url), 'utf8')
const teachingController = await readFile(new URL('../../Chronos/education-class-scheduling/src/main/java/com/chronos/education/scheduling/controller/TeachingPlanLessonController.java', import.meta.url), 'utf8')

const required = [
  ['teaching plans', '/education/teaching/plans', 'plans', uiApi, teachingController],
  ['lesson plans', '/education/teaching/lessons', 'lessons', uiApi, teachingController],
  ['preparations', '/education/teaching/preparations', 'preparations', uiApi, teachingController],
  ['coursewares', 'coursewares', 'coursewares', uiApi, collaborationController],
  ['materials', 'materials', 'materials', uiApi, collaborationController],
  ['question banks', 'question-banks', 'question-banks', uiApi, questionController],
  ['knowledge point tree', '/education/teaching-center/knowledge-points/tree', 'knowledge-points/tree', uiApi, questionController],
  ['research', 'research', 'research', uiApi, domainController],
  ['error book', 'error-books/items', 'error-books', uiApi, researchErrorController],
]

for (const [label, uiPath, backendPath, uiSource, backendSource] of required) {
  if (!uiSource.includes(uiPath) || !backendSource.includes(backendPath.split('/').at(-1))) {
    throw new Error(`API 路径矩阵不一致: ${label}`)
  }
}

const forbiddenClientFields = /\b(status|createBy|versionNo|publishedVersionNo)\s*:/
if (forbiddenClientFields.test(uiApi)) {
  throw new Error('前端 API 契约包含服务端托管字段')
}

if (!uiApi.includes('queryString') || !uiApi.includes('offeringId')) {
  throw new Error('缺少分页/作用域查询契约')
}

console.log(`Teaching-center API contract passed (${required.length} mapped paths)`)
