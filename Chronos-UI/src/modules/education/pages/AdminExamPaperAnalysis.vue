<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createExamPaperItem,
  confirmExamScores,
  deleteExamPaperItem,
  getExamPaperAnalysis,
  listAvailableExamPaperQuestions,
  listAcademicTerms,
  lookupEducationStudents,
  listEducationSubjects,
  listExamCandidates,
  listExamItemScores,
  listExamPaperItems,
  listExamPlans,
  listExamRooms,
  listExamSessions,
  saveExamItemScore,
  saveExamItemScoresBatch,
  publishExamScores,
} from '../../../api/admin'

const terms = ref([])
const semesterCode = ref('')
const plans = ref([])
const sessions = ref([])
const subjects = ref([])
const students = ref([])
const items = ref([])
const analysis = ref([])
const candidates = ref([])
const selectedPlan = ref(null)
const selectedSession = ref(null)
const selectedItem = ref(null)
const itemDialog = ref(false)
const batchDialog = ref(false)
const batchText = ref('')
const availableQuestions = ref([])
const itemForm = reactive({ questionNo: '', questionId: '', title: '', maxScore: 10 })
const scoreByCandidate = reactive({})
const busy = ref(false)

const unwrap = (response) => response?.data?.content || response?.data || []
const subjectName = (id) => subjects.value.find((item) => item.id === id)?.subjectName || id
const studentName = (id) => students.value.find((item) => item.id === id)?.studentName || id

async function run(action, success) {
  busy.value = true
  try {
    await action()
    if (success) ElMessage.success(success)
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

async function loadPlans() {
  plans.value = semesterCode.value ? unwrap(await listExamPlans(semesterCode.value)) : []
  await selectPlan(null)
}

async function selectPlan(plan) {
  selectedPlan.value = plan
  selectedSession.value = null
  sessions.value = plan ? unwrap(await listExamSessions(plan.id)) : []
  await selectSession(null)
}

async function selectSession(session) {
  selectedSession.value = session
  selectedItem.value = null
  candidates.value = []
  items.value = []
  analysis.value = []
  if (!session) return
  const [itemResult, analysisResult, roomResult] = await Promise.all([
    listExamPaperItems(session.id),
    getExamPaperAnalysis(session.id),
    listExamRooms(session.id),
  ])
  items.value = unwrap(itemResult)
  analysis.value = unwrap(analysisResult)
  const candidateResults = await Promise.all(unwrap(roomResult).map((room) => listExamCandidates(room.id)))
  candidates.value = candidateResults.flatMap(unwrap)
  const studentIds = [...new Set(candidates.value.map((candidate) => candidate.studentId))]
  const batches = []
  for (let index = 0; index < studentIds.length; index += 100) batches.push(studentIds.slice(index, index + 100))
  students.value = (await Promise.all(batches.map((batch) => lookupEducationStudents(batch))))
    .flatMap(unwrap)
}

async function selectItem(item) {
  selectedItem.value = item
  Object.keys(scoreByCandidate).forEach((key) => delete scoreByCandidate[key])
  if (!item) return
  const saved = unwrap(await listExamItemScores(selectedSession.value.id, item.id))
  saved.forEach((value) => { scoreByCandidate[value.candidateId] = Number(value.score) })
}

async function openItem() {
  const response = await listAvailableExamPaperQuestions(selectedSession.value.id)
  availableQuestions.value = unwrap(response)
  Object.assign(itemForm, { questionNo: '', questionId: '', title: '', maxScore: 10 })
  itemDialog.value = true
}

function selectQuestion(questionId) {
  const question = availableQuestions.value.find((item) => item.id === questionId)
  if (!question) return
  itemForm.title = question.stem
  itemForm.maxScore = Number(question.score || 10)
}

function exportAnalysis() {
  if (!analysis.value.length) return ElMessage.warning('当前场次没有可导出的题目统计')
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const headers = ['题号', '题目', '满分', '考生人数', '已评分人数', '平均分', '得分率', '满分人数', '零分人数']
  const rows = analysis.value.map((item) => [
    item.questionNo,
    item.title,
    item.maxScore,
    item.candidateCount,
    item.gradedCount,
    item.averageScore,
    item.scoreRate,
    item.fullScoreCount,
    item.zeroScoreCount,
  ])
  const csv = [headers, ...rows].map((row) => row.map(escape).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `试卷分析-${selectedSession.value.examDate}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function saveItem() {
  if (!itemForm.questionNo.trim() || !itemForm.questionId
    || !itemForm.title.trim() || Number(itemForm.maxScore) <= 0) {
    return ElMessage.warning('请选择题库题目并填写题号和有效满分')
  }
  await run(async () => {
    await createExamPaperItem(selectedSession.value.id, itemForm)
    itemDialog.value = false
    await selectSession(selectedSession.value)
  }, '试卷题目已新增')
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm('确认删除此题？已有评分的题目不能删除。', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await run(async () => {
    await deleteExamPaperItem(selectedSession.value.id, item.id)
    await selectSession(selectedSession.value)
  }, '题目已删除')
}

async function saveScore(candidate) {
  const score = scoreByCandidate[candidate.id]
  if (score === undefined || score === null || score < 0
    || score > Number(selectedItem.value.maxScore)) {
    return ElMessage.warning('得分必须在 0 和题目满分之间')
  }
  await run(async () => {
    await saveExamItemScore(selectedSession.value.id, selectedItem.value.id, {
      candidateId: candidate.id,
      score,
    })
    analysis.value = unwrap(await getExamPaperAnalysis(selectedSession.value.id))
  }, '得分已保存')
}

function openBatchScores() {
  batchText.value = ['学号,得分', ...candidates.value.map((candidate) =>
    `${students.value.find((student) => student.id === candidate.studentId)?.studentNo || candidate.studentId},`)].join('\n')
  batchDialog.value = true
}

async function saveBatchScores() {
  const lines = batchText.value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (lines[0]?.replaceAll('，', ',') === '学号,得分') lines.shift()
  if (!lines.length) return ElMessage.warning('请填写学号和得分')
  const rows = []
  const seen = new Set()
  for (const line of lines) {
    const parts = line.replaceAll('，', ',').split(/[,\t]/).map((part) => part.trim())
    if (parts.length !== 2 || !parts[0] || !/^\d+(\.\d{1,2})?$/.test(parts[1])) {
      return ElMessage.warning(`成绩行格式错误：${line}`)
    }
    const student = students.value.find((item) => item.studentNo === parts[0])
    const candidate = candidates.value.find((item) => item.studentId === student?.id)
    if (!candidate || seen.has(candidate.id) || Number(parts[1]) > Number(selectedItem.value.maxScore)) {
      return ElMessage.warning(`考生重复、未在本场考试或分数越界：${parts[0]}`)
    }
    seen.add(candidate.id)
    rows.push({ candidateId: candidate.id, score: Number(parts[1]) })
  }
  await run(async () => {
    await saveExamItemScoresBatch(selectedSession.value.id, selectedItem.value.id, rows)
    batchDialog.value = false
    await selectItem(selectedItem.value)
    analysis.value = unwrap(await getExamPaperAnalysis(selectedSession.value.id))
  }, `已批量保存 ${rows.length} 名考生得分`)
}

async function transitionScores(action) {
  const publishing = action === 'publish'
  try {
    await ElMessageBox.confirm(
      publishing
        ? '发布后将生成正式错题记录，且不能再修改逐题得分。是否继续？'
        : '确认后将冻结当前场次全部逐题得分。是否继续？',
      publishing ? '确认发布成绩' : '确认逐题成绩',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await run(async () => {
    const response = publishing
      ? await publishExamScores(selectedSession.value.id)
      : await confirmExamScores(selectedSession.value.id)
    Object.assign(selectedSession.value, response?.data || {})
  }, publishing ? '成绩已发布并完成错题沉淀' : '逐题成绩已确认')
}

onMounted(() => run(async () => {
  const [termResult, subjectResult] = await Promise.all([
    listAcademicTerms(),
    listEducationSubjects(),
  ])
  terms.value = unwrap(termResult)
  subjects.value = unwrap(subjectResult)
  semesterCode.value = terms.value.find((item) => item.currentTerm)?.termCode
    || terms.value[0]?.termCode || ''
  await loadPlans()
}))
</script>

<template>
  <div class="exam-page">
    <header class="page-header">
      <div><h2>试卷分析</h2><p>基于场次试卷题目和考生逐题实得分统计，不将未评分考生计为零分。</p></div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadPlans">
        <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
      </el-select>
    </header>
    <div class="selector-row">
      <el-select v-model="selectedPlan" value-key="id" placeholder="选择考试计划" @change="selectPlan">
        <el-option v-for="plan in plans" :key="plan.id" :label="plan.planName" :value="plan" />
      </el-select>
      <el-select v-model="selectedSession" value-key="id" placeholder="选择科目场次" @change="selectSession">
        <el-option v-for="session in sessions" :key="session.id" :label="`${subjectName(session.subjectId)} · ${session.examDate}`" :value="session" />
      </el-select>
    </div>
    <div class="section-heading">
      <h3>逐题统计</h3>
      <div class="selector-row">
        <el-tag v-if="selectedSession">成绩状态：{{ selectedSession.scoreStatus || 'DRAFT' }}</el-tag>
        <el-button
          v-if="selectedSession && (!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT')"
          v-permission="['education:exam:paper-analysis:manage']"
          type="warning" @click="transitionScores('confirm')">确认成绩</el-button>
        <el-button
          v-if="selectedSession?.scoreStatus === 'CONFIRMED'"
          v-permission="['education:exam:paper-analysis:manage']"
          type="success" @click="transitionScores('publish')">发布成绩</el-button>
        <el-button
          v-if="selectedSession && (!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT')"
          v-permission="['education:exam:paper-analysis:export']"
          @click="exportAnalysis">导出统计</el-button>
        <el-button
          v-if="selectedSession && (!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT')"
          v-permission="['education:exam:paper-analysis:manage']"
          type="primary" @click="openItem">新增试卷题目</el-button>
      </div>
    </div>
    <el-table :data="analysis" border highlight-current-row empty-text="请选择场次；未录入题目时暂无分析数据" @current-change="(row) => selectItem(items.find((item) => item.id === row?.itemId) || null)">
      <el-table-column prop="questionNo" label="题号" width="90" />
      <el-table-column prop="title" label="题目" min-width="200" />
      <el-table-column label="题库版本" min-width="150">
        <template #default="scope">
          <el-tooltip v-if="items.find((item) => item.id === scope.row.itemId)?.questionVersionId"
            :content="`版本 ${items.find((item) => item.id === scope.row.itemId).questionVersionId}；快照哈希 ${items.find((item) => item.id === scope.row.itemId).questionSnapshotHash}`">
            <span>{{ items.find((item) => item.id === scope.row.itemId).questionVersionId.slice(0, 8) }}… 已锁定</span>
          </el-tooltip>
          <span v-else>历史题目待核验</span>
        </template>
      </el-table-column>
      <el-table-column prop="maxScore" label="满分" width="90" />
      <el-table-column label="评分覆盖" width="120"><template #default="scope">{{ scope.row.gradedCount }}/{{ scope.row.candidateCount }}</template></el-table-column>
      <el-table-column label="平均分" width="100"><template #default="scope">{{ scope.row.averageScore ?? '—' }}</template></el-table-column>
      <el-table-column label="得分率" width="100"><template #default="scope">{{ scope.row.scoreRate == null ? '—' : `${scope.row.scoreRate}%` }}</template></el-table-column>
      <el-table-column prop="fullScoreCount" label="满分人数" width="110" />
      <el-table-column prop="zeroScoreCount" label="零分人数" width="110" />
      <AdaptiveActionColumn label="操作" width="90">
        <template #default="scope">
            <el-button v-if="!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT'" v-permission="['education:exam:paper-analysis:manage']" link type="danger" @click.stop="removeItem(scope.row)">删除</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>

    <template v-if="selectedItem">
      <div class="section-heading"><h3>第 {{ selectedItem.questionNo }} 题 · 考生得分</h3>
        <el-button v-if="selectedSession.status === 'PUBLISHED' && (!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT')"
          v-permission="['education:exam:paper-analysis:manage']" @click="openBatchScores">批量录入</el-button>
      </div>
      <el-table :data="candidates" border>
        <el-table-column prop="seatNo" label="座位号" width="100" />
        <el-table-column label="考生" min-width="160"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column>
        <el-table-column label="得分" width="180">
          <template #default="scope">
            <el-input-number v-model="scoreByCandidate[scope.row.id]" :disabled="selectedSession.scoreStatus && selectedSession.scoreStatus !== 'DRAFT'" :min="0" :max="Number(selectedItem.maxScore)" :precision="2" :step="0.5" />
          </template>
        </el-table-column>
        <AdaptiveActionColumn label="操作" width="100">
          <template #default="scope">
            <el-button v-if="!selectedSession.scoreStatus || selectedSession.scoreStatus === 'DRAFT'" v-permission="['education:exam:paper-analysis:manage']" link type="primary" @click="saveScore(scope.row)">保存</el-button>
          </template>
        </AdaptiveActionColumn>
      </el-table>
    </template>

    <el-dialog v-model="batchDialog" title="批量录入逐题得分" width="640px">
      <p>按“学号,得分”逐行填写；可从电子表格复制两列。空得分行请先填写，提交时整批校验。</p>
      <el-input v-model="batchText" type="textarea" :rows="16" placeholder="学号,得分" />
      <template #footer><el-button @click="batchDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveBatchScores">保存本批成绩</el-button></template>
    </el-dialog>

    <el-dialog v-model="itemDialog" title="新增试卷题目" width="500px">
      <el-form label-width="80px">
        <el-form-item label="题号"><el-input v-model="itemForm.questionNo" maxlength="32" /></el-form-item>
        <el-form-item label="题库题目">
          <el-select v-model="itemForm.questionId" filterable placeholder="选择已发布题目" @change="selectQuestion">
            <el-option v-for="question in availableQuestions" :key="question.id" :label="question.stem" :value="question.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布题干"><el-input v-model="itemForm.title" maxlength="200" readonly /></el-form-item>
        <el-form-item label="满分"><el-input-number v-model="itemForm.maxScore" :min="0.01" :precision="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="itemDialog = false">取消</el-button><el-button type="primary" @click="saveItem">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exam-page { padding: 20px; }
.page-header, .section-heading, .selector-row { display: flex; align-items: center; gap: 12px; }
.page-header, .section-heading { justify-content: space-between; }
.page-header { margin-bottom: 18px; }
.page-header h2, .section-heading h3 { margin: 0; }
.page-header p { margin: 6px 0 0; color: #777; }
.section-heading { margin: 24px 0 12px; }
.selector-row > .el-select { width: 300px; }
</style>
