const api = require('../../services/request')
const session = require('../../services/session')

Page({
  data: { loading: true, user: {}, education: {}, student: false },
  onShow() { this.load() },
  async load() {
    try {
      const bootstrap = await api.get('/portal/bootstrap')
      const education = bootstrap?.contributions?.DATA?.data || {}
      const user = bootstrap?.user || {}
      const displayName = user.displayName || user.username || 'C'
      this.setData({ loading: false, user: { ...user, initial: displayName.slice(0, 1) }, education, student: (education.profileTypes || []).includes('STUDENT') })
    } catch { this.setData({ loading: false }) }
  },
  guardianInvite() { wx.navigateTo({ url: '/pages/guardian-invite/index' }) },
  openNotifications() { wx.showToast({ title: '通知设置跟随平台账号配置', icon: 'none' }) },
  logout() {
    wx.showModal({ title: '退出登录', content: '退出后需要重新通过微信登录。', async success(result) {
      if (!result.confirm) return
      try { await api.post('/auth/revoke', { refreshToken: session.refresh() }) } catch (_) {}
      session.clear(); wx.reLaunch({ url: '/pages/bind/index' })
    } })
  }
})
