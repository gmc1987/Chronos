import { http } from '../../../api/http'

const query = values => new URLSearchParams(
  Object.entries(values).filter(([, value]) => value !== undefined && value !== null && value !== ''),
).toString()

const base = '/admin/education/internships'

export const internshipCompanies = schoolId => http.get(`${base}/companies?${query({ schoolId })}`)
export const createInternshipCompany = payload => http.post(`${base}/companies`, payload)
export const submitInternshipCompany = id => http.post(`${base}/companies/${id}/submit`)
export const decideInternshipCompany = (id, payload) => http.post(`${base}/companies/${id}/decision`, payload)

export const internshipPositions = schoolId => http.get(`${base}/positions?${query({ schoolId })}`)
export const createInternshipPosition = payload => http.post(`${base}/positions`, payload)

export const internshipPlacements = (schoolId, termCode) => http.get(`${base}/placements?${query({ schoolId, termCode })}`)
export const createInternshipPlacement = payload => http.post(`${base}/placements`, payload)
export const submitInternshipPlacement = id => http.post(`${base}/placements/${id}/submit`)
export const decideInternshipPlacement = (id, payload) => http.post(`${base}/placements/${id}/decision`, payload)
export const activateInternshipPlacement = id => http.post(`${base}/placements/${id}/activate`)

export const internshipActivities = id => http.get(`${base}/placements/${id}/activities`)
export const createInternshipActivity = (id, payload) => http.post(`${base}/placements/${id}/activities`, payload)
export const internshipAssessment = id => http.get(`${base}/placements/${id}/assessment`)
export const submitInternshipAssessment = (id, payload) => http.post(`${base}/placements/${id}/assessment`, payload)
export const decideInternshipAssessment = (id, payload) => http.post(`${base}/assessments/${id}/decision`, payload)
export const internshipIncidents = id => http.get(`${base}/placements/${id}/incidents`)
export const reportInternshipIncident = (id, payload) => http.post(`${base}/placements/${id}/incidents`, payload)
export const closeInternshipIncident = (id, comment) => http.post(`${base}/incidents/${id}/close`, { comment })
