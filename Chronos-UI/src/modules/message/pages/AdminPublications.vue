<template>
  <div class="page">
    <div class="header">
      <div>
        <h2>通知公告</h2>
        <p>统一维护通知、公告、受众范围和发布周期</p>
      </div>
      <div>
        <el-button @click="$router.push('/admin/message-settings')">渠道与模板</el-button>
        <el-button type="primary" @click="openCreate">新增内容</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filters">
        <el-select v-model="query.type" clearable placeholder="业务类型" @change="load">
          <el-option label="通知" value="NOTICE" />
          <el-option label="公告" value="ANNOUNCEMENT" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="发布状态" @change="load">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.keyword" clearable placeholder="标题或摘要" @keyup.enter="load" />
        <el-button @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows">
        <el-table-column label="类型" width="90">
          <template #default="scope">{{ scope.row.publicationType === 'NOTICE' ? '通知' : '公告' }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归档" width="80">
          <template #default="scope">{{ scope.row.archived ? '已归档' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="contentType" label="内容来源" width="110" />
        <el-table-column prop="publishAt" label="计划发布时间" width="180" />
        <el-table-column prop="expireAt" label="有效期至" width="180">
          <template #default="scope">{{ scope.row.expireAt || '永久有效' }}</template>
        </el-table-column>
        <el-table-column prop="readCount" label="阅读数" width="90" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openEdit(scope.row)">查看/编辑</el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT' && scope.row.approvalRequired && scope.row.approvalStatus !== 'APPROVED'"
              size="small"
              type="primary"
              @click="submitReview(scope.row)"
            >提交审核</el-button>
            <el-button
              v-if="scope.row.status === 'PENDING_REVIEW' && !scope.row.approvalInstanceId"
              size="small"
              type="success"
              @click="review(scope.row, true)"
            >通过</el-button>
            <el-button
              v-if="scope.row.status === 'PENDING_REVIEW' && !scope.row.approvalInstanceId"
              size="small"
              type="danger"
              @click="review(scope.row, false)"
            >驳回</el-button>
            <el-button
              v-if="['DRAFT', 'SCHEDULED'].includes(scope.row.status) && (!scope.row.approvalRequired || scope.row.approvalStatus === 'APPROVED')"
              size="small"
              type="success"
              @click="publish(scope.row)"
            >发布</el-button>
            <el-button
              v-if="['PUBLISHED', 'SCHEDULED'].includes(scope.row.status)"
              size="small"
              type="warning"
              @click="withdraw(scope.row)"
            >撤下</el-button>
            <el-button
              v-if="['DRAFT', 'WITHDRAWN'].includes(scope.row.status)"
              size="small"
              type="danger"
              @click="remove(scope.row)"
            >删除</el-button>
            <el-button size="small" @click="showStatistics(scope.row)">统计</el-button>
            <el-button size="small" @click="showVersions(scope.row)">版本</el-button>
            <el-button
              v-if="['WITHDRAWN', 'EXPIRED'].includes(scope.row.status) && !scope.row.archived"
              size="small"
              @click="archive(scope.row)"
            >归档</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="query.size"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑通知公告' : '新增通知公告'" width="880px">
      <el-form label-width="110px">
        <div class="form-grid">
          <el-form-item label="业务类型">
            <el-radio-group v-model="form.publicationType" :disabled="readonly">
              <el-radio value="NOTICE">通知</el-radio>
              <el-radio value="ANNOUNCEMENT">公告</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="重要程度">
            <el-select v-model="form.importance" :disabled="readonly">
              <el-option label="普通" value="NORMAL" />
              <el-option label="重要" value="IMPORTANT" />
              <el-option label="紧急" value="URGENT" />
            </el-select>
          </el-form-item>
          <el-form-item class="full" label="标题">
            <el-input v-model="form.title" :disabled="readonly" maxlength="300" show-word-limit />
          </el-form-item>
          <el-form-item class="full" label="摘要">
            <el-input v-model="form.summary" :disabled="readonly" type="textarea" :rows="2" maxlength="1000" />
          </el-form-item>
          <el-form-item class="full" label="正文">
            <PublicationRichTextEditor
              v-model="form.content"
              :disabled="readonly"
            />
          </el-form-item>
          <el-form-item label="计划发布时间">
            <el-date-picker
              v-model="form.publishAt"
              :disabled="readonly"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空则立即发布"
            />
          </el-form-item>
          <el-form-item label="有效期结束">
            <el-date-picker
              v-model="form.expireAt"
              :disabled="readonly"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空则永久有效"
            />
          </el-form-item>
          <el-form-item label="展示设置">
            <el-checkbox v-model="form.pinned" :disabled="readonly">置顶</el-checkbox>
            <el-checkbox v-model="form.mustRead" :disabled="readonly">必须阅读</el-checkbox>
          </el-form-item>
          <el-form-item label="受众计算">
            <el-radio-group v-model="form.audienceMode" :disabled="readonly">
              <el-radio value="SNAPSHOT">发布时快照</el-radio>
              <el-radio value="DYNAMIC">动态组织关系</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="阅读截止时间">
            <el-date-picker
              v-model="form.readDeadline"
              :disabled="readonly"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              clearable
            />
          </el-form-item>
          <el-form-item label="发布审批">
            <el-switch v-model="form.approvalRequired" :disabled="readonly" />
          </el-form-item>
          <el-form-item v-if="form.approvalRequired" label="审批流程">
            <el-select
              v-model="form.approvalWorkflowDefinitionId"
              :disabled="readonly"
              clearable
              placeholder="留空使用内置审核"
            >
              <el-option
                v-for="workflow in workflows"
                :key="workflow.id"
                :label="workflow.flowName"
                :value="workflow.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序值">
            <el-input-number v-model="form.sortOrder" :disabled="readonly" :min="0" />
          </el-form-item>
        </div>

        <el-divider content-position="left">可见范围</el-divider>
        <div v-for="(audience, index) in form.audiences" :key="index" class="audience-row">
          <el-select v-model="audience.subjectType" :disabled="readonly" @change="audience.subjectId = ''">
            <el-option label="全部用户" value="ALL" />
            <el-option label="机构" value="ORGANIZATION" />
            <el-option label="部门" value="DEPARTMENT" />
            <el-option label="岗位" value="POSITION" />
            <el-option label="角色" value="ROLE" />
            <el-option label="指定用户" value="USER" />
          </el-select>
          <el-select
            v-if="audience.subjectType !== 'ALL'"
            v-model="audience.subjectId"
            :disabled="readonly"
            filterable
            placeholder="选择对象"
          >
            <el-option
              v-for="item in audienceOptions(audience.subjectType)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-checkbox
            v-if="audience.subjectType === 'DEPARTMENT'"
            v-model="audience.includeChildren"
            :disabled="readonly"
          >含下级</el-checkbox>
          <el-checkbox v-model="audience.excluded" :disabled="readonly">排除</el-checkbox>
          <el-button v-if="!readonly" link type="danger" @click="form.audiences.splice(index, 1)">移除</el-button>
        </div>
        <el-button v-if="!readonly" plain @click="addAudience">添加范围</el-button>

        <template v-if="form.id">
          <el-divider content-position="left">Word / PDF</el-divider>
          <el-upload
            v-if="!readonly"
            :auto-upload="false"
            :show-file-list="false"
            accept=".doc,.docx,.pdf"
            :on-change="uploadDocument"
          >
            <el-button>导入文档并作为正文</el-button>
          </el-upload>
          <div v-for="item in form.attachments" :key="item.id" class="attachment">
            <span>{{ item.originalName }}（{{ formatSize(item.fileSize) }}）</span>
            <div>
              <el-button link @click="downloadAttachment(item)">下载</el-button>
              <el-button v-if="!readonly" link type="danger" @click="removeAttachment(item)">删除</el-button>
            </div>
          </div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statisticsDialog" title="阅读统计" width="680px">
      <el-descriptions v-if="statistics" :column="3" border>
        <el-descriptions-item label="应读人数">{{ statistics.recipientCount }}</el-descriptions-item>
        <el-descriptions-item label="已读人数">{{ statistics.readCount }}</el-descriptions-item>
        <el-descriptions-item label="阅读率">{{ statistics.readRate }}%</el-descriptions-item>
        <el-descriptions-item label="未读人数">{{ statistics.unreadCount }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ statistics.readDeadline || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="是否逾期">{{ statistics.deadlinePassed ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="statistics?.unreadUsers || []" max-height="320">
        <el-table-column label="未读用户"><template #default="scope">{{ scope.row }}</template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="statisticsDialog = false">关闭</el-button>
        <el-button
          v-if="statistics?.unreadCount"
          type="primary"
          @click="remindUnread"
        >催办未读用户</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="versionDialog" title="版本与操作历史" width="780px">
      <el-table :data="versionRows" max-height="480">
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column prop="operation" label="操作" width="170" />
        <el-table-column prop="createBy" label="操作人" width="140" />
        <el-table-column prop="createTime" label="操作时间" min-width="180" />
        <el-table-column label="处理" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="restoreVersion(scope.row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PublicationRichTextEditor from '../components/PublicationRichTextEditor.vue'
import {
  createPublication,
  approvePublication,
  archivePublication,
  deletePublicationAttachment,
  deletePublication,
  downloadAdminPublicationAttachment,
  listPublications,
  listRoles,
  listUsers,
  organizationUnits,
  orgList,
  positions,
  publicationDetail,
  publicationStatistics,
  publicationVersions,
  publishPublication,
  rejectPublication,
  remindUnreadPublication,
  restorePublicationVersion,
  submitPublication,
  updatePublication,
  uploadPublicationAttachment,
  withdrawPublication,
  listAvailableWorkflows,
} from '../../../api/admin'

const statuses = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '待发布', value: 'SCHEDULED' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已撤下', value: 'WITHDRAWN' },
  { label: '已过期', value: 'EXPIRED' },
]
const query = reactive({ type: '', status: '', keyword: '', size: 20 })
const currentPage = ref(1)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const statisticsDialog = ref(false)
const statistics = ref(null)
const versionDialog = ref(false)
const versionRows = ref([])
const versionPublicationId = ref('')
const workflows = ref([])
const directory = reactive({ organizations: [], departments: [], positions: [], roles: [], users: [] })
const emptyForm = () => ({
  id: '',
  publicationType: 'NOTICE',
  contentType: 'RICH_TEXT',
  title: '',
  summary: '',
  content: '',
  importance: 'NORMAL',
  pinned: false,
  mustRead: false,
  audienceMode: 'SNAPSHOT',
  readDeadline: null,
  approvalRequired: false,
  approvalWorkflowDefinitionId: null,
  sortOrder: 0,
  publishAt: null,
  expireAt: null,
  status: 'DRAFT',
  audiences: [{ subjectType: 'ALL', subjectId: '*', includeChildren: false, excluded: false }],
  attachments: [],
})
const form = ref(emptyForm())
const readonly = computed(() => !['DRAFT', 'SCHEDULED'].includes(form.value.status))

const load = async () => {
  loading.value = true
  try {
    const response = await listPublications({ ...query, page: currentPage.value - 1 })
    rows.value = response.data.content || []
    total.value = response.data.totalElements || 0
  } finally {
    loading.value = false
  }
}

const loadDirectory = async () => {
  const [organizationsResponse, positionsResponse, rolesResponse, usersResponse, workflowsResponse] = await Promise.all([
    orgList({ page: 0, size: 500 }),
    positions(),
    listRoles({ page: 0, size: 500 }),
    listUsers({ page: 0, size: 500 }),
    listAvailableWorkflows().catch(() => ({ data: [] })),
  ])
  directory.organizations = organizationsResponse.data.content || []
  directory.positions = positionsResponse.data || []
  directory.roles = rolesResponse.data.content || []
  directory.users = usersResponse.data.content || []
  workflows.value = workflowsResponse.data || []
  const unitResponses = await Promise.all(directory.organizations.map(item => organizationUnits(item.id)))
  directory.departments = unitResponses.flatMap((response, index) => flattenDepartments(
    response.data || [],
    directory.organizations[index]?.organizationName,
  ))
}

// 部门接口返回树形结构；转换成平铺选项并保留机构名称，避免同名科室无法区分。
const flattenDepartments = (items, organizationName, parentPath = '') => items.flatMap(item => {
  const path = parentPath ? `${parentPath} / ${item.departmentName}` : item.departmentName
  return [
    { ...item, displayName: `${organizationName || '未命名机构'} · ${path}` },
    ...flattenDepartments(item.children || [], organizationName, path),
  ]
})

const audienceOptions = type => ({
  ORGANIZATION: directory.organizations.map(item => ({ label: item.organizationName, value: item.id })),
  DEPARTMENT: directory.departments.map(item => ({ label: item.displayName, value: item.id })),
  POSITION: directory.positions.map(item => ({ label: item.positionName, value: item.id })),
  ROLE: directory.roles.map(item => ({ label: item.roleName, value: item.id })),
  USER: directory.users.map(item => ({ label: item.displayName || item.username, value: item.username })),
}[type] || [])

const openCreate = () => {
  form.value = emptyForm()
  dialog.value = true
}
const openEdit = async row => {
  const response = await publicationDetail(row.id)
  form.value = { ...emptyForm(), ...response.data }
  dialog.value = true
}
const addAudience = () => form.value.audiences.push({
  subjectType: 'DEPARTMENT',
  subjectId: '',
  includeChildren: true,
  excluded: false,
})
const payload = () => ({
  publicationType: form.value.publicationType,
  contentType: form.value.contentType,
  title: form.value.title,
  summary: form.value.summary,
  content: form.value.content,
  importance: form.value.importance,
  pinned: form.value.pinned,
  mustRead: form.value.mustRead,
  audienceMode: form.value.audienceMode,
  readDeadline: form.value.readDeadline || null,
  approvalRequired: form.value.approvalRequired,
  approvalWorkflowDefinitionId: form.value.approvalWorkflowDefinitionId || null,
  sortOrder: form.value.sortOrder,
  publishAt: form.value.publishAt || null,
  expireAt: form.value.expireAt || null,
  audiences: form.value.audiences,
})
const save = async () => {
  if (!form.value.title.trim()) return ElMessage.warning('请输入标题')
  if (!form.value.audiences.some(item => !item.excluded)) return ElMessage.warning('至少配置一个正向可见范围')
  saving.value = true
  try {
    const response = form.value.id
      ? await updatePublication(form.value.id, payload())
      : await createPublication(payload())
    form.value = { ...emptyForm(), ...response.data }
    ElMessage.success('草稿已保存')
    await load()
  } finally {
    saving.value = false
  }
}
const publish = async row => {
  await ElMessageBox.confirm('确认发布该内容？到达计划时间后门户用户将可见。', '发布确认')
  await publishPublication(row.id)
  ElMessage.success('发布设置已生效')
  await load()
}
const submitReview = async row => {
  await submitPublication(row.id)
  ElMessage.success('已提交审核')
  await load()
}
const review = async (row, approved) => {
  const result = await ElMessageBox.prompt(
    approved ? '请输入审核意见（可选）' : '请输入驳回原因',
    approved ? '审核通过' : '审核驳回',
    { inputValidator: value => approved || !!value?.trim() || '驳回原因不能为空' },
  )
  if (approved) await approvePublication(row.id, result.value)
  else await rejectPublication(row.id, result.value)
  ElMessage.success(approved ? '审核已通过' : '已驳回')
  await load()
}
const showStatistics = async row => {
  const response = await publicationStatistics(row.id)
  statistics.value = response.data
  statisticsDialog.value = true
}
const showVersions = async row => {
  const response = await publicationVersions(row.id)
  versionRows.value = response.data || []
  versionPublicationId.value = row.id
  versionDialog.value = true
}
const restoreVersion = async version => {
  await ElMessageBox.confirm(`恢复版本 ${version.versionNo} 后将生成一个新的草稿版本，确认继续？`, '恢复历史版本')
  await restorePublicationVersion(versionPublicationId.value, version.versionNo)
  ElMessage.success('历史版本已恢复为新草稿')
  versionDialog.value = false
  await load()
}
const remindUnread = async () => {
  const response = await remindUnreadPublication(statistics.value.publicationId)
  ElMessage.success(`已为 ${response.data || 0} 名未读用户创建催办任务`)
}
const withdraw = async row => {
  await ElMessageBox.confirm('撤下后门户用户将立即不可见，确认继续？', '撤下确认')
  await withdrawPublication(row.id)
  ElMessage.success('内容已撤下')
  await load()
}
const remove = async row => {
  await ElMessageBox.confirm('确认删除该内容及其附件？', '删除确认', { type: 'warning' })
  await deletePublication(row.id)
  ElMessage.success('删除成功')
  await load()
}
const archive = async row => {
  await ElMessageBox.confirm('归档后门户保持不可见，并保留历史审计记录，确认继续？', '归档通知公告')
  await archivePublication(row.id)
  ElMessage.success('内容已归档')
  await load()
}
const uploadDocument = async uploadFile => {
  const response = await uploadPublicationAttachment(form.value.id, uploadFile.raw, true)
  form.value = { ...emptyForm(), ...response.data }
  ElMessage.success('文档已导入，正文已提取')
}
const downloadAttachment = async item => {
  const blob = await downloadAdminPublicationAttachment(item.id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = item.originalName
  anchor.click()
  URL.revokeObjectURL(url)
}
const removeAttachment = async item => {
  await ElMessageBox.confirm(`确认删除附件“${item.originalName}”？`, '删除附件')
  await deletePublicationAttachment(item.id)
  form.value.attachments = form.value.attachments.filter(attachment => attachment.id !== item.id)
  ElMessage.success('附件已删除')
}
const formatSize = bytes => bytes > 1024 * 1024
  ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
  : `${Math.ceil(bytes / 1024)} KB`
const statusLabel = value => statuses.find(item => item.value === value)?.label || value
const statusType = value => ({ PUBLISHED: 'success', SCHEDULED: 'warning', WITHDRAWN: 'info', EXPIRED: 'info' }[value] || '')

onMounted(async () => {
  await Promise.all([load(), loadDirectory()])
})
</script>

<style scoped>
.page { padding: 24px; }
.header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.header h2 { margin: 0 0 5px; }
.header p { margin: 0; color: #84909a; }
.filters { display: grid; grid-template-columns: 150px 150px minmax(220px, 1fr) auto; gap: 12px; margin-bottom: 16px; }
.el-pagination { justify-content: flex-end; margin-top: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 18px; }
.form-grid .full { grid-column: 1 / -1; }
.audience-row { display: grid; grid-template-columns: 150px minmax(220px, 1fr) auto auto auto; gap: 10px; align-items: center; margin-bottom: 10px; }
.attachment { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eef0f2; }
</style>
