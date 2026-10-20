const api = require('../../services/request')

const relationships = [
  { label: '父亲', value: 'FATHER' },
  { label: '母亲', value: 'MOTHER' },
  { label: '祖父', value: 'GRANDFATHER' },
  { label: '祖母', value: 'GRANDMOTHER' },
  { label: '其他监护人', value: 'OTHER' }
]

Page({
  data: { relationships, relationshipIndex: 0, primaryGuardian: false, emergencyContact: true, invitation: null, imagePath: '', loading: false },
  chooseRelationship(event) { this.setData({ relationshipIndex: Number(event.detail.value) }) },
  togglePrimary(event) { this.setData({ primaryGuardian: event.detail.value }) },
  toggleEmergency(event) { this.setData({ emergencyContact: event.detail.value }) },
  async create() {
    this.setData({ loading: true, invitation: null, imagePath: '' })
    try {
      const relationship = relationships[this.data.relationshipIndex].value
      const invitation = await api.post('/api/mini-program/guardian-invitations', {
        relationship,
        primaryGuardian: this.data.primaryGuardian,
        emergencyContact: this.data.emergencyContact
      })
      const imagePath = await this.download(invitation.scene)
      this.setData({ invitation, imagePath })
    } finally { this.setData({ loading: false }) }
  },
  download(scene) {
    return api.download(`/api/mini-program/guardian-invitations/${encodeURIComponent(scene)}/code`)
  },
  save() {
    if (!this.data.imagePath) return
    wx.saveImageToPhotosAlbum({ filePath: this.data.imagePath, success: () => wx.showToast({ title: '已保存到相册', icon: 'success' }) })
  }
})
