import { http } from '../../api/http'
import { getAdminToken } from '../../store/auth'

const base = '/admin/sales-delivery'
const params = values => new URLSearchParams(values)

export const catalog = () => http.get(base + '/catalog')
export const updateCatalog = (code, values) => http.put(base + '/catalog/' + encodeURIComponent(code), values)
export const salesStatus = () => http.get(base + '/status')
export const orders = (keyword, page) => http.get(base + '/orders?' + params({ keyword, page }))
export const orderDetail = id => http.get(base + '/orders/' + id)
export const createOrder = values => http.post(base + '/orders', values)
export const confirmOrder = id => http.post(base + '/orders/' + id + '/confirm')
export const deploymentDetail = id => http.get(base + '/deployments/' + id)
export const configure = (order, id, values) =>
  http.post(base + '/orders/' + order + '/deployments' + (id ? '?' + params({ deploymentId: id }) : ''), values)
export const sign = (id, orderId) => http.post(base + '/deployments/' + id + '/licenses?' + params({ orderId }))
export const packageJob = (id, configRevision, licenseRevision) =>
  http.post(base + '/deployments/' + id + '/packages?' + params({ configRevision, licenseRevision }))
export const recordDelivery = (id, values) => http.post(base + '/packages/' + id + '/events', values)

export const deliveryStatus = () => http.get('/delivery/status')
export const delegatedAccounts = () => http.get('/delivery/accounts')
export const createSchool = values => http.post('/delivery/schools', values)
export const createAccount = values => http.post('/delivery/accounts', values)
export const updateGrant = values => http.put('/delivery/accounts/grants', values)

export async function download(path, fileName) {
  const baseUrl = import.meta.env.VITE_API_BASE || '/api'
  const largeFile = fileName.endsWith('.tar.gz')
  const destination = largeFile && window.showSaveFilePicker
    ? await window.showSaveFilePicker({ suggestedName: fileName })
    : null
  const response = await fetch(baseUrl + base + path, {
    headers: { Authorization: 'Bearer ' + getAdminToken() },
  })
  if (!response.ok) throw new Error((await response.json().catch(() => ({}))).msg || '下载失败')
  if (destination && response.body) {
    await response.body.pipeTo(await destination.createWritable())
    return
  }
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.download = fileName
  link.href = url
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
