let accessToken = ''
let refreshToken = ''

function restore() {
  accessToken = wx.getStorageSync('chronosAccessToken') || ''
  refreshToken = wx.getStorageSync('chronosRefreshToken') || ''
}

function save(tokens) {
  accessToken = tokens?.accessToken || ''
  refreshToken = tokens?.refreshToken || ''
  wx.setStorageSync('chronosAccessToken', accessToken)
  wx.setStorageSync('chronosRefreshToken', refreshToken)
}

function clear() {
  accessToken = ''
  refreshToken = ''
  wx.removeStorageSync('chronosAccessToken')
  wx.removeStorageSync('chronosRefreshToken')
  wx.removeStorageSync('chronosBootstrap')
}

function token() { return accessToken }
function refresh() { return refreshToken }
function authenticated() { return Boolean(accessToken) }

module.exports = { restore, save, clear, token, refresh, authenticated }
