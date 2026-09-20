<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAssessmentScheme,
  createGradebook,
  getGradebook,
  getKnowledgeScoreAnalysis,
  listAssessmentSchemes,
  listGradebookSnapshots,
  listGradebooks,
  publishGradebook,
  submitGradebook,
  updateGradebookItems,
} from '../../../api/admin'

const schemes = ref([])
const gradebooks = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const schemeForm = ref({
  name: '',
  offeringId: '',
  totalScore: 100,
  passScore: 60,
  components: [],
})
const componentDraft = ref({ code: '', name: '', weight: 0, maxScore: 100, sourceType: 'MANUAL' })
const selected = ref(null)
const draftItems = ref([])
const snapshots = ref([])
const knowledgeAnalysis = ref(null)
const route = useRoute()
const isKnowledgeAnalysis = computed(() => route.name === 'admin-education-score-knowledge-analysis')
const activeTab = ref('gradebooks')
const dirty = ref(false)
const snapshotLoading = ref(false)
const statusLabel = {
  DRAFT: '草稿',
  EDITING: '录入中',
  SUBMITTED: '已提交',
  REVIEWING: '审核中',
  APPROVED: '已审核',
  REJECTED: '已驳回',
  PUBLISHED: '已发布',
}
const specialStatusOptions = [
  { value: '', label: '正常' },
  { value: 'ABSENT', label: '缺考' },
  { value: 'EXEMPT', label: '免修' },
  { value: 'DEFERRED', label: '缓考' },
  { value: 'INVALID', label: '无效' },
]

const selectedStatus = computed(() => statusLabel[selected.value?.status] || selected.value?.status || '')
const components = computed(() => selected.value?.components || [])
const students = computed(() => selected.value?.students || [])
const readOnly = computed(() => selected.value?.status === 'PUBLISHED')
const canEdit = computed(() => ['EDITING', 'REJECTED'].includes(selected.value?.status))
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
  if (isKnowledgeAnalysis.value) {
    const response = await getKnowledgeScoreAnalysis()
    knowledgeAnalysis.value = response?.data || null
    return
  }
  loading.value = true
  try {
    const [schemeResponse, gradebookResponse] = await Promise.all([
      listAssessmentSchemes(),
      listGradebooks(),
    ])
    schemes.value = schemeResponse?.data || []
    gradebooks.value = gradebookResponse?.data || []
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
  } finally {
    detailLoading.value = false
  }
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
  await createAssessmentScheme({ ...schemeForm.value })
  ElMessage.success('考核方案已保存')
  schemeForm.value = { name: '', offeringId: '', totalScore: 100, passScore: 60, components: [] }
  await refresh()
}

async function createNewGradebook() {
  const publishedScheme = schemes.value.find((scheme) => scheme.status === 'PUBLISHED')
  if (!publishedScheme) {
    ElMessage.warning('请先创建并发布考核方案')
    return
  }
  await createGradebook({ schemeId: publishedScheme.id, offeringId: publishedScheme.offeringId })
  ElMessage.success('成绩册已创建')
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

onMounted(() => {
  refresh()
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

    <el-alert
      v-if="isKnowledgeAnalysis"
      type="info"
      :closable="false"
      show-icon
      class="knowledge-empty-state"
    >
      <template #title>知识点成绩分析暂不可用</template>
      <p>{{ knowledgeAnalysis?.reason || '正在检查知识点分析依赖。' }}</p>
      <div v-if="knowledgeAnalysis?.dependencies?.length" class="knowledge-dependencies">
        <div v-for="dependency in knowledgeAnalysis.dependencies" :key="dependency.code">
          <strong>{{ dependency.code }}</strong>
          <el-tag size="small" effect="plain">{{ dependency.status }}</el-tag>
          <span>{{ dependency.description }}</span>
        </div>
      </div>
    </el-alert>

    <el-tabs v-else v-model="activeTab">
      <el-tab-pane label="成绩册" name="gradebooks">
        <div class="toolbar">
          <el-button type="primary" @click="createNewGradebook">新建成绩册</el-button>
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
                <el-button :disabled="!canEdit" @click="saveItems">批量保存</el-button>
                <el-button type="warning" :disabled="dirty || !['EDITING', 'REJECTED'].includes(selected.status)" @click="submit">提交审核</el-button>
                <el-button type="success" :disabled="selected.status !== 'APPROVED'" @click="publish">发布</el-button>
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
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="考核方案" name="schemes">
        <el-form :model="schemeForm" inline>
          <el-form-item label="方案名称"><el-input v-model="schemeForm.name" placeholder="如：2026春季课程考核" /></el-form-item>
          <el-form-item label="课程开设"><el-input v-model="schemeForm.offeringId" placeholder="Offering ID" /></el-form-item>
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
          <el-button type="primary" :disabled="weightTotal !== 100" @click="saveScheme">保存方案</el-button>
        </div>
        <el-table :data="schemes" stripe>
          <el-table-column prop="name" label="方案名称" />
          <el-table-column prop="offeringId" label="课程开设" />
          <el-table-column prop="totalScore" label="总分" />
          <el-table-column prop="passScore" label="及格分" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
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
.knowledge-empty-state { max-width: 900px; }
.knowledge-empty-state p { margin: 8px 0 0; }
.knowledge-dependencies { display: grid; gap: 8px; margin-top: 12px; }
.knowledge-dependencies > div { display: flex; align-items: center; gap: 8px; }
</style>
