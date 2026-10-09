<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'
import {
  completeOfficeRequest,
  createCollaborationResource,
  createOfficeRequest,
  disableCollaborationResource,
  fetchCollaborationContacts,
  fetchCollaborationResources,
  fetchManagedCollaborationResources,
  fetchManagedOfficeRequests,
  fetchMyOfficeRequests,
  updateCollaborationResource,
  uploadCollaborationFile,
  withdrawOfficeRequest,
} from '../../../api/portal'

const router = useRouter()
const route = useRoute()
const activeTab = ref(route.query.tab || 'overview')
const resources = reactive({ VEHICLE: [], SEAL: [] })
const requests = ref([])
const contacts = ref([])
const canManageResources = hasAdminPermission('iam:directory:manage', 'education:scheduling:manage')
const contactKeyword = ref('')
const loading = ref(false)
const requestDialog = ref(false)
const requestType = ref('VEHICLE')
const requestFile = ref(null)
const requestForm = reactive({
  resourceId: '',
  timeRange: [],
  purpose: '',
  destination: '',
  passengerCount: 1,
  documentTitle: '',
  copyCount: 1,
})

const scheduleKey = `chronos:portal:personal-schedules:${getAdminUsername() || 'current'}`
function readSchedules() {
  try {
    const value = JSON.parse(localStorage.getItem(scheduleKey) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}
const schedules = ref(readSchedules())
const scheduleDialog = ref(false)
const scheduleForm = reactive({
  id: '',
  title: '',
  timeRange: [],
  location: '',
  description: '',
})
const sortedSchedules = computed(() => [...schedules.value].sort((a, b) => a.startAt.localeCompare(b.startAt)))
const managedResources = ref([])
const managedRequests = ref([])
const managedType = ref('VEHICLE')
const resourceDialog = ref(false)
const resourceForm = reactive({
  id: '',
  resourceType: 'VEHICLE',
  resourceCode: '',
  resourceName: '',
  licensePlate: '',
  capacity: 5,
  description: '',
  enabled: true,
})

const entries = [
  { title: '请假', description: '假期余额、申请、撤回和销假', path: '/portal/education/leaves', icon: 'Calendar' },
  { title: '出差', description: '差旅行程、费用预算和审批', path: '/portal/collaboration/business-trips', icon: 'Suitcase' },
  { title: '用车', description: '公务车辆预约与审批', tab: 'requests', type: 'VEHICLE', icon: 'Van' },
  { title: '用印', description: '印章申请、审批与执行登记', tab: 'requests', type: 'SEAL', icon: 'Stamp' },
  { title: '公文', description: '收文、发文、签发和归档', path: '/portal/collaboration/documents', icon: 'Document' },
  { title: '会议', description: '发起会议、预订会议室和签到', path: '/portal/education/meetings', icon: 'ChatDotRound' },
  { title: '日程', description: '个人日程与提醒', tab: 'schedule', icon: 'Clock' },
  { title: '通讯录', description: '教职工工作联系方式', tab: 'contacts', icon: 'User' },
  { title: '共享文件', description: '统一文件中心共享视图', path: '/portal/collaboration/files', icon: 'FolderOpened' },
]

const unwrap = response => response?.data?.content || response?.data || []
const statusLabel = status => ({
  PENDING: '审批中',
  APPROVED: '已批准/待执行',
  IN_USE: '执行中',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回',
  CONFLICT: '资源冲突',
})[status] || status

function go(entry) {
  if (entry.path) {
    router.push(entry.path)
    return
  }
  activeTab.value = entry.tab
  if (entry.type) openRequest(entry.type)
}

async function load() {
  loading.value = true
  try {
    const [vehicleResponse, sealResponse, requestResponse] = await Promise.all([
      fetchCollaborationResources('VEHICLE'),
      fetchCollaborationResources('SEAL'),
      fetchMyOfficeRequests(),
    ])
    resources.VEHICLE = unwrap(vehicleResponse)
    resources.SEAL = unwrap(sealResponse)
    requests.value = unwrap(requestResponse)
  } catch (error) {
    ElMessage.error(error?.message || '协同办公数据加载失败')
  } finally {
    loading.value = false
  }
}

function openRequest(type) {
  requestType.value = type
  Object.assign(requestForm, {
    resourceId: resources[type][0]?.id || '',
    timeRange: [],
    purpose: '',
    destination: '',
    passengerCount: 1,
    documentTitle: '',
    copyCount: 1,
  })
  requestFile.value = null
  requestDialog.value = true
}

async function submitRequest() {
  if (!requestForm.resourceId || requestForm.timeRange.length !== 2 || !requestForm.purpose.trim()) {
    ElMessage.warning('请选择资源、时间并填写申请事由')
    return
  }
  try {
    let attachmentFileId = null
    if (requestType.value === 'SEAL' && requestFile.value) {
      const uploaded = await uploadCollaborationFile('WORKFLOW_FORM_DRAFT', null, requestFile.value)
      attachmentFileId = uploaded?.data?.id
      if (!attachmentFileId) throw new Error('用印附件上传失败')
    }
    await createOfficeRequest({
      requestType: requestType.value,
      resourceId: requestForm.resourceId,
      startAt: requestForm.timeRange[0],
      endAt: requestForm.timeRange[1],
      purpose: requestForm.purpose,
      destination: requestForm.destination,
      passengerCount: requestForm.passengerCount,
      documentTitle: requestForm.documentTitle,
      copyCount: requestForm.copyCount,
      attachmentFileId,
    })
    requestDialog.value = false
    ElMessage.success('申请已提交审批')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  }
}

async function withdraw(row) {
  try {
    await ElMessageBox.confirm('确认撤回这条申请吗？', '撤回申请')
    await withdrawOfficeRequest(row.id)
    ElMessage.success('申请已撤回')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.message || '撤回失败')
  }
}

function openSchedule(row) {
  Object.assign(scheduleForm, row ? {
    id: row.id,
    title: row.title,
    timeRange: [row.startAt, row.endAt],
    location: row.location || '',
    description: row.description || '',
  } : { id: '', title: '', timeRange: [], location: '', description: '' })
  scheduleDialog.value = true
}

function saveSchedule() {
  if (!scheduleForm.title.trim() || scheduleForm.timeRange.length !== 2) {
    ElMessage.warning('请填写标题并选择时间')
    return
  }
  const value = {
    id: scheduleForm.id || crypto.randomUUID(),
    title: scheduleForm.title.trim(),
    startAt: scheduleForm.timeRange[0],
    endAt: scheduleForm.timeRange[1],
    location: scheduleForm.location.trim(),
    description: scheduleForm.description.trim(),
  }
  const index = schedules.value.findIndex(item => item.id === value.id)
  if (index >= 0) schedules.value.splice(index, 1, value)
  else schedules.value.push(value)
  localStorage.setItem(scheduleKey, JSON.stringify(schedules.value))
  scheduleDialog.value = false
  ElMessage.success('个人日程已保存')
}

function removeSchedule(row) {
  schedules.value = schedules.value.filter(item => item.id !== row.id)
  localStorage.setItem(scheduleKey, JSON.stringify(schedules.value))
}

async function searchContacts() {
  try {
    contacts.value = unwrap(await fetchCollaborationContacts(contactKeyword.value))
  } catch (error) {
    ElMessage.error(error?.message || '通讯录加载失败')
  }

  async function loadManagedResources() {
    if (!canManageResources) return
    try {
      const [resourceResponse, requestResponse] = await Promise.all([
        fetchManagedCollaborationResources(managedType.value),
        fetchManagedOfficeRequests(),
      ])
      managedResources.value = unwrap(resourceResponse)
      managedRequests.value = unwrap(requestResponse)
    } catch (error) {
      ElMessage.error(error?.message || '资源档案加载失败')
    }

    async function completeManagedRequest(row) {
      try {
        await completeOfficeRequest(row.id)
        ElMessage.success(row.requestType === 'SEAL' ? '用印执行已登记完成' : '车辆使用已登记完成')
        await Promise.all([loadManagedResources(), load()])
      } catch (error) {
        ElMessage.error(error?.message || '执行登记失败')
      }
    }
  }

  function openResource(row) {
    Object.assign(resourceForm, row || {
      id: '',
      resourceType: managedType.value,
      resourceCode: '',
      resourceName: '',
      licensePlate: '',
      capacity: managedType.value === 'VEHICLE' ? 5 : null,
      description: '',
      enabled: true,
    })
    resourceDialog.value = true
  }

  async function saveResource() {
    if (!resourceForm.resourceCode.trim() || !resourceForm.resourceName.trim()) {
      ElMessage.warning('请填写资源编码和名称')
      return
    }
    try {
      const payload = { ...resourceForm }
      if (resourceForm.id) await updateCollaborationResource(resourceForm.id, payload)
      else await createCollaborationResource(payload)
      resourceDialog.value = false
      ElMessage.success('资源档案已保存')
      await Promise.all([loadManagedResources(), load()])
    } catch (error) {
      ElMessage.error(error?.message || '资源保存失败')
    }
  }

  async function disableResource(row) {
    try {
      await ElMessageBox.confirm(`确认停用“${row.resourceName}”吗？`, '停用资源')
      await disableCollaborationResource(row.id)
      ElMessage.success('资源已停用')
      await Promise.all([loadManagedResources(), load()])
    } catch (error) {
      if (error !== 'cancel') ElMessage.error(error?.message || '资源停用失败')
    }
  }
}

onMounted(async () => {
  await load()
  if (activeTab.value === 'contacts') await searchContacts()
})
</script>

<template>
  <div class="collaboration-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>协同办公</h1>
        <p>统一发起业务、查看个人记录；审批任务仍由流程中心集中处理。</p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="office-tabs" @tab-change="name => { if (name === 'contacts') searchContacts(); if (name === 'resources') loadManagedResources() }">
      <el-tab-pane label="工作台" name="overview">
        <section class="entry-grid">
          <button v-for="entry in entries" :key="entry.title" class="entry-card" type="button" @click="go(entry)">
            <el-icon size="28"><component :is="entry.icon" /></el-icon>
            <strong>{{ entry.title }}</strong>
            <span>{{ entry.description }}</span>
          </button>
        </section>
      </el-tab-pane>

      <el-tab-pane label="用车与用印" name="requests">
        <div class="section-actions">
          <el-button type="primary" @click="openRequest('VEHICLE')">发起用车</el-button>
          <el-button @click="openRequest('SEAL')">发起用印</el-button>
        </div>
        <el-empty v-if="!requests.length" description="暂无用车或用印申请" />
        <el-table v-else :data="requests" stripe>
          <el-table-column label="业务" width="90">
            <template #default="{ row }">{{ row.requestType === 'VEHICLE' ? '用车' : '用印' }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="事由" min-width="180" show-overflow-tooltip />
          <el-table-column prop="startAt" label="开始时间" width="180" />
          <el-table-column prop="endAt" label="结束时间" width="180" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdraw(row)">撤回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的日程" name="schedule">
        <div class="section-actions">
          <el-alert title="个人日程保存在当前门户浏览器中，不进入后台业务审批。" type="info" :closable="false" />
          <el-button type="primary" @click="openSchedule()">新建日程</el-button>
        </div>
        <el-empty v-if="!sortedSchedules.length" description="暂无个人日程" />
        <el-timeline v-else>
          <el-timeline-item v-for="item in sortedSchedules" :key="item.id" :timestamp="`${item.startAt} 至 ${item.endAt}`">
            <el-card shadow="never">
              <div class="schedule-title">
                <strong>{{ item.title }}</strong>
                <span>
                  <el-button link @click="openSchedule(item)">编辑</el-button>
                  <el-button link type="danger" @click="removeSchedule(item)">删除</el-button>
                </span>
              </div>
              <p v-if="item.location">地点：{{ item.location }}</p>
              <p v-if="item.description">{{ item.description }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-tab-pane>

      <el-tab-pane label="通讯录" name="contacts">
        <div class="contact-search">
          <el-input v-model="contactKeyword" clearable placeholder="按姓名、工号或邮箱搜索" @keyup.enter="searchContacts" />
          <el-button type="primary" @click="searchContacts">搜索</el-button>
        </div>
        <el-table :data="contacts" stripe>
          <el-table-column prop="employeeName" label="姓名" width="120" />
          <el-table-column prop="employeeCode" label="工号" width="140" />
          <el-table-column prop="departmentName" label="部门" min-width="160" />
          <el-table-column prop="positionName" label="岗位" min-width="140" />
          <el-table-column prop="workPhone" label="工作电话" width="150" />
          <el-table-column prop="workEmail" label="工作邮箱" min-width="210" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canManageResources" label="车辆/印章管理" name="resources">
        <div class="section-actions">
          <el-radio-group v-model="managedType" @change="loadManagedResources">
            <el-radio-button value="VEHICLE">车辆档案</el-radio-button>
            <el-radio-button value="SEAL">印章档案</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="openResource()">新增{{ managedType === 'VEHICLE' ? '车辆' : '印章' }}</el-button>
        </div>
        <el-table :data="managedResources" stripe>
          <el-table-column prop="resourceCode" label="编码" width="150" />
          <el-table-column prop="resourceName" label="名称" min-width="180" />
          <el-table-column v-if="managedType === 'VEHICLE'" prop="licensePlate" label="车牌" width="140" />
          <el-table-column v-if="managedType === 'VEHICLE'" prop="capacity" label="座位数" width="100" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="140"><template #default="{ row }"><el-button link @click="openResource(row)">编辑</el-button><el-button v-if="row.enabled" link type="danger" @click="disableResource(row)">停用</el-button></template></el-table-column>
        </el-table>
        <h3 class="management-title">申请与执行台账</h3>
        <el-table :data="managedRequests.filter(item => item.requestType === managedType)" stripe>
          <el-table-column prop="purpose" label="事由" min-width="200" show-overflow-tooltip />
          <el-table-column prop="startAt" label="开始时间" width="180" />
          <el-table-column prop="endAt" label="结束时间" width="180" />
          <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="执行" width="120"><template #default="{ row }"><el-button v-if="['APPROVED', 'IN_USE'].includes(row.status)" link type="primary" @click="completeManagedRequest(row)">登记完成</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="requestDialog" :title="requestType === 'VEHICLE' ? '发起用车申请' : '发起用印申请'" width="620px">
      <el-form label-width="110px">
        <el-form-item :label="requestType === 'VEHICLE' ? '车辆' : '印章'" required>
          <el-select v-model="requestForm.resourceId" class="full-width" placeholder="请选择">
            <el-option v-for="item in resources[requestType]" :key="item.id" :label="item.resourceName" :value="item.id">
              <span>{{ item.resourceName }}</span>
              <span v-if="item.licensePlate">（{{ item.licensePlate }}）</span>
            </el-option>
          </el-select>
          <small v-if="!resources[requestType].length">尚未配置可用资源，请联系管理员先维护资源档案。</small>
        </el-form-item>
        <el-form-item label="使用时间" required>
          <el-date-picker v-model="requestForm.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
        <template v-if="requestType === 'VEHICLE'">
          <el-form-item label="目的地" required><el-input v-model="requestForm.destination" /></el-form-item>
          <el-form-item label="乘车人数" required><el-input-number v-model="requestForm.passengerCount" :min="1" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="文件名称" required><el-input v-model="requestForm.documentTitle" /></el-form-item>
          <el-form-item label="用印份数" required><el-input-number v-model="requestForm.copyCount" :min="1" /></el-form-item>
          <el-form-item label="用印文件">
            <el-upload :auto-upload="false" :limit="1" :on-change="file => requestFile = file.raw" :on-remove="() => requestFile = null">
              <el-button>选择附件</el-button>
            </el-upload>
          </el-form-item>
        </template>
        <el-form-item label="申请事由" required><el-input v-model="requestForm.purpose" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="requestDialog = false">取消</el-button><el-button type="primary" @click="submitRequest">提交审批</el-button></template>
    </el-dialog>

    <el-dialog v-model="scheduleDialog" title="个人日程" width="560px">
      <el-form label-width="90px">
        <el-form-item label="标题" required><el-input v-model="scheduleForm.title" /></el-form-item>
        <el-form-item label="时间" required><el-date-picker v-model="scheduleForm.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="地点"><el-input v-model="scheduleForm.location" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="scheduleForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="scheduleDialog = false">取消</el-button><el-button type="primary" @click="saveSchedule">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="resourceDialog" :title="`${resourceForm.id ? '编辑' : '新增'}${resourceForm.resourceType === 'VEHICLE' ? '车辆' : '印章'}`" width="560px">
      <el-form label-width="90px">
        <el-form-item label="资源编码" required><el-input v-model="resourceForm.resourceCode" /></el-form-item>
        <el-form-item label="资源名称" required><el-input v-model="resourceForm.resourceName" /></el-form-item>
        <template v-if="resourceForm.resourceType === 'VEHICLE'">
          <el-form-item label="车牌号"><el-input v-model="resourceForm.licensePlate" /></el-form-item>
          <el-form-item label="座位数" required><el-input-number v-model="resourceForm.capacity" :min="1" /></el-form-item>
        </template>
        <el-form-item label="说明"><el-input v-model="resourceForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="resourceForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="resourceDialog = false">取消</el-button><el-button type="primary" @click="saveResource">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.collaboration-page{padding:24px}.page-head{margin-bottom:18px}.page-head h1{margin:0 0 8px}.page-head p{margin:0;color:#667085}.entry-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px}.entry-card{display:grid;grid-template-columns:36px 1fr;grid-template-rows:auto auto;gap:5px 10px;text-align:left;border:1px solid #e4e7ed;border-radius:10px;background:#fff;padding:18px;cursor:pointer;color:#344054}.entry-card:hover{border-color:#409eff;box-shadow:0 6px 18px #409eff1f}.entry-card .el-icon{grid-row:1/3;color:#409eff}.entry-card span{font-size:13px;color:#7b8794}.section-actions{display:flex;align-items:center;justify-content:flex-end;gap:12px;margin-bottom:16px}.section-actions .el-alert{flex:1}.contact-search{display:flex;gap:10px;max-width:560px;margin-bottom:16px}.schedule-title{display:flex;justify-content:space-between;align-items:center}.schedule-title+p,.schedule-title~p{margin:8px 0 0;color:#667085}.full-width{width:100%}small{display:block;color:#909399;margin-top:6px}.management-title{margin:28px 0 14px}
</style>
