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
