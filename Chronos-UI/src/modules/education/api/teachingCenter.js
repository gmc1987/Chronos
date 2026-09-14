import { http } from '../../../api/http'

export const teachingCenterOfferings = () => http.get('/education/teaching-center/offerings')
export const teachingDomainPageApi = (domain, params = {}) => {
  const query = queryString(params)
  return http.get(`/education/teaching-center/api/${domain}?${query}`)
}
export const teachingDomainDetailApi = (domain, id) =>
  http.get(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}`)
export const createTeachingDomainApi = (domain, body) =>
  http.post(`/education/teaching-center/api/${domain}`, body)
export const updateTeachingDomainApi = (domain, id, body) =>
  http.put(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}`, body)
export const archiveTeachingDomainApi = (domain, id) =>
  http.post(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/archive`)
export const transitionTeachingDomainApi = (domain, id, status) =>
  http.post(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`)
export const teachingDomainChildrenApi = (domain, id, child, params = {}) => {
  const query = queryString(params)
  return http.get(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/${child}?${query}`)
}
export const publishedScheduleEntries = (offeringId) =>
  http.get(`/admin/education/schedules?dimension=OFFERING&targetId=${encodeURIComponent(offeringId)}&published=true`)
export const teachingProduction = (offeringId) =>
  http.get(`/education/teaching/offerings/${encodeURIComponent(offeringId)}/production`)
export const offeringPlans = (offeringId) =>
  http.get(`/education/teaching/offerings/${encodeURIComponent(offeringId)}/plans`)
export const offeringLessons = (offeringId) =>
  http.get(`/education/teaching/offerings/${encodeURIComponent(offeringId)}/lessons`)
export const offeringPreparations = (offeringId) =>
  http.get(`/education/teaching/offerings/${encodeURIComponent(offeringId)}/preparations`)
export const createTeachingPlan = body => http.post('/education/teaching/plans', body)
export const updateTeachingPlan = (id, body) => http.put(`/education/teaching/plans/${encodeURIComponent(id)}`, body)
export const teachingPlanDetail = id => http.get(`/education/teaching/plans/${encodeURIComponent(id)}`)
export const teachingPlanVersions = id => http.get(`/education/teaching/plans/${encodeURIComponent(id)}/versions`)
export const lessonPlanDetail = id => http.get(`/education/teaching/lessons/${encodeURIComponent(id)}`)
export const lessonPlanVersions = id => http.get(`/education/teaching/lessons/${encodeURIComponent(id)}/versions`)
export const createLessonPlan = body => http.post('/education/teaching/lessons', body)
export const updateLessonPlan = (id, body) => http.put(`/education/teaching/lessons/${encodeURIComponent(id)}`, body)
export const createPlanItem = (planId, body) => http.post(`/education/teaching/plans/${encodeURIComponent(planId)}/items`, body)
export const updatePlanItem = (id, body) => http.put(`/education/teaching/plan-items/${encodeURIComponent(id)}`, body)
export const getPreparation = id => http.get(`/education/teaching/preparations/${encodeURIComponent(id)}`)

const queryString = (params = {}) => new URLSearchParams(
  Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
).toString()

export const teachingCenterResources = (params) => {
  const query = queryString(params)
  return http.get(`/education/teaching-center/resources?${query}`)
}
export const createTeachingResource = (body) => http.post('/education/teaching-center/resources', body)
export const updateTeachingResource = (id, body) => http.put(`/education/teaching-center/resources/${id}`, body)
export const transitionTeachingResource = (id, status) =>
  http.post(`/education/teaching-center/resources/${id}/status?status=${encodeURIComponent(status)}`)

export const teachingDomainPage = (type, params = {}) => {
  const query = queryString(params)
  return http.get(`/education/teaching-center/domain/${encodeURIComponent(type)}?${query}`)
}
export const createTeachingDomain = (type, body) =>
  http.post(`/education/teaching-center/domain/${encodeURIComponent(type)}`, body)
export const updateTeachingDomain = (type, id, body) =>
  http.put(`/education/teaching-center/domain/${encodeURIComponent(type)}/${id}`, body)
export const archiveTeachingDomain = (type, id) =>
  http.post(`/education/teaching-center/domain/${encodeURIComponent(type)}/${id}/archive`)
export const statusTeachingDomain = (type, id, status) =>
  http.post(`/education/teaching-center/domain/${encodeURIComponent(type)}/${id}/status?status=${encodeURIComponent(status)}`)
export const exportTeachingDomainCsv = (type, params = {}) => {
  const query = queryString(params)
  return http.download(`/education/teaching-center/domain/${encodeURIComponent(type)}/export.csv?${query}`)
}
export const importTeachingDomainCsv = (type, body) =>
  http.postText(`/education/teaching-center/domain/${encodeURIComponent(type)}/import.csv`, body, 'text/csv')
export const teachingChildPage = (type, parentId, params = {}) => {
  const query = new URLSearchParams({ parentId, ...params }).toString()
  return http.get(`/education/teaching-center/children/${encodeURIComponent(type)}?${query}`)
}
export const createTeachingChild = (type, body) =>
  http.post(`/education/teaching-center/children/${encodeURIComponent(type)}`, body)
export const updateTeachingChild = (type, id, body) =>
  http.put(`/education/teaching-center/children/${encodeURIComponent(type)}/${id}`, body)
export const deleteTeachingChild = (type, id) =>
  http.delete(`/education/teaching-center/children/${encodeURIComponent(type)}/${id}`)
export const submitTeachingReview = (type, id, body) =>
  http.post(`/education/teaching-center/domain/${encodeURIComponent(type)}/${id}/submit-review`, body)
export const teachingReviewStatus = (type, id) =>
  http.get(`/education/teaching-center/domain/${encodeURIComponent(type)}/${id}/review-status`)

// Slice two uses explicit domain contracts; the generic compatibility CRUD above is
// intentionally not used by the preparation and resource workbenches.
export const preparationPage = (params = {}) => params.offeringId
  ? offeringPreparations(params.offeringId)
  : http.get(`/education/teaching-center/api/preparations?${queryString(params)}`)
export const createPreparation = (body) => http.post('/education/teaching/preparations', body)
export const updatePreparation = (id, body) => http.put(`/education/teaching/preparations/${encodeURIComponent(id)}`, body)
export const preparationChildren = (id, child, params = {}) =>
  http.get(`/education/teaching-center/preparations/${encodeURIComponent(id)}/${child}?${queryString(params)}`)
export const createPreparationChild = (id, child, body) =>
  http.post(`/education/teaching-center/preparations/${encodeURIComponent(id)}/${child}`, body)
export const submitPreparation = (id) =>
  http.post(`/education/teaching-center/preparations/${encodeURIComponent(id)}/submit`)

export const resourcePage = (domain, params = {}) => http.get(`/education/teaching-center/api/${domain}?${queryString(params)}`)
export const createResource = (domain, body) => http.post(`/education/teaching-center/${domain}`, body)
export const updateResource = (domain, id, body) =>
  http.put(`/education/teaching-center/${domain}/${encodeURIComponent(id)}`, body)
export const copyResource = (domain, id, body) =>
  http.post(`/education/teaching-center/${domain}/${encodeURIComponent(id)}/copy`, body)
export const resourceVersions = (domain, id) =>
  http.get(`/education/teaching-center/${domain}/${encodeURIComponent(id)}/versions`)
export const addResourceVersion = (domain, id, body) =>
  http.post(`/education/teaching-center/${domain}/${encodeURIComponent(id)}/versions`, body)
export const uploadTeachingFile = (file, businessType, businessId, onProgress) => {
  const data = new FormData()
  data.append('file', file)
  return http.upload(`/files?${queryString({ businessType, businessId })}`, data, onProgress)
}
export const setCurrentResourceVersion = (domain, id, versionId) =>
  http.post(`/education/teaching-center/${domain}/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/current`)
export const submitResource = (domain, id) =>
  http.post(`/education/teaching-center/${domain}/versions/${encodeURIComponent(id)}/submit-review`)

// Slice three contracts. Keep all identifiers selected from server data; these
// helpers are deliberately small so the workbench can show the workflow steps.
export const questionBanks = (params = {}) => resourcePage('question-banks', params)
export const createQuestionBank = (body) => http.post('/education/teaching-center/question-banks', body)
export const updateQuestionBank = (id, body) => http.put(`/education/teaching-center/question-banks/${encodeURIComponent(id)}`, body)
export const questions = (params = {}) => params.bankId
  ? http.get(`/education/teaching-center/question-banks/${encodeURIComponent(params.bankId)}/questions`)
  : resourcePage('questions', params)
export const createQuestion = (body) => http.post('/education/teaching-center/questions', body)
export const updateQuestion = (id, body) => http.put(`/education/teaching-center/questions/${encodeURIComponent(id)}`, body)
export const questionOptions = (id, params = {}) =>
  teachingDomainChildrenApi('questions', id, 'options', params)
export const submitQuestion = (id) => http.post(`/education/teaching-center/questions/${encodeURIComponent(id)}/submit`)
export const questionVersions = (id) => http.get(`/education/teaching-center/questions/${encodeURIComponent(id)}/versions`)
export const questionImportTemplate = () => http.download('/education/teaching-center/questions/import/template')
export const validateQuestionImport = (body) =>
  http.postText('/education/teaching-center/questions/import/precheck', body, 'text/csv')
export const commitQuestionImport = (body) =>
  http.postText('/education/teaching-center/questions/import/confirm', body, 'text/csv')
export const knowledgePointTree = (courseId) =>
  http.get(`/education/teaching-center/api/knowledge-points/tree?${queryString({ courseId })}`)
export const createKnowledgePoint = (body) => http.post('/education/teaching-center/knowledge-points', body)
export const updateKnowledgePoint = (id, body) => http.put(`/education/teaching-center/knowledge-points/${encodeURIComponent(id)}`, body)
export const moveKnowledgePoint = (id, body) =>
  http.post(`/education/teaching-center/api/knowledge-points/${encodeURIComponent(id)}/move`, body)
export const disableKnowledgePoint = (id) =>
  http.post(`/education/teaching-center/api/knowledge-points/${encodeURIComponent(id)}/disable`)

// Slice four contracts. Research and error-book writes go through the named
// domain API; the UI never exposes status or foreign-key text inputs.
export const researchPage = (params = {}) => resourcePage('research', params)
export const createResearch = (body) => createResource('research', body)
export const updateResearch = (id, body) => updateResource('research', id, body)
export const researchGroups = () => http.get('/education/teaching-center/research-groups')
export const createResearchGroup = (body) => http.post('/education/teaching-center/research-groups', body)
export const updateResearchGroup = (id, body) => http.put(`/education/teaching-center/research-groups/${encodeURIComponent(id)}`, body)
export const researchActivities = (id) => http.get(`/education/teaching-center/research-groups/${encodeURIComponent(id)}/activities`)
export const createResearchActivity = (id, body) => http.post(`/education/teaching-center/research-groups/${encodeURIComponent(id)}/activities`, body)
export const updateResearchActivity = (id, body) => http.put(`/education/teaching-center/research-activities/${encodeURIComponent(id)}`, body)
export const researchResults = (id) => http.get(`/education/teaching-center/research-activities/${encodeURIComponent(id)}/results`)
export const createResearchResult = (id, body) => http.post(`/education/teaching-center/research-activities/${encodeURIComponent(id)}/results`, body)
export const updateResearchResult = (id, body) => http.put(`/education/teaching-center/research-results/${encodeURIComponent(id)}`, body)
export const researchAttendance = (id, body) => http.post(`/education/teaching-center/research-activities/${encodeURIComponent(id)}/attendance`, body)
export const researchMinutes = (id, body) => http.post(`/education/teaching-center/research-activities/${encodeURIComponent(id)}/minutes`, body)
export const submitResearchResult = (id) => http.post(`/education/teaching-center/research-results/${encodeURIComponent(id)}/submit`)
export const researchChildren = (id, child, params = {}) =>
  teachingDomainChildrenApi('research', id, child, params)
export const createResearchChild = (id, child, body) =>
  http.post(`/education/teaching-center/api/research/${encodeURIComponent(id)}/${child}`, body)
export const transitionResearch = (id, status) =>
  http.post(`/education/teaching-center/api/research/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`)
export const mistakesPage = (params = {}) => http.get(`/education/teaching-center/error-books/items?${queryString(params)}`)
export const createMistake = (body) => http.post('/education/teaching-center/error-books/manual', body)
export const updateMistake = (id, body) => updateResource('mistakes', id, body)
export const transitionMistake = (id, status) =>
  http.post(`/education/teaching-center/api/mistakes/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`)

// Homework is a teaching-domain workflow, not a generic platform Workflow instance.
export const homeworkPage = (params = {}) =>
  http.get(`/education/teaching-center/api/homeworks?${queryString(params)}`)
export const createHomework = (body) =>
  http.post('/education/teaching-center/api/homeworks', body)
export const updateHomework = (id, body) =>
  http.put(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}`, body)
export const publishHomework = (id) =>
  http.post(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}/publish`)
export const closeHomework = (id) =>
  http.post(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}/close`)
export const homeworkSubmissions = (id, params = {}) =>
  http.get(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}/submissions?${queryString(params)}`)
export const myHomeworkSubmission = (id) =>
  http.get(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}/my-submission`)
export const saveHomeworkSubmission = (id, body) =>
  http.post(`/education/teaching-center/api/homeworks/${encodeURIComponent(id)}/submissions`, body)
export const submitHomework = (submissionId) =>
  http.post(`/education/teaching-center/api/homework-submissions/${encodeURIComponent(submissionId)}/submit`)
export const gradeHomeworkSubmission = (submissionId, body) =>
  http.post(`/education/teaching-center/api/homework-submissions/${encodeURIComponent(submissionId)}/grade`, body)
