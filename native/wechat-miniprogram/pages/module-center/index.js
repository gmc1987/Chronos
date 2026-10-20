const groups = {
  collaboration: {
    title: '协同办公',
    modules: [
      { title: '请假', detail: '发起请假并查看审批状态', form: 'leave', path: '/portal/education/leaves', icon: '假' },
      { title: '出差', detail: '发起出差并查看审批状态', form: 'trip', path: '/portal/collaboration/business-trips', icon: '差' },
      { title: '用车与用印', detail: '申请学校车辆或印章', form: 'office', path: '/portal/collaboration/requests/my', icon: '印' },
      { title: '公文中心', detail: '查看本人经办和签发的公文', path: '/portal/collaboration/documents/my', icon: '文' },
      { title: '文件中心', detail: '查看授权共享文件', path: '/portal/collaboration/files/shares', icon: '件' }
    ]
  },
  teaching: {
    title: '教学中心',
    modules: [
      { title: '教学计划', detail: '查看授权范围内的教学计划', path: '/education/teaching-center/api/plans?page=0&size=50', icon: '教' },
      { title: '教案', detail: '查看和管理课程教案', path: '/education/teaching-center/api/lesson-plans?page=0&size=50', icon: '案' },
      { title: '课件', detail: '查看课程课件和最近更新', path: '/education/teaching-center/api/coursewares?page=0&size=50', icon: '课' },
      { title: '题库', detail: '进入授权练习与题库', path: '/education/teaching-center/api/questions?page=0&size=50', icon: '题' }
    ]
  }
}

Page({
  data: { title: '应用模块', modules: [] },
  onLoad(options) {
    const group = groups[options.type] || groups.collaboration
    wx.setNavigationBarTitle({ title: group.title })
    this.setData(group)
  },
  open(event) {
    const item = event.currentTarget.dataset.item
    wx.navigateTo({ url: `/pages/records/index?title=${encodeURIComponent(item.title)}&path=${encodeURIComponent(item.path)}&form=${item.form || ''}` })
  }
})
