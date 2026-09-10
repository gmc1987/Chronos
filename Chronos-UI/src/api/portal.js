import { http } from './http'

export const portalBootstrap = () => http.get('/portal/bootstrap')
export const portalHome = () => http.get('/portal/home')
export const portalEducationStudentContexts = () => http.get('/portal/education/student-contexts')
export const portalEducationSchedule = (studentId) => http.get(
  `/portal/education/schedule?${new URLSearchParams(
    Object.entries({ studentId }).filter(([, value]) => value),
  )}`,
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
