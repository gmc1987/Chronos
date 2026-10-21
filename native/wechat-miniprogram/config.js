function isDeveloperBuild() {
  try {
    return wx.getAccountInfoSync().miniProgram.envVersion === 'develop'
  } catch (error) {
    return false
  }
}

function baseURL() {
  if (isDeveloperBuild()) return wx.getStorageSync('chronosApiBaseURL') || 'http://127.0.0.1:8080'
  // Each school deployment replaces this value with its approved HTTPS API domain.
  return 'https://api.example.invalid'
}

function localMockEnabled() {
  return isDeveloperBuild() && wx.getStorageSync('chronosLocalMockEnabled') === true
}

module.exports = { baseURL, localMockEnabled }
