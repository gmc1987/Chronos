import { http } from '../../../api/http'

export const teachingCenterResources = (params) => {
  const query = new URLSearchParams(params).toString()
  return http.get(`/education/teaching-center/resources?${query}`)
}
export const createTeachingResource = (body) => http.post('/education/teaching-center/resources', body)
export const updateTeachingResource = (id, body) => http.put(`/education/teaching-center/resources/${id}`, body)
export const transitionTeachingResource = (id, status) =>
  http.post(`/education/teaching-center/resources/${id}/status?status=${encodeURIComponent(status)}`)

export const teachingDomainPage = (type, params = {}) => {
  const query = new URLSearchParams(params).toString()
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
  const query = new URLSearchParams(params).toString()
  return http.get(`/education/teaching-center/domain/${encodeURIComponent(type)}/export.csv?${query}`, { responseType: 'blob' })
}
export const importTeachingDomainCsv = (type, body) =>
  http.post(`/education/teaching-center/domain/${encodeURIComponent(type)}/import.csv`, body, {
    headers: { 'Content-Type': 'text/csv' }
  })
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
