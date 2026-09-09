<template>
  <div class="page">
    <div class="header">
      <div><h2>通知渠道与模板</h2><p>外部渠道默认关闭，配置供应商发送器后再启用</p></div>
      <el-button @click="$router.push('/admin/publications')">返回通知公告</el-button>
    </div>
    <el-alert v-if="monitor.alert" type="warning" show-icon :closable="false" title="存在待处理的投递积压或死信" />
    <div class="metrics">
      <el-card>已投递<strong>{{ monitor.delivered || 0 }}</strong></el-card>
      <el-card>待投递<strong>{{ monitor.pending || 0 }}</strong></el-card>
      <el-card>死信<strong>{{ monitor.dead || 0 }}</strong></el-card>
      <el-card>成功率<strong>{{ monitor.deliveryRate ?? 100 }}%</strong></el-card>
    </div>
    <el-tabs>
      <el-tab-pane label="渠道策略">
        <el-table :data="policies">
          <el-table-column prop="channel" label="渠道" width="140" />
          <el-table-column label="启用" width="100"><template #default="scope"><el-switch v-model="scope.row.enabled" :disabled="scope.row.channel === 'IN_APP'" /></template></el-table-column>
          <el-table-column label="每分钟上限"><template #default="scope"><el-input-number v-model="scope.row.maxPerMinute" :min="1" /></template></el-table-column>
          <el-table-column label="每日上限"><template #default="scope"><el-input-number v-model="scope.row.maxPerDay" :min="1" /></template></el-table-column>
          <el-table-column label="最小间隔（秒）"><template #default="scope"><el-input-number v-model="scope.row.minIntervalSeconds" :min="0" /></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="savePolicy(scope.row)">保存</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="消息模板">
        <div class="toolbar"><el-input v-model="keyword" clearable placeholder="模板编码或名称" @keyup.enter="queryTemplates" /><el-button @click="queryTemplates">查询</el-button><el-button type="primary" @click="openTemplate()">新增模板</el-button></div>
        <el-table :data="templates">
          <el-table-column prop="templateCode" label="模板编码" />
          <el-table-column prop="templateName" label="模板名称" />
          <el-table-column prop="channel" label="渠道" width="120" />
          <el-table-column label="状态" width="100"><template #default="scope">{{ scope.row.enabled ? '启用' : '停用' }}</template></el-table-column>
          <el-table-column label="操作" width="150"><template #default="scope"><el-button link @click="openTemplate(scope.row)">编辑</el-button><el-button link type="danger" @click="removeTemplate(scope.row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-pagination v-model:current-page="templatePage" v-model:page-size="templatePageSize" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" :total="templateTotal" @size-change="changeTemplatePageSize" @current-change="loadTemplates" />
      </el-tab-pane>
      <el-tab-pane label="投递死信">
        <el-table :data="deadDeliveries">
          <el-table-column prop="publicationId" label="公告 ID" min-width="220" />
          <el-table-column prop="username" label="用户" width="140" />
          <el-table-column prop="channel" label="渠道" width="100" />
          <el-table-column prop="attemptCount" label="重试次数" width="100" />
          <el-table-column prop="lastError" label="最后错误" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" width="100">
            <template #default="scope">
              <el-button link type="primary" @click="retryDelivery(scope.row)">重新投递</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="deadPage" v-model:page-size="deadPageSize" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" :total="deadTotal" @size-change="changeDeadPageSize" @current-change="loadDeadDeliveries" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="dialog" :title="templateForm.id ? '编辑模板' : '新增模板'" width="680px">
      <el-form label-width="100px">
        <el-form-item label="模板编码"><el-input v-model="templateForm.templateCode" /></el-form-item>
        <el-form-item label="模板名称"><el-input v-model="templateForm.templateName" /></el-form-item>
        <el-form-item label="渠道"><el-select v-model="templateForm.channel"><el-option v-for="channel in channels" :key="channel" :label="channel" :value="channel" /></el-select></el-form-item>
        <el-form-item label="标题模板"><el-input v-model="templateForm.subjectTemplate" /></el-form-item>
        <el-form-item label="内容模板"><el-input v-model="templateForm.contentTemplate" type="textarea" :rows="7" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="templateForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="saveTemplate">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createNotificationTemplate,
  deleteNotificationTemplate,
  listDeadPublicationDeliveries,
  listNotificationChannelPolicies,
  listNotificationTemplates,
  notificationMonitor,
  retryPublicationDelivery,
  saveNotificationChannelPolicy,
  updateNotificationTemplate
} from '../../../api/admin'

const channels = ['IN_APP', 'EMAIL', 'SMS', 'WE_COM']
const policies = ref([])
const templates = ref([])
const deadDeliveries = ref([])
const monitor = reactive({})
const keyword = ref('')
const dialog = ref(false)
const emptyTemplate = () => ({ id: '', templateCode: '', templateName: '', channel: 'IN_APP', subjectTemplate: '', contentTemplate: '', enabled: true })
const templateForm = ref(emptyTemplate())
const templatePage = ref(1), templatePageSize = ref(10), templateTotal = ref(0)
const deadPage = ref(1), deadPageSize = ref(10), deadTotal = ref(0)
const loadTemplates = async () => {
  const response = await listNotificationTemplates({ keyword: keyword.value, page: templatePage.value - 1, size: templatePageSize.value })
  templates.value = response.data?.content || response.data || []
  templateTotal.value = response.data?.totalElements ?? templates.value.length
}
const queryTemplates = () => { templatePage.value = 1; loadTemplates() }
const changeTemplatePageSize = () => { templatePage.value = 1; loadTemplates() }
const loadDeadDeliveries = async () => {
  const response = await listDeadPublicationDeliveries({ page: deadPage.value - 1, size: deadPageSize.value })
  deadDeliveries.value = response.data?.content || response.data || []
  deadTotal.value = response.data?.totalElements ?? deadDeliveries.value.length
}
const changeDeadPageSize = () => { deadPage.value = 1; loadDeadDeliveries() }
const load = async () => {
  const [policyResponse, monitorResponse, deadResponse] = await Promise.all([
    listNotificationChannelPolicies(),
    notificationMonitor(),
    listDeadPublicationDeliveries({ page: deadPage.value - 1, size: deadPageSize.value })
  ])
  policies.value = policyResponse.data || []
  Object.assign(monitor, monitorResponse.data || {})
  deadDeliveries.value = deadResponse.data?.content || deadResponse.data || []
  deadTotal.value = deadResponse.data?.totalElements ?? deadDeliveries.value.length
  await loadTemplates()
}
const savePolicy = async row => { await saveNotificationChannelPolicy(row.channel, row); ElMessage.success('渠道策略已保存') }
const openTemplate = row => { templateForm.value = row ? { ...row } : emptyTemplate(); dialog.value = true }
const saveTemplate = async () => { const action = templateForm.value.id ? updateNotificationTemplate(templateForm.value.id, templateForm.value) : createNotificationTemplate(templateForm.value); await action; dialog.value = false; ElMessage.success('模板已保存'); await loadTemplates() }
const removeTemplate = async row => { await ElMessageBox.confirm(`确认删除模板“${row.templateName}”？`, '删除模板'); await deleteNotificationTemplate(row.id); await loadTemplates() }
const retryDelivery = async row => {
  await retryPublicationDelivery(row.id)
  ElMessage.success('已重新加入投递队列')
  await load()
}
onMounted(load)
</script>

<style scoped>
.page { padding: 24px; }
.header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.header h2 { margin: 0 0 5px; }.header p { margin: 0; color: #84909a; }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 16px 0; }
.metrics :deep(.el-card__body) { display: flex; justify-content: space-between; }.metrics strong { font-size: 22px; }
.toolbar { display: grid; grid-template-columns: minmax(220px, 1fr) auto auto; gap: 10px; margin-bottom: 12px; }
</style>
