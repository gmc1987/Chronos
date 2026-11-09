import { http } from '../../../api/http'

const query = (path, values) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value != null && value !== '') params.set(key, String(value))
  }
  return params.size ? `${path}?${params}` : path
}

export const accommodationBuildings = organizationId => http.get(query('/admin/education/accommodations/buildings', { organizationId }))
export const createAccommodationBuilding = payload => http.post('/admin/education/accommodations/buildings', payload)
export const accommodationRooms = buildingId => http.get(`/admin/education/accommodations/buildings/${buildingId}/rooms`)
export const createAccommodationRoom = (buildingId, payload) => http.post(`/admin/education/accommodations/buildings/${buildingId}/rooms`, payload)
export const updateAccommodationRoom = (roomId, payload) => http.put(`/admin/education/accommodations/rooms/${roomId}`, payload)
export const deleteAccommodationRoom = roomId => http.delete(`/admin/education/accommodations/rooms/${roomId}`)
export const createAccommodationBed = (roomId, payload) => http.post(`/admin/education/accommodations/rooms/${roomId}/beds`, payload)
export const accommodationBeds = (semesterCode, availableOnly = false, organizationId) => http.get(query('/admin/education/accommodations/beds', { semesterCode, availableOnly, organizationId }))
export const accommodationAssignments = (semesterCode, organizationId) => http.get(query('/admin/education/accommodations/assignments', { semesterCode, organizationId }))
export const accommodationStudents = (keyword, organizationId) => http.get(query('/admin/education/accommodations/student-options', { keyword, organizationId }))
export const assignAccommodation = payload => http.post('/admin/education/accommodations/assign', payload)
export const cancelAccommodation = id => http.delete(`/admin/education/accommodations/assignments/${id}`)
export const downloadAccommodationImportTemplate = () => http.download('/admin/education/accommodations/import-template')
export const importAccommodationResources = (file, dryRun = true, organizationId) => {
  const data = new FormData()
  data.append('file', file)
  return http.upload(`/admin/education/accommodations/import?dryRun=${dryRun}&organizationId=${encodeURIComponent(organizationId || '')}`, data)
}
export const downloadAccommodationAssignmentImportTemplate = (organizationId, termId) => http.download(query(
  '/admin/education/accommodations/assignment-import/template',
  { organizationId, termId },
))
export const previewAccommodationAssignments = (file, organizationId, termId, templateVersion = '1.0') => {
  const data = new FormData()
  data.append('file', file)
  return http.upload(query('/admin/education/accommodations/assignment-import/preview', {
    organizationId,
    termId,
    templateVersion,
  }), data)
}
export const commitAccommodationAssignments = payload => http.post(
  '/admin/education/accommodations/assignment-import/commit',
  payload,
)
export const accommodationAssignmentImportBatch = batchId => http.get(
  `/admin/education/accommodations/assignment-import/batches/${batchId}`,
)
