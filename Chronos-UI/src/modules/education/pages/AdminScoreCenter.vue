<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasAdminPermission } from '../../../store/auth'
import {
  createAssessmentScheme,
  createGradebook,
  dictionaryOptions,
  exportGradebook,
  getAssessmentScheme,
  getGradebook,
  listGradeOfferingOptions,
  listAssessmentSchemes,
  listGradebookSnapshots,
  listGradebooks,
  importGradebook,
  listPublishedGrades,
  listGradeChanges,
  listGradeChangeIncidents,
  listGradeMakeups,
  requestGradeChange,
  registerGradeMakeup,
  saveGradeMakeupResult,
  publishGradeMakeup,
  retryGradeChangeIncident,
  ignoreGradeChangeIncident,
  publishAssessmentScheme,
  publishGradebook,
  submitGradebook,
  updateGradebookItems,
  updateAssessmentScheme,
} from '../../../api/admin'

const schemes = ref([])
const offerings = ref([])
const gradebooks = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const emptySchemeForm = () => ({
  name: '',
  offeringId: '',
  totalScore: 100,
  passScore: 60,
  components: [],
})
const schemeForm = ref(emptySchemeForm())
const editingSchemeId = ref('')
const gradebookDialog = ref(false)
const gradebookForm = ref({ schemeId: '', offeringId: '' })
const componentDraft = ref({ code: '', name: '', weight: 0, maxScore: 100, sourceType: 'MANUAL' })
const selected = ref(null)
const draftItems = ref([])
const snapshots = ref([])
const activeTab = ref('gradebooks')
const dirty = ref(false)
const snapshotLoading = ref(false)
const importInput = ref(null)
const publishedGrades = ref([])
const changeRequests = ref([])
const makeupRecords = ref([])
const changeDialog = ref(false)
const changeForm = ref({ courseGradeId: '', studentName: '', afterScore: null, reason: '' })
const makeupDialog = ref(false)
const makeupForm = ref({ sourceGradeId: '', studentName: '', attemptType: 'MAKEUP', remark: '' })
const incidents = ref([])
const incidentStatus = ref('OPEN')
const incidentPage = ref(1)
const incidentPageSize = ref(20)
const incidentTotal = ref(0)
const incidentLoading = ref(false)
const statusLabel = {
  DRAFT: '草稿',
  EDITING: '录入中',
  SUBMITTED: '已提交',
  REVIEWING: '审核中',
  APPROVED: '已审核',
  REJECTED: '已驳回',
  PUBLISHED: '已发布',
}
const specialStatusOptions = ref([])

const selectedStatus = computed(() => statusLabel[selected.value?.status] || selected.value?.status || '')
const components = computed(() => selected.value?.components || [])
const students = computed(() => selected.value?.students || [])
const readOnly = computed(() => selected.value?.status === 'PUBLISHED')
const canEdit = computed(() => ['EDITING', 'REJECTED'].includes(selected.value?.status))
const publishedSchemes = computed(() => schemes.value.filter((scheme) => scheme.status === 'PUBLISHED'))
const weightTotal = computed(() => schemeForm.value.components.reduce(
  (total, item) => total + Number(item.weight || 0),
  0,
))
const matrixRows = computed(() => students.value.map((student) => ({
  ...student,
  scores: components.value.map((component) => findDraftItem(student.studentId, component.id)),
  total: calculateTotal(student.studentId),
})))

function findDraftItem(studentId, componentId) {
  return draftItems.value.find((item) => item.studentId === studentId && item.componentId === componentId)
}

function calculateTotal(studentId) {
  return components.value.reduce((total, component) => {
    const item = findDraftItem(studentId, component.id)
    if (item?.rawScore === null || item?.rawScore === undefined || item?.specialStatus) return total
    return total + (Number(item.rawScore) / Number(component.maxScore || 100)) * Number(component.weight || 0)
  }, 0)
}

function offeringLabel(offeringId) {
  const offering = offerings.value.find((item) => item.id === offeringId)
  if (!offering) return offeringId || '—'
  return `${offering.semesterCode} · ${offering.courseName} · ${offering.teachingClassName}`
}

function normalizeGradebook(book) {
  const data = book || {}
  const sourceItems = data.items || data.gradeItems || []
  const sourceStudents = data.students || data.roster || data.gradebookStudents || []
  const sourceComponents = data.components || data.assessmentComponents || []
  const normalizedItems = sourceItems.map((item) => ({ ...item, rawScore: item.rawScore ?? null, specialStatus: item.specialStatus || '' }))
  sourceStudents.forEach((student) => sourceComponents.forEach((component) => {
    if (!normalizedItems.some((item) => item.studentId === student.studentId && item.componentId === component.id)) {
      normalizedItems.push({
        studentId: student.studentId,
        componentId: component.id,
        rawScore: null,
        specialStatus: '',
        remark: null,
      })
    }
  }))
  return {
    ...data,
    students: sourceStudents,
    components: sourceComponents,
    items: normalizedItems,
  }
}

async function refresh() {
  loading.value = true
  try {
    // 审核人、录入人和方案管理人的权限可以相互独立。只请求当前账号有权
    // 访问的资源，避免某个辅助接口返回 403 导致整个成绩页面无法使用。
    const canReadSchemes = hasAdminPermission('education:score:scheme:view')
    const canReadGradebooks = hasAdminPermission('education:score:gradebook:view')
    const canReadOfferings = hasAdminPermission(
      'education:score:scheme:view',
      'education:score:scheme:create',
      'education:score:gradebook:view',
      'education:score:gradebook:create',
    )
    const [schemeResponse, gradebookResponse, offeringResponse] = await Promise.all([
      canReadSchemes ? listAssessmentSchemes() : Promise.resolve({ data: [] }),
      canReadGradebooks ? listGradebooks() : Promise.resolve({ data: [] }),
      canReadOfferings ? listGradeOfferingOptions() : Promise.resolve({ data: [] }),
    ])
    schemes.value = schemeResponse?.data || []
    gradebooks.value = gradebookResponse?.data || []
    offerings.value = offeringResponse?.data || []
  } finally {
    loading.value = false
  }
}

async function selectGradebook(book) {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前成绩有未保存修改，继续将丢失这些修改。', '未保存提示', {
        type: 'warning',
        confirmButtonText: '继续',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  detailLoading.value = true
  try {
    const response = await getGradebook(book.id)
    selected.value = normalizeGradebook(response?.data || book)
    draftItems.value = selected.value.items.map((item) => ({ ...item }))
    dirty.value = false
    await loadSnapshots()
    await loadProductionData()
  } finally {
    detailLoading.value = false
  }
}

async function loadProductionData() {
  if (!selected.value?.id || selected.value.status !== 'PUBLISHED') {
    publishedGrades.value = []
    changeRequests.value = []
    makeupRecords.value = []
    return
  }
  const [grades, changes, makeups] = await Promise.all([
    listPublishedGrades(selected.value.id),
    listGradeChanges(selected.value.id),
    listGradeMakeups(selected.value.id),
  ])
  publishedGrades.value = grades?.data || []
  changeRequests.value = changes?.data || []
  makeupRecords.value = makeups?.data || []
}

function openChange(row) {
  changeForm.value = { courseGradeId: row.id, studentName: row.studentName, afterScore: row.totalScore, reason: '' }
  changeDialog.value = true
}

async function submitChange() {
  await requestGradeChange(selected.value.id, changeForm.value)
  ElMessage.success('成绩更正申请已提交审核')
  changeDialog.value = false
  await loadProductionData()
}

function openMakeup(row) {
  makeupForm.value = { sourceGradeId: row.id, studentName: row.studentName, attemptType: 'MAKEUP', remark: '' }
  makeupDialog.value = true
}

async function submitMakeup() {
  await registerGradeMakeup(selected.value.id, makeupForm.value)
  ElMessage.success('补考/重修已登记')
  makeupDialog.value = false
  await loadProductionData()
}

async function enterMakeupScore(row) {
  const { value } = await ElMessageBox.prompt('请输入补考/重修成绩', '录入结果', {
    inputType: 'number',
    inputValue: row.resultScore ?? '',
  })
  await saveGradeMakeupResult(row.id, { resultScore: Number(value), remark: row.remark })
  ElMessage.success('结果已保存')
  await loadProductionData()
}

async function publishMakeupResult(row) {
  await ElMessageBox.confirm('发布后会生成新的成绩版本，历史成绩不会被覆盖。', '发布确认', { type: 'warning' })
  await publishGradeMakeup(row.id)
  ElMessage.success('补考/重修成绩已发布')
  await loadProductionData()
}

async function loadIncidents() {
  if (!hasAdminPermission('education:score:incident:manage')) return
  incidentLoading.value = true
  try {
    const response = await listGradeChangeIncidents({
      status: incidentStatus.value,
      page: incidentPage.value - 1,
      size: incidentPageSize.value,
    })
    incidents.value = response?.data?.content || []
    incidentTotal.value = response?.data?.totalElements || 0
  } finally {
    incidentLoading.value = false
  }
}

async function searchIncidents() {
  incidentPage.value = 1
  await loadIncidents()
}

async function retryIncident(row) {
  await ElMessageBox.confirm(
    '重试前会再次确认工作流已完成，并幂等写入成绩新版本。是否继续？',
    '重试成绩回写',
    { type: 'warning' },
  )
  await retryGradeChangeIncident(row.id)
  ElMessage.success('成绩更正已成功回写')
  await loadIncidents()
}

async function ignoreIncident(row) {
  const { value } = await ElMessageBox.prompt(
    '忽略只会关闭事故告警，不会将成绩更正单伪造为已回写。请填写原因。',
    '忽略成绩事故',
    {
      inputType: 'textarea',
      inputValidator: input => Boolean(input?.trim()) || '请填写忽略原因',
      confirmButtonText: '确认忽略',
    },
  )
  await ignoreGradeChangeIncident(row.id, value.trim())
  ElMessage.success('事故已忽略')
  await loadIncidents()
}

function markDirty() {
  dirty.value = true
}

async function loadSnapshots() {
  if (!selected.value?.id) return
  snapshotLoading.value = true
  try {
    const response = await listGradebookSnapshots(selected.value.id)
    snapshots.value = response?.data || []
  } finally {
    snapshotLoading.value = false
  }
}

function addSchemeComponent() {
  if (!componentDraft.value.name || !componentDraft.value.code) {
    ElMessage.warning('请填写成绩项目编码和名称')
    return
  }
  schemeForm.value.components.push({ ...componentDraft.value })
  componentDraft.value = { code: '', name: '', weight: 0, maxScore: 100, sourceType: 'MANUAL' }
}

function removeSchemeComponent(index) {
  schemeForm.value.components.splice(index, 1)
}

async function saveScheme() {
  if (!schemeForm.value.name || !schemeForm.value.offeringId) {
    ElMessage.warning('请填写方案名称和课程开设')
    return
  }
  if (weightTotal.value !== 100) {
    ElMessage.warning('成绩项目权重合计必须为100%')
    return
  }
  if (editingSchemeId.value) {
    await updateAssessmentScheme(editingSchemeId.value, { ...schemeForm.value })
    ElMessage.success('考核方案已更新')
  } else {
    await createAssessmentScheme({ ...schemeForm.value })
    ElMessage.success('考核方案已创建')
  }
  resetSchemeForm()
  await refresh()
}

async function editScheme(row) {
  const response = await getAssessmentScheme(row.id)
  const detail = response?.data
  if (!detail || detail.status !== 'DRAFT') {
    ElMessage.warning('仅草稿方案可以编辑')
    return
  }
  editingSchemeId.value = detail.id
  schemeForm.value = {
    name: detail.name,
    offeringId: detail.offeringId,
    totalScore: Number(detail.totalScore),
    passScore: Number(detail.passScore),
    rowVersion: detail.rowVersion,
    components: (detail.components || []).map((item) => ({
      code: item.code,
      name: item.name,
      sourceType: item.sourceType,
      weight: Number(item.weight),
      maxScore: Number(item.maxScore),
      sortOrder: item.sortOrder,
    })),
  }
}

function resetSchemeForm() {
  editingSchemeId.value = ''
  schemeForm.value = emptySchemeForm()
}

async function publishScheme(row) {
  await ElMessageBox.confirm('发布后方案将冻结，不能继续修改。是否发布？', '发布考核方案', {
    type: 'warning',
  })
  await publishAssessmentScheme(row.id)
  ElMessage.success('考核方案已发布')
  if (editingSchemeId.value === row.id) resetSchemeForm()
  await refresh()
}

function createNewGradebook() {
  if (!publishedSchemes.value.length) {
    ElMessage.warning('请先创建并发布考核方案')
    return
  }
  gradebookForm.value = { schemeId: '', offeringId: '' }
  gradebookDialog.value = true
}

function selectGradebookScheme(schemeId) {
  const scheme = publishedSchemes.value.find((item) => item.id === schemeId)
  gradebookForm.value.offeringId = scheme?.offeringId || ''
}

async function confirmCreateGradebook() {
  if (!gradebookForm.value.schemeId || !gradebookForm.value.offeringId) {
    ElMessage.warning('请选择已发布考核方案')
    return
  }
  await createGradebook(gradebookForm.value)
  ElMessage.success('成绩册已创建')
  gradebookDialog.value = false
  await refresh()
}

async function saveItems() {
  if (!selected.value || !canEdit.value) return
  await updateGradebookItems(selected.value.id, {
    rowVersion: selected.value.rowVersion,
    items: draftItems.value.map(({ studentId, componentId, rawScore, specialStatus, remark }) => ({
      studentId,
      componentId,
      rawScore: rawScore === '' ? null : rawScore,
      specialStatus: specialStatus || null,
      remark: remark || null,
    })),
  })
  dirty.value = false
  ElMessage.success('成绩已批量保存')
  await refresh()
  await selectGradebook(selected.value)
}

async function submit() {
  if (!selected.value || dirty.value) {
    ElMessage.warning('请先保存成绩')
    return
  }
  await submitGradebook(selected.value.id, { rowVersion: selected.value.rowVersion })
  ElMessage.success('已提交审核')
  await refresh()
  await selectGradebook(selected.value)
}

async function publish() {
  if (!selected.value) return
  await publishGradebook(selected.value.id, { rowVersion: selected.value.rowVersion })
  ElMessage.success('成绩册已发布')
  await refresh()
  await selectGradebook(selected.value)
}

async function downloadGradebook() {
  if (!selected.value) return
  const blob = await exportGradebook(selected.value.id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `成绩册-${selected.value.id}.xlsx`
  anchor.click()
  URL.revokeObjectURL(url)
}

function chooseImportFile() {
  importInput.value?.click()
}

async function uploadGradebook(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !selected.value) return
  const validation = await importGradebook(selected.value.id, file, true)
  if (!validation?.data?.valid) {
    ElMessage.error((validation?.data?.errors || []).slice(0, 3).join('；') || '文件校验失败')
    return
  }
  await importGradebook(selected.value.id, file, false)
  ElMessage.success(`已导入 ${validation.data.imported} 条成绩`)
  await selectGradebook(selected.value)
}

function beforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function snapshotContent(snapshot) {
  try {
    return JSON.stringify(JSON.parse(snapshot || '{}'), null, 2)
  } catch {
    return snapshot || '{}'
  }
}

onMounted(async () => {
  // 特殊成绩状态由字典统一管理，避免各页面各自维护枚举导致口径不一致。
  try {
    const response = await dictionaryOptions('EDU_GRADE_SPECIAL_STATUS')
    specialStatusOptions.value = [
      { value: '', label: '正常' },
      ...(response?.data || []).map((item) => ({
        value: item.dictValue,
        label: item.dictName,
      })),
    ]
    if (specialStatusOptions.value.length === 1) {
      ElMessage.warning('成绩特殊状态字典未配置，请联系管理员')
    }
  } catch (error) {
    specialStatusOptions.value = [{ value: '', label: '正常' }]
    ElMessage.error(error?.message || '成绩特殊状态字典加载失败')
  }
  await refresh()
  await loadIncidents()
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="score-center">
    <header class="page-header">
      <div>
        <h1>成绩中心</h1>
        <p>维护考核方案、人工录入成绩并完成提交审核与发布。</p>
      </div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="成绩册" name="gradebooks">
        <div class="toolbar">
          <el-button
            v-permission="['education:score:gradebook:create']"
            type="primary"
            @click="createNewGradebook"
          >
            新建成绩册
          </el-button>
        </div>
        <el-table v-loading="loading" :data="gradebooks" stripe @row-click="selectGradebook">
          <el-table-column prop="name" label="成绩册" min-width="180" />
          <el-table-column prop="offeringName" label="课程开设" min-width="180" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ statusLabel[row.status] || row.status }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
        </el-table>

        <el-card v-if="selected" v-loading="detailLoading" class="editor">
          <template #header>
            <div class="editor-header">
              <span>{{ selected.name || '成绩录入' }} · {{ selectedStatus }}</span>
              <span class="editor-actions">
                <el-tag v-if="dirty" type="warning">有未保存修改</el-tag>
                <input ref="importInput" class="hidden-file" type="file" accept=".xlsx" @change="uploadGradebook">
                <el-button
                  v-permission="['education:score:gradebook:export']"
                  @click="downloadGradebook"
                >
                  导出/下载模板
                </el-button>
                <el-button
                  v-permission="['education:score:gradebook:import']"
                  :disabled="!canEdit"
                  @click="chooseImportFile"
                >
                  Excel 导入
                </el-button>
                <el-button
                  v-permission="['education:score:gradebook:update']"
                  :disabled="!canEdit"
                  @click="saveItems"
                >
                  批量保存
                </el-button>
                <el-button
                  v-permission="['education:score:gradebook:submit']"
                  type="warning"
                  :disabled="dirty || !['EDITING', 'REJECTED'].includes(selected.status)"
                  @click="submit"
                >
                  提交审核
                </el-button>
                <el-button
                  v-permission="['education:score:gradebook:publish']"
                  type="success"
                  :disabled="selected.status !== 'APPROVED'"
                  @click="publish"
                >
                  发布
                </el-button>
              </span>
            </div>
          </template>

          <el-alert v-if="!components.length || !students.length" type="info" :closable="false" show-icon>
            成绩册详情未返回学生或成绩项目，暂时无法展示录入表格。
          </el-alert>
          <div v-else class="grade-grid">
            <el-table :data="matrixRows" border stripe height="460">
              <el-table-column fixed="left" prop="studentName" label="学生" min-width="160" />
              <el-table-column fixed="left" prop="studentNo" label="学号" width="130" />
              <el-table-column
                v-for="component in components"
                :key="component.id"
                :label="`${component.name} (${component.weight}%)`"
                :min-width="180"
              >
                <template #default="{ row }">
                  <div class="score-cell">
                    <el-input-number
                      :model-value="findDraftItem(row.studentId, component.id).rawScore"
                      :min="0"
                      :max="Number(component.maxScore || 100)"
                      :precision="2"
                      controls-position="right"
                      :disabled="!canEdit"
                      @update:model-value="(value) => { findDraftItem(row.studentId, component.id).rawScore = value; markDirty() }"
                      @change="markDirty"
                    />
                    <el-select
                      :model-value="findDraftItem(row.studentId, component.id).specialStatus"
                      clearable
                      :disabled="!canEdit"
                      placeholder="状态"
                      @update:model-value="(value) => { findDraftItem(row.studentId, component.id).specialStatus = value || ''; markDirty() }"
                      @change="markDirty"
                    >
                      <el-option v-for="option in specialStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                  </div>
                </template>
              </el-table-column>
              <el-table-column fixed="right" label="自动汇总" width="120">
                <template #default="{ row }">{{ row.total.toFixed(2) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <el-divider />
          <div class="snapshot-header">
            <strong>成绩册发布快照历史</strong>
            <el-button text :loading="snapshotLoading" @click="loadSnapshots">刷新历史</el-button>
          </div>
          <el-table v-loading="snapshotLoading" :data="snapshots" size="small" border>
            <el-table-column prop="versionNo" label="版本" width="90" />
            <el-table-column prop="publishedAt" label="发布时间" width="190" />
            <el-table-column prop="publishedBy" label="发布人" min-width="140" />
            <el-table-column prop="snapshotHash" label="快照哈希" min-width="260" />
            <el-table-column label="只读快照" width="110">
              <template #default="{ row }">
                <el-popover placement="left" width="440" trigger="click">
                  <template #reference><el-button link type="primary">查看</el-button></template>
                  <pre class="snapshot-json">{{ snapshotContent(row.snapshotJson) }}</pre>
                </el-popover>
              </template>
            </el-table-column>
          </el-table>

          <template v-if="selected.status === 'PUBLISHED'">
            <el-divider />
            <strong>已发布成绩与更正</strong>
            <el-table :data="publishedGrades" size="small" border>
              <el-table-column prop="studentNo" label="学号" width="140" />
              <el-table-column prop="studentName" label="姓名" min-width="140" />
              <el-table-column prop="totalScore" label="当前成绩" width="110" />
              <el-table-column prop="versionNo" label="版本" width="90" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button
                    v-permission="['education:score:change:request']"
                    link
                    type="primary"
                    @click="openChange(row)"
                  >
                    申请更正
                  </el-button>
                  <el-button v-if="!row.passed" v-permission="['education:score:makeup:manage']" link type="warning" @click="openMakeup(row)">补考/重修</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-divider />
            <strong>更正申请</strong>
            <el-table :data="changeRequests" size="small" border>
              <el-table-column prop="studentId" label="学生ID" min-width="160" />
              <el-table-column prop="beforeScore" label="原成绩" width="100" />
              <el-table-column prop="afterScore" label="拟更正" width="100" />
              <el-table-column prop="reason" label="原因" min-width="200" />
              <el-table-column prop="status" label="状态" width="110" />
            </el-table>
            <el-divider />
            <strong>补考与重修</strong>
            <el-table :data="makeupRecords" size="small" border>
              <el-table-column prop="studentId" label="学生ID" min-width="160" />
              <el-table-column prop="attemptType" label="类型" width="100" />
              <el-table-column prop="resultScore" label="成绩" width="100" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button v-if="row.status !== 'PUBLISHED'" v-permission="['education:score:makeup:manage']" link @click="enterMakeupScore(row)">录入</el-button>
                  <el-button v-if="row.status === 'SCORED'" v-permission="['education:score:makeup:manage']" link type="success" @click="publishMakeupResult(row)">发布</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="考核方案" name="schemes">
        <el-form :model="schemeForm" inline>
          <el-form-item label="方案名称"><el-input v-model="schemeForm.name" placeholder="如：2026春季课程考核" /></el-form-item>
          <el-form-item label="课程开设">
            <el-select
              v-model="schemeForm.offeringId"
              filterable
              :disabled="Boolean(editingSchemeId)"
              placeholder="选择课程开设"
              style="width: 320px"
            >
              <el-option
                v-for="offering in offerings"
                :key="offering.id"
                :label="offeringLabel(offering.id)"
                :value="offering.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="总分"><el-input-number v-model="schemeForm.totalScore" :min="1" /></el-form-item>
          <el-form-item label="及格分"><el-input-number v-model="schemeForm.passScore" :min="0" /></el-form-item>
        </el-form>
        <div class="component-builder">
          <el-input v-model="componentDraft.code" placeholder="项目编码" />
          <el-input v-model="componentDraft.name" placeholder="项目名称" />
          <el-input-number v-model="componentDraft.weight" :min="0" :max="100" placeholder="权重%" />
          <el-input-number v-model="componentDraft.maxScore" :min="1" placeholder="满分" />
          <el-button @click="addSchemeComponent">添加项目</el-button>
        </div>
        <el-table :data="schemeForm.components" size="small" border>
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="name" label="项目" />
          <el-table-column prop="weight" label="权重%" />
          <el-table-column prop="maxScore" label="满分" />
          <el-table-column label="操作" width="80">
            <template #default="{ $index }"><el-button link type="danger" @click="removeSchemeComponent($index)">删除</el-button></template>
          </el-table-column>
        </el-table>
        <div class="scheme-footer">
          <span :class="{ invalid: weightTotal !== 100 }">权重合计：{{ weightTotal }}%</span>
          <el-button v-if="editingSchemeId" @click="resetSchemeForm">取消编辑</el-button>
          <el-button
            v-permission="['education:score:scheme:create', 'education:score:scheme:update']"
            type="primary"
            :disabled="weightTotal !== 100"
            @click="saveScheme"
          >
            {{ editingSchemeId ? '保存修改' : '创建方案' }}
          </el-button>
        </div>
        <el-table :data="schemes" stripe>
          <el-table-column prop="name" label="方案名称" />
          <el-table-column label="课程开设" min-width="260">
            <template #default="{ row }">{{ offeringLabel(row.offeringId) }}</template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总分" />
          <el-table-column prop="passScore" label="及格分" />
          <el-table-column label="状态">
            <template #default="{ row }">{{ statusLabel[row.status] || row.status }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'DRAFT'"
                v-permission="['education:score:scheme:update']"
                link
                type="primary"
                @click="editScheme(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="row.status === 'DRAFT'"
                v-permission="['education:score:scheme:publish']"
                link
                type="success"
                @click="publishScheme(row)"
              >
                发布
              </el-button>
              <el-tag v-else type="success" size="small">已冻结</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane
        v-if="hasAdminPermission('education:score:incident:manage')"
        label="回写事故"
        name="incidents"
      >
        <div class="incident-toolbar">
          <el-select v-model="incidentStatus" style="width: 160px" @change="searchIncidents">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已恢复" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
          <el-button :loading="incidentLoading" @click="loadIncidents">刷新</el-button>
        </div>
        <el-table v-loading="incidentLoading" :data="incidents" border>
          <el-table-column prop="changeRequestId" label="更正单ID" min-width="210" />
          <el-table-column prop="workflowInstanceId" label="流程实例ID" min-width="210" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="retryCount" label="重试次数" width="100" />
          <el-table-column prop="lastError" label="最近错误" min-width="260" show-overflow-tooltip />
          <el-table-column prop="createTime" label="发生时间" width="180" />
          <el-table-column v-if="incidentStatus === 'OPEN'" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="retryIncident(row)">重试</el-button>
              <el-button link type="danger" @click="ignoreIncident(row)">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="incidentPage"
          v-model:page-size="incidentPageSize"
          class="incident-pagination"
          layout="total, sizes, prev, pager, next"
          :total="incidentTotal"
          @size-change="searchIncidents"
          @current-change="loadIncidents"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="changeDialog" title="申请成绩更正" width="480px">
      <el-form label-width="90px">
        <el-form-item label="学生"><el-input :model-value="changeForm.studentName" disabled /></el-form-item>
        <el-form-item label="更正成绩"><el-input-number v-model="changeForm.afterScore" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="更正原因"><el-input v-model="changeForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="changeDialog = false">取消</el-button><el-button type="primary" @click="submitChange">提交审核</el-button></template>
    </el-dialog>
    <el-dialog v-model="makeupDialog" title="登记补考/重修" width="480px">
      <el-form label-width="90px">
        <el-form-item label="学生"><el-input :model-value="makeupForm.studentName" disabled /></el-form-item>
        <el-form-item label="类型"><el-radio-group v-model="makeupForm.attemptType"><el-radio value="MAKEUP">补考</el-radio><el-radio value="RETAKE">重修</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="makeupForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="makeupDialog = false">取消</el-button><el-button type="primary" @click="submitMakeup">登记</el-button></template>
    </el-dialog>
    <el-dialog v-model="gradebookDialog" title="新建成绩册" width="560px">
      <el-form label-width="100px">
        <el-form-item label="考核方案">
          <el-select
            v-model="gradebookForm.schemeId"
            filterable
            placeholder="选择已发布方案"
            style="width: 100%"
            @change="selectGradebookScheme"
          >
            <el-option
              v-for="scheme in publishedSchemes"
              :key="scheme.id"
              :label="`${scheme.name} · ${offeringLabel(scheme.offeringId)}`"
              :value="scheme.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程开设">
          <el-input :model-value="offeringLabel(gradebookForm.offeringId)" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradebookDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateGradebook">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.score-center { padding: 24px; }
.page-header, .editor-header, .snapshot-header, .scheme-footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.page-header { margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; }
.page-header p { margin: 0; color: var(--el-text-color-secondary); }
.toolbar { margin-bottom: 16px; }
.editor { margin-top: 20px; }
.editor-actions { display: flex; align-items: center; gap: 8px; }
.grade-grid { overflow-x: auto; }
.score-cell { display: grid; gap: 6px; }
.component-builder { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; }
.component-builder .el-input { width: 180px; }
.scheme-footer { justify-content: flex-end; margin: 16px 0; }
.invalid { color: var(--el-color-danger); }
.snapshot-header { margin: 12px 0; }
.snapshot-json { max-height: 360px; overflow: auto; margin: 0; white-space: pre-wrap; word-break: break-all; }
.hidden-file { display: none; }
.incident-toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.incident-pagination { justify-content: flex-end; margin-top: 16px; }
</style>
