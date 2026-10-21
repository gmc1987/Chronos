export const statusLabels = {
  PENDING_DISPATCH: '待派单', DISPATCHED: '已派单', ACCEPTED: '已接单', IN_PROGRESS: '维修中',
  WAITING_MATERIAL: '待物料', COMPLETED: '已完工待确认', CLOSED: '已关闭', CANCELLED: '已取消',
  SUBMITTED: '待审批', APPROVED: '已批准待出库', REJECTED: '已驳回', ISSUED: '已出库',
}
export const categoryLabels = { ELECTRICAL: '电气', WATER: '给排水', NETWORK: '网络', HVAC: '空调', FURNITURE: '家具', SECURITY: '安防', OTHER: '其他' }
export const urgencyLabels = { NORMAL: '普通', URGENT: '紧急', CRITICAL: '重大' }
export const dateTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
