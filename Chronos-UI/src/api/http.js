import { getAdminRefresh, getAdminToken, getConsumerToken, saveAdminTokens, clearAdminTokens } from '../store/auth'
import { ElMessage } from 'element-plus'

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
const publicPathPrefixes = ['/public/', '/dicts/']
const anonymousAuthPaths = ['/auth/login', '/auth/refresh']

// Tomcat 默认请求头空间还要容纳 Cookie 等内容，超过该值的历史 JWT
// 应先通过 refresh token 换成服务端生成的精简 Token。
const MAX_SAFE_TOKEN_LENGTH = 6000

const pickTokenByPath = (url, adminToken, consumerToken) => {
  const path = String(url || '')

  // 公共接口必须保持无认证请求，避免浏览器中的过期 Token 抢先触发 JWT 401。
  if (publicPathPrefixes.some((prefix) => path.startsWith(prefix))) {
    return null
  }

  if (anonymousAuthPaths.includes(path)) {
    return null
  }

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

  const rejectRequest = (message, status) => {
    const description = typeof message === 'string' && message.trim() ? message : `请求失败（HTTP ${status || '网络错误'}）`
    // 所有使用公共 HTTP 客户端的页面都能看到服务端业务错误，包括未捕获异常的事件处理器。
    ElMessage.error({ message: description, grouping: true })
    const error = new Error(description)
    error.status = status
    throw error
  }

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
    let adminToken = getAdminToken()
    const consumerToken = getConsumerToken()
    const headers = new Headers(cfg.headers)

    // 兼容升级前已签发、包含大量权限码的 JWT。刷新请求不携带
    // Authorization，因此能够绕过旧 Token 导致的请求头超限。
    if (adminToken?.length > MAX_SAFE_TOKEN_LENGTH && !anonymousAuthPaths.includes(url)) {
      adminToken = await refreshToken()
      if (!adminToken) {
        clearAdminTokens()
      }
    }

    if (options?.headers) new Headers(options.headers).forEach((v, k) => v ? headers.set(k, v) : headers.delete(k))
    if (!headers.has('Authorization')) {
      const token = pickTokenByPath(url, adminToken, consumerToken)
      if (token) headers.set('Authorization', `Bearer ${token}`)
    }

    let res
    try {
      res = await fetch(`${cfg.baseURL}${url}`, { ...options, headers })
    } catch (error) {
      rejectRequest(error?.message === 'Failed to fetch' ? '网络连接失败，请检查服务是否可用' : error?.message, 0)
    }
    const publicRequest = publicPathPrefixes.some((prefix) => url.startsWith(prefix))
    if (res.status === 401 && !publicRequest && !options?._retry && !url.startsWith('/auth/login') && !url.startsWith('/auth/refresh')) {
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
      rejectRequest('登录状态已失效，请重新登录', 401)
    }

    if (!res.ok) {
      let message = `请求失败（HTTP ${res.status}）`
      try { const errorBody = await res.json(); message = errorBody?.msg || (typeof errorBody?.data === 'string' ? errorBody.data : null) || message } catch { /* 非JSON错误 */ }
      rejectRequest(message, res.status)
    }
    if (options?._responseType === 'blob') return res.blob()
    const data = await res.json()
    if (data?.code && !/^2\d\d$/.test(String(data.code))) rejectRequest(data.msg, Number(data.code))
    return data
  }

  return {
    get: (url) => request(url),
    post: (url, body) => request(url, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
    postText: (url, body, contentType = 'text/plain') => request(url, {
      method: 'POST',
      body,
      headers: { 'Content-Type': contentType },
    }),
    put: (url, body) => request(url, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
    delete: (url) => request(url, { method: 'DELETE' }),
    upload: (url, formData) => request(url, { method: 'POST', body: formData, headers: { 'Content-Type': '' } }),
    download: (url) => request(url, { method: 'GET', _responseType: 'blob' }),
  }
}

export const http = createHttp()
