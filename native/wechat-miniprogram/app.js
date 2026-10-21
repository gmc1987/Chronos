const session = require('./services/session')

App({
  globalData: { launchScene: '' },
  onLaunch(options) {
    const query = options?.query || {}
    const scene = decodeURIComponent(query.scene || '')
    if (scene) {
      this.globalData.launchScene = scene
      wx.setStorageSync('chronosLaunchScene', scene)
    }
    // Local mock parameters are accepted only by the developer build. They let
    // the IDE exercise the complete binding flow before a public HTTPS domain
    // and WeChat phone-number authorization are available.
    if (query.localMock !== undefined) {
      wx.setStorageSync('chronosLocalMockEnabled', query.localMock === '1')
    }
    if (query.apiBaseURL) wx.setStorageSync('chronosApiBaseURL', decodeURIComponent(query.apiBaseURL))
    if (query.mockOpenId) wx.setStorageSync('chronosMockOpenId', decodeURIComponent(query.mockOpenId))
    if (query.mockPhone) wx.setStorageSync('chronosMockPhone', decodeURIComponent(query.mockPhone))
    session.restore()
  }
})
