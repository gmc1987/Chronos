import { http } from './http'

export const portalBootstrap = () => http.get('/portal/bootstrap')
export const portalHome = () => http.get('/portal/home')
export const portalEducationStudentContexts = () => http.get('/portal/education/student-contexts')
export const portalEducationSchedule = (studentId, date) => http.get(
  `/portal/education/schedule?${new URLSearchParams(
    Object.entries({ studentId, date }).filter(([, value]) => value),
  )}`,
)
export const portalMyInvigilations = () => http.get('/portal/education/exam/my-invigilations')
export const portalAcknowledgeInvigilation = (id) => http.post(
  `/portal/education/exam/my-invigilations/${id}/acknowledge`,
)
export const portalCheckInInvigilation = (id) => http.post(
  `/portal/education/exam/my-invigilations/${id}/check-in`,
)
export const portalMyExams = () => http.get('/portal/education/exam/my-exams')
export const portalMyInvigilationChanges = () => http.get('/portal/education/exam/my-change-requests')
export const portalRequestInvigilationChange = (id, payload) => http.post(
  `/portal/education/exam/my-invigilations/${id}/change-requests`, payload,
)
export const portalApplications = () => http.get('/portal/applications')
export const savePortalPreference = (payload) => http.put('/portal/preference', payload)
export const resetPortalPreference = () => http.post('/portal/preference/reset')
export const favoriteApplication = (id) => http.put(`/portal/favorites/${id}`)
export const unfavoriteApplication = (id) => http.delete(`/portal/favorites/${id}`)
export const visitApplication = (id) => http.post(`/portal/applications/${id}/visit`)
export const portalPublications = (params = {}) => http.get(`/publications?${new URLSearchParams(
  Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined),
)}`)
export const portalPublicationDetail = (id) => http.get(`/publications/${id}`)
export const readPortalPublication = (id) => http.post(`/publications/${id}/read`)
export const downloadPortalPublicationAttachment = (id) => http.download(`/publications/attachments/${id}`)
export const notificationChannelPreferences = () => http.get('/message/channel-preferences')
export const saveNotificationChannelPreference = (channel, payload) => http.put(`/message/channel-preferences/${channel}`, payload)
export const portalMeetings = () => http.get('/portal/education/meetings')
export const respondToMeeting = (id, payload) => http.post(`/portal/education/meetings/${id}/response`, payload)
export const checkInMeeting = (id) => http.post(`/portal/education/meetings/${id}/check-in`)
export const updateMeetingActionStatus = (id, itemId, payload) => http.put(
  `/portal/education/meetings/${id}/action-items/${itemId}/status`, payload,
)
export const portalGrades = (params = {}) => http.get(`/portal/education/grades?${new URLSearchParams(
  Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined),
)}`)
export const portalGradeDetail = (id) => http.get(`/portal/education/grades/${id}`)
