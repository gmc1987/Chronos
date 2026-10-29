import { http } from '../../../api/http'

export const accommodationBuildings = () => http.get('/admin/education/accommodations/buildings')
export const createAccommodationBuilding = payload => http.post('/admin/education/accommodations/buildings', payload)
export const accommodationRooms = buildingId => http.get(`/admin/education/accommodations/buildings/${buildingId}/rooms`)
export const createAccommodationRoom = (buildingId, payload) => http.post(`/admin/education/accommodations/buildings/${buildingId}/rooms`, payload)
export const updateAccommodationRoom = (roomId, payload) => http.put(`/admin/education/accommodations/rooms/${roomId}`, payload)
export const deleteAccommodationRoom = roomId => http.delete(`/admin/education/accommodations/rooms/${roomId}`)
export const createAccommodationBed = (roomId, payload) => http.post(`/admin/education/accommodations/rooms/${roomId}/beds`, payload)
export const accommodationBeds = (semesterCode, availableOnly = false) => http.get(`/admin/education/accommodations/beds?semesterCode=${encodeURIComponent(semesterCode)}&availableOnly=${availableOnly}`)
export const accommodationAssignments = semesterCode => http.get(`/admin/education/accommodations/assignments?semesterCode=${encodeURIComponent(semesterCode)}`)
export const accommodationStudents = keyword => http.get(`/admin/education/accommodations/student-options?keyword=${encodeURIComponent(keyword || '')}`)
export const assignAccommodation = payload => http.post('/admin/education/accommodations/assign', payload)
export const cancelAccommodation = id => http.delete(`/admin/education/accommodations/assignments/${id}`)
export const downloadAccommodationImportTemplate = () => http.download('/admin/education/accommodations/import-template')
export const importAccommodationResources = (file, dryRun = true) => {
  const data = new FormData()
  data.append('file', file)
  return http.upload(`/admin/education/accommodations/import?dryRun=${dryRun}`, data)
}
