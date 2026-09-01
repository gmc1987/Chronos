import { http } from './http'

export const portalBootstrap = () => http.get('/portal/bootstrap')
export const portalHome = () => http.get('/portal/home')
export const portalApplications = () => http.get('/portal/applications')
export const savePortalPreference = (payload) => http.put('/portal/preference', payload)
export const resetPortalPreference = () => http.post('/portal/preference/reset')
export const favoriteApplication = (id) => http.put(`/portal/favorites/${id}`)
export const unfavoriteApplication = (id) => http.delete(`/portal/favorites/${id}`)
export const visitApplication = (id) => http.post(`/portal/applications/${id}/visit`)
