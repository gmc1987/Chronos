import { http } from '../../api/http'

const qs = params => new URLSearchParams(Object.entries(params || {})
  .filter(([, value]) => value !== '' && value !== null && value !== undefined)).toString()

export const repairOrders = params => http.get(`/admin/facility/repair-orders?${qs(params)}`)
export const repairOrderDetail = id => http.get(`/admin/facility/repair-orders/${id}`)
export const dispatchRepairOrder = (id, payload) => http.post(`/admin/facility/repair-orders/${id}/dispatch`, payload)
export const autoDispatchRepairOrder = id => http.post(`/admin/facility/repair-orders/${id}/auto-dispatch`)
export const cancelRepairOrder = (id, remark) => http.post(`/admin/facility/repair-orders/${id}/cancel`, { remark })

export const technicians = () => http.get('/admin/facility/technicians')
export const facilityOrganizationOptions = () => http.get('/admin/facility/organization-options')
export const createTechnician = payload => http.post('/admin/facility/technicians', payload)
export const updateTechnician = (id, payload) => http.put(`/admin/facility/technicians/${id}`, payload)

export const materials = params => http.get(`/admin/facility/materials?${qs(params)}`)
export const createMaterial = payload => http.post('/admin/facility/materials', payload)
export const updateMaterial = (id, payload) => http.put(`/admin/facility/materials/${id}`, payload)
export const restockMaterial = (id, payload) => http.post(`/admin/facility/materials/${id}/restock`, payload)

export const materialRequests = params => http.get(`/admin/facility/material-requests?${qs(params)}`)
export const reviewMaterialRequest = (id, payload) => http.post(`/admin/facility/material-requests/${id}/review`, payload)
export const issueMaterialRequest = id => http.post(`/admin/facility/material-requests/${id}/issue`)
export const maintenanceLedger = params => http.get(`/admin/facility/ledger?${qs(params)}`)

export const myRepairOrders = params => http.get(`/portal/facility/repair-orders?${qs(params)}`)
export const myRepairOrderDetail = id => http.get(`/portal/facility/repair-orders/${id}`)
export const createRepairOrder = payload => http.post('/portal/facility/repair-orders', payload)
export const closeRepairOrder = (id, payload) => http.post(`/portal/facility/repair-orders/${id}/close`, payload)
export const cancelMyRepairOrder = (id, remark) => http.post(`/portal/facility/repair-orders/${id}/cancel`, { remark })
export const assignedRepairOrders = params => http.get(`/portal/facility/assigned-repair-orders?${qs(params)}`)
export const acceptRepairOrder = id => http.post(`/portal/facility/assigned-repair-orders/${id}/accept`)
export const startRepairOrder = id => http.post(`/portal/facility/assigned-repair-orders/${id}/start`)
export const completeRepairOrder = (id, note) => http.post(`/portal/facility/assigned-repair-orders/${id}/complete`, { note })
export const availableMaterials = params => http.get(`/portal/facility/materials?${qs(params)}`)
export const requestRepairMaterials = payload => http.post('/portal/facility/material-requests', payload)
