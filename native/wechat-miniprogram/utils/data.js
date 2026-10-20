function rows(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.content)) return value.content
  if (Array.isArray(value?.records)) return value.records
  if (Array.isArray(value?.items)) return value.items
  return []
}

const hiddenKeys = new Set([
  'id', 'applicationId', 'businessKey', 'definitionId', 'engineInstanceId',
  'processInstanceId', 'formId', 'taskId', 'rowVersion', 'createBy', 'lastUpdateBy'
])

const labels = {
  flowName: '流程名称', flowCode: '流程编码', title: '标题', name: '名称', purpose: '事由',
  documentTitle: '文件名称', fileName: '文件名称', status: '状态', instanceStatus: '流程状态',
  type: '类型', applicantName: '申请人', applicantType: '申请人类型', username: '用户',
  nodeName: '当前节点', initiator: '发起人', assignee: '处理人', createTime: '创建时间',
  createdAt: '创建时间', lastUpdateTime: '更新时间', startTime: '开始时间', endTime: '结束时间',
  startAt: '开始时间', endAt: '结束时间', startDate: '开始日期', endDate: '结束日期',
  dueAt: '办理期限', completedAt: '完成时间', finishedAt: '结束时间', reason: '原因',
  description: '说明', summary: '摘要', comment: '意见', destination: '目的地', amount: '金额',
  courseName: '课程', teacherName: '教师', classroomName: '教室', relationship: '关系',
  studentName: '学生', parentName: '家长', requestType: '申请类型', resourceName: '使用资源',
  passengerCount: '乘车人数', copyCount: '文件份数', leaveType: '假别', requestedDays: '请假天数',
  actualEndDate: '实际结束日期', cancellationStatus: '销假状态', cancellationReason: '销假原因',
  transportAmount: '交通费', accommodationAmount: '住宿费', mealAmount: '餐费', otherAmount: '其他费用',
  estimatedAmount: '预计总额', financeStatus: '财务状态', employeeName: '姓名', employeeCode: '工号',
  departmentName: '归属部门', positionName: '职级', workPhone: '电话', workEmail: '邮箱',
  organizerUsername: '组织者', meetingType: '会议形式', agenda: '会议议程', roomName: '会议室',
  location: '地点', responseStatus: '参会状态', checkedInAt: '签到时间'
}

function hidden(key) {
  return hiddenKeys.has(key) || key === 'id' || /(?:Id|ID)$/.test(key)
}

function flatten(record) {
  if (!record || typeof record !== 'object') return record
  const value = { ...record }
  for (const key of ['meeting', 'room', 'currentParticipant']) {
    if (value[key] && typeof value[key] === 'object') Object.assign(value, value[key])
  }
  return value
}

function displayEntries(record) {
  const value = flatten(record)
  if (!value || typeof value !== 'object') return []
  const contact = value.employeeName && value.employeeCode
  const preferred = contact
    ? ['employeeName', 'employeeCode', 'departmentName', 'positionName']
    : ['flowName', 'title', 'name', 'purpose', 'status', 'nodeName', 'initiator', 'assignee',
      'destination', 'startDate', 'startTime', 'endDate', 'endTime', 'createTime', 'description']
  const ordered = preferred.concat(Object.keys(value).sort())
  return ordered
    .filter((key, index) => ordered.indexOf(key) === index)
    .filter(key => !hidden(key) && labels[key] && value[key] !== null && value[key] !== '' && typeof value[key] !== 'object')
    .filter(key => !contact || !['workPhone', 'workEmail'].includes(key))
    .slice(0, 10)
    .map(key => ({ key, label: labels[key], value: format(value[key], key) }))
}

function format(value, key) {
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (/(time|at)$/i.test(key || '') && typeof value === 'string') {
    return value.replace('T', ' ').replace(/\.\d+$/, '')
  }
  if (['status', 'instanceStatus', 'financeStatus', 'cancellationStatus', 'responseStatus'].includes(key)) {
    return {
      PENDING: '待处理', CLAIMABLE: '待认领', APPROVED: '已通过', REJECTED: '已拒绝',
      RUNNING: '审批中', COMPLETED: '已完成', WITHDRAWN: '已撤回', CANCELLED: '已取消',
      TERMINATED: '已终止', INVITED: '待确认', ACCEPTED: '已接受', DECLINED: '已谢绝', LEAVE: '已请假'
    }[String(value)] || String(value)
  }
  return String(value)
}

function encode(value) { return encodeURIComponent(JSON.stringify(value)) }
function decode(value) { try { return JSON.parse(decodeURIComponent(value || '')) } catch { return {} } }

module.exports = { rows, displayEntries, format, encode, decode, flatten }
