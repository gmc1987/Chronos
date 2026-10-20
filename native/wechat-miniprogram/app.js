const session = require('./services/session')

App({
  globalData: { launchScene: '' },
  onLaunch(options) {
    const scene = decodeURIComponent(options?.query?.scene || '')
    if (scene) {
      this.globalData.launchScene = scene
      wx.setStorageSync('chronosLaunchScene', scene)
    }
    session.restore()
  }
})
