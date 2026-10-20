const api = require('../../services/request')
const session = require('../../services/session')

Page({
  data: {
    loading: true,
    state: '',
    bindingToken: '',
    school: null,
    invitation: null,
    profileType: 'STUDENT',
    credentialNo: '',
    parentName: ''
  },

  onLoad(options) {
    const scene = decodeURIComponent(options.scene || wx.getStorageSync('chronosLaunchScene') || '')
    this.login(scene)
  },

  async login(scene) {
    this.setData({ loading: true })
    try {
      const code = await new Promise((resolve, reject) => wx.login({ success: value => resolve(value.code), fail: reject }))
      const result = await api.post('/api/mini-program/auth/login', { code, scene })
      if (result.state === 'AUTHENTICATED') return this.finish(result.tokens)
      this.setData({
        loading: false,
        state: result.state,
        bindingToken: result.bindingToken || '',
        school: result.school || null,
        invitation: result.invitation || null
      })
    } catch (error) {
      this.setData({ loading: false, state: 'ERROR' })
    }
  },

  selectType(event) { this.setData({ profileType: event.currentTarget.dataset.type }) },
  inputCredential(event) { this.setData({ credentialNo: event.detail.value }) },
  inputParentName(event) { this.setData({ parentName: event.detail.value }) },

  async bindPhone(event) {
    if (!event.detail.code) return wx.showToast({ title: '需要授权手机号完成学校身份核验', icon: 'none' })
    try {
      wx.showLoading({ title: '正在核验' })
      let result
      if (this.data.state === 'GUARDIAN_BINDING_REQUIRED') {
        if (!this.data.parentName.trim()) throw new Error('请填写家长姓名')
        result = await api.post('/api/mini-program/auth/bind-guardian', {
          bindingToken: this.data.bindingToken,
          parentName: this.data.parentName.trim(),
          phoneCode: event.detail.code
        })
      } else {
        if (!this.data.credentialNo.trim()) throw new Error('请填写学号或工号')
        result = await api.post('/api/mini-program/auth/bind-profile', {
          bindingToken: this.data.bindingToken,
          profileType: this.data.profileType,
          credentialNo: this.data.credentialNo.trim(),
          phoneCode: event.detail.code
        })
      }
      this.finish(result.tokens)
    } catch (error) {
      wx.showToast({ title: error.message || '绑定失败', icon: 'none' })
    } finally { wx.hideLoading() }
  },

  finish(tokens) {
    session.save(tokens)
    wx.removeStorageSync('chronosLaunchScene')
    wx.switchTab({ url: '/pages/workbench/index' })
  }
})
