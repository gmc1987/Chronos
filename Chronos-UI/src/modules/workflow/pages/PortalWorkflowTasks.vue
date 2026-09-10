<template>
  <div class="task-page">
    <div class="title">
      <div><h2>流程任务</h2><p>集中处理待办、已办和我发起的流程</p></div>
      <div>
        <el-badge :value="unreadCount" :hidden="!unreadCount" class="notification-badge">
          <el-button @click="$router.push('/portal/workflow-notifications')">消息通知</el-button>
        </el-badge>
        <el-button v-if="canDelegate" @click="$router.push('/portal/workflow-delegations')">委托设置</el-button>
        <el-button v-if="canStart" type="primary" @click="$router.push('/portal/workflows')">发起流程</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="changeTab">
      <el-tab-pane label="待办" name="pending">
        <el-table :data="pending" v-loading="loading">
          <el-table-column prop="flowName" label="流程" />
          <el-table-column prop="businessKey" label="业务编号" />
          <el-table-column prop="initiator" label="发起人" width="110" />
          <el-table-column prop="nodeName" label="当前节点" />
          <el-table-column prop="dueAt" label="办理期限" width="180" />
          <el-table-column label="SLA" width="110">
            <template #default="{ row }">
              <el-tag :type="slaType(row.slaStatus)">{{ slaText(row.slaStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="390">
            <template #default="{ row }">
              <el-button v-if="row.claimable && canClaim" link type="primary" @click="claim(row)">认领</el-button>
              <template v-else>
                <el-button link type="primary" @click="openForm(row)">办理</el-button>
                <el-button v-if="canApprove && row.operations?.approve !== false" link type="success" @click="approve(row)">通过</el-button>
                <el-button v-if="canReject && row.operations?.reject !== false" link type="danger" @click="reject(row)">拒绝</el-button>
                <el-dropdown @command="command => operate(row, command)">
                  <el-button link>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canClaim" command="unclaim">取消认领</el-dropdown-item>
                      <el-dropdown-item v-if="canReturn && row.operations?.return !== false" command="return">退回</el-dropdown-item>
                      <el-dropdown-item v-if="canTransfer && row.operations?.transfer !== false" command="transfer">转办</el-dropdown-item>
                      <el-dropdown-item v-if="canAddSign && row.operations?.addSign !== false" command="add-sign">加签</el-dropdown-item>
                      <el-dropdown-item v-if="canCc && row.operations?.cc !== false" command="cc">抄送</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="已办" name="handled">
        <el-table :data="handled" v-loading="loading">
          <el-table-column prop="flowName" label="流程" />
          <el-table-column prop="businessKey" label="业务编号" />
          <el-table-column prop="nodeName" label="节点" />
          <el-table-column prop="status" label="结果" width="130" />
          <el-table-column prop="comment" label="意见" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link @click="openForm(row)">查看</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我发起的" name="initiated">
        <el-table :data="initiated" v-loading="loading">
          <el-table-column prop="flowName" label="流程" />
          <el-table-column prop="businessKey" label="业务编号" />
          <el-table-column prop="status" label="状态" width="130" />
          <el-table-column prop="currentNodeKey" label="当前节点" />
          <el-table-column prop="createTime" label="发起时间" width="190" />
          <el-table-column label="操作" width="210">
            <template #default="{ row }">
              <el-button link type="primary" @click="openInstance(row)">查看</el-button>
              <el-button v-if="canRemind && row.status === 'RUNNING'" link type="warning" @click="remind(row)">催办</el-button>
              <el-button v-if="canWithdraw && row.status === 'RUNNING'" link type="danger" @click="withdraw(row)">撤回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="load"
    />

    <el-dialog v-model="operationDialog" :title="operationTitle" width="480px">
      <el-form label-width="80px">
        <el-form-item v-if="['return', 'reject'].includes(operationType)" label="目标节点">
          <el-select v-model="operationForm.targetNodeKey" style="width: 100%">
            <el-option v-for="node in operationTask?.returnTargets || []" :key="node.nodeKey" :label="node.nodeName" :value="node.nodeKey" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="目标用户">
          <el-select v-model="operationForm.assignee" filterable style="width: 100%">
            <el-option v-for="user in users" :key="user.username" :label="`${user.displayName}（${user.username}）`" :value="user.username" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作意见"><el-input v-model="operationForm.comment" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="operationDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOperation">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  addSignWorkflowTask, ccWorkflowTask, claimWorkflowTask, completeWorkflowTask,
  handledWorkflowTasks, initiatedWorkflowInstances, pendingWorkflowTasks,
  rejectWorkflowTask, remindWorkflowInstance, returnWorkflowTask,
  transferWorkflowTask, unclaimWorkflowTask, withdrawWorkflowInstance,
  workflowDirectoryUsers, workflowNotificationUnreadCount
} from '../../../api/admin'
import { hasAdminPermission } from '../../../store/auth'

const allowed = permission => hasAdminPermission(permission)
const router = useRouter()
const route = useRoute()
const canStart = allowed('workflow:instance:start')
const canApprove = allowed('workflow:task:approve')
const canReject = allowed('workflow:task:reject')
const canReturn = allowed('workflow:task:return')
const canTransfer = allowed('workflow:task:transfer')
const canAddSign = allowed('workflow:task:add-sign')
const canCc = allowed('workflow:task:cc')
const canClaim = allowed('workflow:task:claim')
const canDelegate = allowed('workflow:delegation:manage')
const canRemind = allowed('workflow:task:remind')
const canWithdraw = allowed('workflow:instance:withdraw')

const allowedTabs = new Set(['pending', 'handled', 'initiated'])
const tab = ref(allowedTabs.has(String(route.query.tab)) ? String(route.query.tab) : 'pending')
const pending = ref([])
const handled = ref([])
const initiated = ref([])
const users = ref([])
const loading = ref(false)
const unreadCount = ref(0)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const operationDialog = ref(false)
const operationTask = ref(null)
const operationType = ref('')
const operationForm = ref({ assignee: '', targetNodeKey: '', comment: '' })
const operationTitle = computed(() => ({ transfer: '转办任务', 'add-sign': '添加会签人', cc: '抄送用户', return: '退回任务', reject: '拒绝并退回' }[operationType.value] || '任务操作'))

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: pageSize.value }
    const response = tab.value === 'pending'
      ? await pendingWorkflowTasks(params)
      : tab.value === 'handled' ? await handledWorkflowTasks(params) : await initiatedWorkflowInstances(params)
    const values = response?.data?.content || response?.data || []
    total.value = response?.data?.totalElements ?? values.length
    if (tab.value === 'pending') pending.value = values
    else if (tab.value === 'handled') handled.value = values
    else initiated.value = values
  } finally {
    loading.value = false
  }
}

const changeTab = () => {
  page.value = 1
  load()
}

const changePageSize = () => {
  page.value = 1
  load()
}

const comment = async title => (await ElMessageBox.prompt('请输入处理意见', title, { inputPlaceholder: '意见（可选）' })).value || ''
const openForm = row => router.push(`/portal/workflow-instances/${row.instanceId}/forms`)
const openInstance = row => router.push(`/portal/workflow-instances/${row.id}/forms`)
const slaText = status => ({ NORMAL: '正常', DUE_SOON: '即将到期', OVERDUE: '已逾期', ESCALATED: '已升级' }[status] || '正常')
const slaType = status => ({ DUE_SOON: 'warning', OVERDUE: 'danger', ESCALATED: 'danger' }[status] || 'success')

const claim = async row => { await claimWorkflowTask(row.id); ElMessage.success('任务认领成功'); await load() }
const approve = async row => { await completeWorkflowTask(row.id, { approved: true, comment: await comment('审批通过') }); ElMessage.success('审批已通过'); await load() }
const reject = async row => {
  if (row.rejectPolicy === 'SELECTABLE') {
    operationTask.value = row
    operationType.value = 'reject'
    operationForm.value = { assignee: '', targetNodeKey: '', comment: '' }
    operationDialog.value = true
    return
  }
  await rejectWorkflowTask(row.id, { comment: await comment('审批拒绝') })
  ElMessage.success('拒绝处理完成')
  await load()
}
const remind = async row => { await remindWorkflowInstance(row.id); ElMessage.success('催办通知已发送'); await load() }

const operate = async (row, type) => {
  if (type === 'unclaim') {
    await unclaimWorkflowTask(row.id)
    ElMessage.success('已取消认领')
    return load()
  }
  if (type === 'return' && row.returnPolicy !== 'SELECTABLE') {
    await returnWorkflowTask(row.id, { comment: await comment('退回任务') })
    ElMessage.success('任务已退回')
    return load()
  }
  operationTask.value = row
  operationType.value = type
  operationForm.value = { assignee: '', targetNodeKey: '', comment: '' }
  operationDialog.value = true
}

const submitOperation = async () => {
  const form = operationForm.value
  if (['return', 'reject'].includes(operationType.value)) {
    if (!form.targetNodeKey) return ElMessage.warning('请选择目标节点')
    const api = operationType.value === 'return' ? returnWorkflowTask : rejectWorkflowTask
    await api(operationTask.value.id, form)
  }
  else {
    if (!form.assignee) return ElMessage.warning('请选择目标用户')
    const api = operationType.value === 'transfer' ? transferWorkflowTask : operationType.value === 'add-sign' ? addSignWorkflowTask : ccWorkflowTask
    await api(operationTask.value.id, form)
  }
  operationDialog.value = false
  ElMessage.success('操作成功')
  await load()
}

const withdraw = async row => {
  await ElMessageBox.confirm('确认撤回该流程？', '撤回流程', { type: 'warning' })
  await withdrawWorkflowInstance(row.id, {})
  ElMessage.success('已撤回')
  await load()
}

onMounted(async () => {
  await Promise.all([
    load(),
    workflowDirectoryUsers().then(response => { users.value = response?.data || [] }),
    workflowNotificationUnreadCount().then(response => { unreadCount.value = response?.data || 0 })
  ])
})
</script>

<style scoped>
.task-page { background: #fff; border-radius: 12px; padding: 24px; }
.title { display: flex; justify-content: space-between; align-items: center; }
.title h2 { margin: 0; }
.title p { color: #8492a6; margin: 8px 0 20px; }
.notification-badge { margin-right: 12px; }
</style>
