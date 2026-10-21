const api = require('../../services/request')
const status = { PENDING_DISPATCH:'待派单',DISPATCHED:'已派单',ACCEPTED:'已接单',IN_PROGRESS:'维修中',WAITING_MATERIAL:'待物料',COMPLETED:'已完工待确认',CLOSED:'已关闭',CANCELLED:'已取消' }
Page({
  data:{ loading:true, assigned:false, rows:[] },
  onShow(){ this.load() }, onPullDownRefresh(){ this.load().finally(wx.stopPullDownRefresh) },
  async load(){ this.setData({loading:true});try{const path=this.data.assigned?'/portal/facility/assigned-repair-orders?page=0&size=50':'/portal/facility/repair-orders?page=0&size=50';const page=await api.get(path);this.setData({rows:(page.content||[]).map(x=>({...x,statusLabel:status[x.status]||x.status})),loading:false})}catch{this.setData({loading:false})} },
  switchMode(e){this.setData({assigned:e.currentTarget.dataset.assigned==='true'},()=>this.load())},
  create(){wx.navigateTo({url:'/pages/repair-form/index'})},
  detail(e){wx.navigateTo({url:`/pages/repair-detail/index?id=${e.currentTarget.dataset.id}&assigned=${this.data.assigned?1:0}`})}
})
