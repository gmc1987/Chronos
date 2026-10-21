const config = require('../config')
const session = require('./session')

let refreshPromise = null

function send(path, options = {}, accessToken = session.token()) {
  const headers = { 'content-type': 'application/json', ...(options.header || {}) }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${config.baseURL()}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header: headers,
      success: resolve,
      fail: reject
    })
  })
}

function unwrap(response) {
  const body = response.data || {}
  if (response.statusCode < 200 || response.statusCode >= 300 || (body.code && !String(body.code).startsWith('2'))) {
    const error = new Error(body.msg || `请求失败（${response.statusCode}）`)
    error.statusCode = response.statusCode
    throw error
  }
  return body.data === undefined ? body : body.data
}

async function renewSession() {
  if (!session.refresh()) throw new Error('登录状态已失效')
  if (!refreshPromise) {
    refreshPromise = send('/auth/refresh', {
      method: 'POST', data: { refreshToken: session.refresh() }
    }, '').then(unwrap).then(tokens => {
      session.save(tokens)
      return tokens.accessToken
    }).finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

function requireLogin() {
  session.clear()
  wx.reLaunch({ url: '/pages/bind/index' })
}

async function request(path, options = {}) {
  try {
    let response = await send(path, options)
    if (response.statusCode === 401 && !options.skipRefresh) {
      try {
        const accessToken = await renewSession()
        response = await send(path, { ...options, skipRefresh: true }, accessToken)
      } catch (error) {
        requireLogin()
        throw error
      }
    }
    return unwrap(response)
  } catch (error) {
    const networkFailure = error.statusCode === undefined && !error.message?.includes('登录状态')
    wx.showToast({
      title: networkFailure ? '网络连接失败，请稍后重试' : (error.message || '请求失败'),
      icon: 'none', duration: 2600
    })
    throw error
  }
}

function get(path) { return request(path) }
function post(path, data) { return request(path, { method: 'POST', data }) }
function put(path, data) { return request(path, { method: 'PUT', data }) }

async function download(path, retried = false) {
  const run = accessToken => new Promise((resolve, reject) => wx.downloadFile({
    url: `${config.baseURL()}${path}`,
    header: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    success: resolve,
    fail: reject
  }))
  try {
    let response = await run(session.token())
    if (response.statusCode === 401 && !retried) response = await run(await renewSession())
    if (response.statusCode !== 200) {
      const error = new Error(`文件下载失败（${response.statusCode}）`)
      error.statusCode = response.statusCode
      throw error
    }
    return response.tempFilePath
  } catch (error) {
    if (error.statusCode === 401) requireLogin()
    wx.showToast({ title: error.message || '文件下载失败', icon: 'none' })
    throw error
  }
}

async function upload(path, filePath, retried = false) {
  const run = accessToken => new Promise((resolve, reject) => wx.uploadFile({
    url: `${config.baseURL()}${path}`, filePath, name: 'file',
    header: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    success: resolve, fail: reject
  }))
  try {
    let response = await run(session.token())
    if (response.statusCode === 401 && !retried) response = await run(await renewSession())
    const body = typeof response.data === 'string' ? JSON.parse(response.data) : response.data
    return unwrap({ statusCode: response.statusCode, data: body })
  } catch (error) {
    if (error.statusCode === 401) requireLogin()
    wx.showToast({ title: error.message || '照片上传失败', icon: 'none' })
    throw error
  }
}

module.exports = { request, get, post, put, download, upload }
