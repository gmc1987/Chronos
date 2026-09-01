import { getAdminRefresh, getAdminToken, getConsumerToken, saveAdminTokens, clearAdminTokens } from '../store/auth'

const defaultConfig = {
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  headers: { 'Content-Type': 'application/json' },
}

const isValidToken = (token) => !!token && token !== 'undefined' && token !== 'null'

const adminPathPrefixes = [
  '/admin/',
  '/auth/',
  '/ai-model/',
  '/agent/',
  '/workflow',
  '/portal/',
  '/prompt-template/',
  '/skill/',
  '/tool/',
  '/assets/',
]

const consumerPathPrefixes = ['/consumer/']

const pickTokenByPath = (url, adminToken, consumerToken) => {
  const path = String(url || '')

  if (adminPathPrefixes.some((prefix) => path.startsWith(prefix))) {
    return isValidToken(adminToken) ? adminToken : null
  }

  if (consumerPathPrefixes.some((prefix) => path.startsWith(prefix))) {
    return isValidToken(consumerToken) ? consumerToken : null
  }

  if (isValidToken(adminToken)) return adminToken
  if (isValidToken(consumerToken)) return consumerToken
  return null
}

export const createHttp = (config = {}) => {
  const cfg = { ...defaultConfig, ...config }

  const refreshToken = async () => {
    const refresh = getAdminRefresh()
    if (!refresh) return null
    try {
      const res = await fetch(`${cfg.baseURL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refresh }),
      })
      if (!res.ok) return null
      const data = await res.json()
      if (data?.code === '200' && data?.data?.accessToken) {
        saveAdminTokens(data.data, true)
        return data.data.accessToken
      }
    } catch {
      return null
    }
    return null
  }

  const request = async (url, options = {}) => {
    const adminToken = getAdminToken()
    const consumerToken = getConsumerToken()
    const headers = new Headers(cfg.headers)

    if (options?.headers) new Headers(options.headers).forEach((v, k) => v ? headers.set(k, v) : headers.delete(k))
    if (!headers.has('Authorization')) {
      const token = pickTokenByPath(url, adminToken, consumerToken)
      if (token) headers.set('Authorization', `Bearer ${token}`)
    }

    const res = await fetch(`${cfg.baseURL}${url}`, { ...options, headers })
    if (res.status === 401 && !options?._retry && !url.startsWith('/auth/login') && !url.startsWith('/auth/refresh')) {
      const next = await refreshToken()
      if (next) {
        const retryHeaders = new Headers(headers)
        retryHeaders.set('Authorization', `Bearer ${next}`)
        return request(url, { ...options, headers: retryHeaders, _retry: true })
      }
      clearAdminTokens()
      if (typeof window !== 'undefined') {
        window.location.href = window.location.pathname.startsWith('/admin') ? '/admin/login' : '/login'
      }
      throw new Error('HTTP 401')
    }

    if (!res.ok) {
      let message = `HTTP ${res.status}`
      try { const errorBody = await res.json(); message = errorBody?.msg || errorBody?.data || message } catch { /* 非JSON错误 */ }
      throw new Error(message)
    }
    if (options?._responseType === 'blob') return res.blob()
    return res.json()
  }

  return {
    get: (url) => request(url),
    post: (url, body) => request(url, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
    put: (url, body) => request(url, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
    delete: (url) => request(url, { method: 'DELETE' }),
    upload: (url, formData) => request(url, { method: 'POST', body: formData, headers: { 'Content-Type': '' } }),
    download: (url) => request(url, { method: 'GET', _responseType: 'blob' }),
  }
}

export const http = createHttp()
