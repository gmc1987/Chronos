import { http } from '../../../api/http'

const query = values => new URLSearchParams(
  Object.entries(values).filter(([, value]) => value !== undefined && value !== null && value !== ''),
).toString()

export const academicPrograms = schoolId => http.get(`/admin/education/academic-lifecycle/programs?${query({ schoolId })}`)
export const createAcademicProgram = payload => http.post('/admin/education/academic-lifecycle/programs', payload)
export const addAcademicProgramCourse = (id, payload) => http.post(`/admin/education/academic-lifecycle/programs/${id}/courses`, payload)
export const submitAcademicProgram = id => http.post(`/admin/education/academic-lifecycle/programs/${id}/submit`)
export const publishAcademicProgram = id => http.post(`/admin/education/academic-lifecycle/programs/${id}/publish`)

export const termRegistrations = (schoolId, termCode) => http.get(`/admin/education/academic-lifecycle/registrations?${query({ schoolId, termCode })}`)
export const submitTermRegistration = payload => http.post('/admin/education/academic-lifecycle/registrations', payload)
export const decideTermRegistration = (id, payload) => http.post(`/admin/education/academic-lifecycle/registrations/${id}/decision`, payload)

export const courseEnrollments = (schoolId, termCode) => http.get(`/admin/education/academic-lifecycle/enrollments?${query({ schoolId, termCode })}`)
export const enrollCourse = payload => http.post('/admin/education/academic-lifecycle/enrollments', payload)
export const withdrawCourse = (id, reason) => http.post(`/admin/education/academic-lifecycle/enrollments/${id}/withdraw`, { reason })

export const graduationAudits = schoolId => http.get(`/admin/education/academic-lifecycle/graduation-audits?${query({ schoolId })}`)
export const evaluateGraduation = payload => http.post('/admin/education/academic-lifecycle/graduation-audits/evaluate', payload)
export const decideGraduation = (id, payload) => http.post(`/admin/education/academic-lifecycle/graduation-audits/${id}/decision`, payload)
