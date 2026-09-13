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
export const preparationPage = (params = {}) => http.get(`/education/teaching-center/api/preparations?${queryString(params)}`)
export const createPreparation = (body) => http.post('/education/teaching-center/api/preparations', body)
export const updatePreparation = (id, body) => http.put(`/education/teaching-center/api/preparations/${encodeURIComponent(id)}`, body)
export const preparationChildren = (id, child, params = {}) =>
  http.get(`/education/teaching-center/api/preparations/${encodeURIComponent(id)}/${child}?${queryString(params)}`)
export const createPreparationChild = (id, child, body) =>
  http.post(`/education/teaching-center/api/preparations/${encodeURIComponent(id)}/${child}`, body)
export const submitPreparation = (id) =>
  http.post(`/education/teaching-center/api/preparations/${encodeURIComponent(id)}/submit`)

export const resourcePage = (domain, params = {}) => http.get(`/education/teaching-center/api/${domain}?${queryString(params)}`)
export const createResource = (domain, body) => http.post(`/education/teaching-center/api/${domain}`, body)
export const updateResource = (domain, id, body) =>
  http.put(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}`, body)
export const resourceVersions = (domain, id) =>
  http.get(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/versions`)
export const uploadTeachingFile = (file, businessType, businessId, onProgress) => {
  const data = new FormData()
  data.append('file', file)
  return http.upload(`/files?${queryString({ businessType, businessId })}`, data, onProgress)
}
export const setCurrentResourceVersion = (domain, id, versionId) =>
  http.post(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/current`)
export const submitResource = (domain, id) =>
  http.post(`/education/teaching-center/api/${domain}/${encodeURIComponent(id)}/submit`)

// Slice three contracts. Keep all identifiers selected from server data; these
// helpers are deliberately small so the workbench can show the workflow steps.
export const questionBanks = (params = {}) => resourcePage('question-banks', params)
export const createQuestionBank = (body) => createResource('question-banks', body)
export const updateQuestionBank = (id, body) => updateResource('question-banks', id, body)
export const questions = (params = {}) => resourcePage('questions', params)
export const createQuestion = (body) => createResource('questions', body)
export const updateQuestion = (id, body) => updateResource('questions', id, body)
export const questionOptions = (id, params = {}) =>
  teachingDomainChildrenApi('questions', id, 'options', params)
export const submitQuestion = (id) => submitResource('questions', id)
export const questionVersions = (id) => resourceVersions('questions', id)
export const questionImportTemplate = () => http.download('/education/teaching-center/question-banks/questions/import/template')
export const validateQuestionImport = (body) =>
  http.postText('/education/teaching-center/question-banks/questions/import/validate', body, 'text/csv')
export const commitQuestionImport = (body) =>
  http.postText('/education/teaching-center/question-banks/questions/import/commit', body, 'text/csv')
export const knowledgePointTree = (courseId) =>
  http.get(`/education/teaching-center/api/knowledge-points/tree?${queryString({ courseId })}`)
export const createKnowledgePoint = (body) => createResource('knowledge-points', body)
export const updateKnowledgePoint = (id, body) => updateResource('knowledge-points', id, body)
export const moveKnowledgePoint = (id, body) =>
  http.post(`/education/teaching-center/api/knowledge-points/${encodeURIComponent(id)}/move`, body)
export const disableKnowledgePoint = (id) =>
  http.post(`/education/teaching-center/api/knowledge-points/${encodeURIComponent(id)}/disable`)

// Slice four contracts. Research and error-book writes go through the named
// domain API; the UI never exposes status or foreign-key text inputs.
export const researchPage = (params = {}) => resourcePage('research', params)
export const createResearch = (body) => createResource('research', body)
export const updateResearch = (id, body) => updateResource('research', id, body)
export const researchChildren = (id, child, params = {}) =>
  teachingDomainChildrenApi('research', id, child, params)
export const createResearchChild = (id, child, body) =>
  http.post(`/education/teaching-center/api/research/${encodeURIComponent(id)}/${child}`, body)
export const transitionResearch = (id, status) =>
  http.post(`/education/teaching-center/api/research/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`)
export const mistakesPage = (params = {}) => resourcePage('mistakes', params)
export const createMistake = (body) => createResource('mistakes', body)
export const updateMistake = (id, body) => updateResource('mistakes', id, body)
export const transitionMistake = (id, status) =>
  http.post(`/education/teaching-center/api/mistakes/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`)
