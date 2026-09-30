<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SchedulingModeSwitch from '../components/SchedulingModeSwitch.vue'
import {
  cancelAiSchedulingRun,
  compareAiSchedulingCandidates,
  confirmAiSchedulingRun,
  createAiSchedulingRun,
  generateAiSchedulingRun,
  getAiSchedulingRun,
  listAiSchedulingCandidates,
  listAcademicTerms,
  listCourseOfferings,
  previewAiSchedulingCandidate,
  explainAiSchedulingCandidate,
  replyAiSchedulingRun,
} from '../../../api/admin'

const POLL_INTERVAL = 2500
const route = useRoute()
const router = useRouter()
const POLLING_STATUSES = ['QUEUED', 'RUNNING']
const TERMINAL_STATUSES = ['CANDIDATES_READY', 'FAILED', 'CANCELLED', 'EXPIRED']
const statusLabels = {
  DRAFT: '草稿',
  NEEDS_CLARIFICATION: '需要澄清',
  READY_FOR_CONFIRMATION: '待确认',
  CONFIRMED: '已确认',
  QUEUED: '排队中',
  RUNNING: '生成中',
  CANDIDATES_READY: '候选已就绪',
  FAILED: '失败',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}
const statusTypes = {
  DRAFT: 'info',
  NEEDS_CLARIFICATION: 'warning',
  READY_FOR_CONFIRMATION: 'warning',
  CONFIRMED: 'success',
  QUEUED: 'info',
  RUNNING: 'primary',
  CANDIDATES_READY: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
  EXPIRED: 'warning',
}

const form = reactive({
  semesterCode: '',
  requestText: '',
  mode: 'GLOBAL',
  selectedOfferingIds: [],
  candidateCount: 3,
})
const terms = ref([])
const offerings = ref([])
const run = ref(null)
const candidates = ref([])
const candidateSelection = ref([])
const previewCandidate = ref(null)
const previewDiff = ref(null)
const modelExplanation = ref(null)
let explanationRequestSequence = 0
const comparedCandidates = ref([])
const clarificationReply = ref('')
const creating = ref(false)
const loadingOfferings = ref(false)
const replying = ref(false)
const confirming = ref(false)
const generating = ref(false)
const cancelling = ref(false)
const loadingCandidates = ref(false)
const runRequestSequence = ref(0)
let pollTimer = null

const currentStatus = computed(() => run.value?.status || '')
const statusLabel = computed(() => statusLabels[currentStatus.value] || currentStatus.value || '未创建')
const statusType = computed(() => statusTypes[currentStatus.value] || 'info')
const parsedPlan = computed(() => run.value?.parsedPlan || run.value?.plan || run.value?.schedulePlan || null)
const clarificationItems = computed(() => {
  const value = parsedPlan.value?.clarifications
    || run.value?.clarifications
    || run.value?.clarificationMessages
    || run.value?.questions
  if (Array.isArray(value)) return value
  return value ? [value] : []
})
const unsupportedItems = computed(() => parsedPlan.value?.unsupported || [])
const unresolvedItems = computed(() => parsedPlan.value?.unresolvedClauses || [])
const constraintDraft = computed(() => parsedPlan.value?.constraints
  || run.value?.constraintDraft
  || run.value?.constraints
  || null)
const selectedCandidateRows = computed(() => candidateSelection.value)
const compareMetricRows = computed(() => {
  const selected = comparedCandidates.value
  const keys = new Set(selected.flatMap(candidate => Object.keys(candidate.metrics || candidate.indicators || {})))
  return [...keys].map(metric => ({
    metric,
    values: selected.map(candidate => (candidate.metrics || candidate.indicators || {})[metric] ?? '-'),
  }))
})
const canCancel = computed(() => Boolean(run.value?.id) && !TERMINAL_STATUSES.includes(currentStatus.value))
const canGenerate = computed(() => currentStatus.value === 'CONFIRMED')
const offeringLabel = offering => [
  offering.semesterCode,
  offering.courseName || offering.courseId,
  offering.teachingClassName || offering.name,
].filter(Boolean).join(' · ')

const unwrapData = response => response?.data ?? null
const unwrapList = response => {
  const data = unwrapData(response)
  if (Array.isArray(data)) return data
  return data?.content || data?.items || data?.candidates || []
}
const unwrapRun = response => {
  const data = unwrapData(response)
  return data?.run || data
}
const errorText = error => error?.message || '请求失败，请稍后重试'
const showError = error => ElMessage.error(errorText(error))
const formatJson = value => {
  if (value === null || value === undefined || value === '') return '暂无'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}
const metricEntries = candidate => Object.entries(candidate.metrics || candidate.indicators || {})
const candidateExplanation = candidate => {
  const metrics = candidate.metrics
  if (!metrics) return '暂无可验证的生成指标，请勿仅凭总分判断方案。'
  const lines = [
    `已排 ${metrics.scheduledLessons ?? 0} 节，未排 ${metrics.unscheduledLessons ?? 0} 节；偏好时段命中 ${metrics.preferredSlotHits ?? 0} 次。`,
  ]
  if (metrics.consecutiveBlockHits > 0) lines.push(`本轮连堂偏好达成 ${metrics.consecutiveBlockHits} 次。`)
  if (metrics.teacherDayConcentrationHits > 0) lines.push(`本轮教师同日集中偏好命中 ${metrics.teacherDayConcentrationHits} 次。`)
  if (metrics.unscheduledLessons > 0) lines.push('仍有未排课时，不能直接用于正式课表。')
  if (metrics.campusSwitchPenalty > 0) lines.push(`跨校区切换惩罚 ${metrics.campusSwitchPenalty}。`)
  if (metrics.teacherGapPenalty > 0) lines.push(`教师空档惩罚 ${metrics.teacherGapPenalty}。`)
  return lines.join(' ')
}

const stopPolling = () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const schedulePoll = (runId, sequence) => {
  stopPolling()
  pollTimer = setTimeout(() => {
    pollTimer = null
    void loadRun(runId, sequence)
  }, POLL_INTERVAL)
}

const loadCandidates = async (runId, sequence = runRequestSequence.value) => {
  loadingCandidates.value = true
  try {
    const response = await listAiSchedulingCandidates(runId)
    if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
    candidates.value = unwrapList(response)
  } catch (error) {
    if (sequence === runRequestSequence.value) showError(error)
  } finally {
    if (sequence === runRequestSequence.value) loadingCandidates.value = false
  }
}

const loadRun = async (runId, sequence = runRequestSequence.value) => {
  try {
    const response = await getAiSchedulingRun(runId)
    if (sequence !== runRequestSequence.value) return false
    const nextRun = unwrapRun(response)
    if (!nextRun?.id) throw new Error('服务端未返回有效的排课 Run')
    run.value = nextRun
    if (nextRun.status === 'CANDIDATES_READY') await loadCandidates(runId, sequence)
    if (sequence === runRequestSequence.value && POLLING_STATUSES.includes(nextRun.status)) {
      schedulePoll(runId, sequence)
    } else {
      stopPolling()
    }
    return true
  } catch (error) {
    if (sequence === runRequestSequence.value) {
      stopPolling()
      showError(error)
    }
    return false
  }
}

const loadOfferings = async () => {
  if (!form.semesterCode) {
    offerings.value = []
    return
  }
  loadingOfferings.value = true
  try {
    offerings.value = unwrapList(await listCourseOfferings(form.semesterCode, { page: 0, size: 200 }))
  } catch (error) {
    showError(error)
  } finally {
    loadingOfferings.value = false
  }
}

const loadInitial = async () => {
  try {
    terms.value = unwrapList(await listAcademicTerms({ page: 0, size: 100 }))
    const requestedTerm = route.query.semesterCode
    const current = terms.value.find(term => term.termCode === requestedTerm)
      || terms.value.find(term => term.currentTerm)
      || terms.value[0]
    if (current) {
      form.semesterCode = current.termCode
      await loadOfferings()
    }
  } catch (error) {
    showError(error)
  }
}

const changeSemester = async () => {
  form.selectedOfferingIds = []
  await loadOfferings()
}

const createRun = async () => {
  const requestText = form.requestText.trim()
  if (!form.semesterCode) return ElMessage.warning('请选择学期')
  if (!requestText) return ElMessage.warning('请输入自然语言排课需求')
  if (form.mode === 'LOCAL' && !form.selectedOfferingIds.length) return ElMessage.warning('LOCAL 模式至少选择一个教学任务')

  stopPolling()
  const sequence = runRequestSequence.value + 1
  runRequestSequence.value = sequence
  run.value = null
  candidates.value = []
  candidateSelection.value = []
  previewCandidate.value = null
  modelExplanation.value = null
  explanationRequestSequence += 1
  creating.value = true
  try {
    const payload = {
      clientRequestId: `ai-scheduling-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      semesterCode: form.semesterCode,
      requestText,
      mode: form.mode,
      candidateCount: form.candidateCount,
    }
    if (form.mode === 'LOCAL') payload.selectedOfferingIds = [...form.selectedOfferingIds]
    const response = await createAiSchedulingRun(payload)
    const createdRun = unwrapRun(response)
    const runId = createdRun?.id || createdRun?.runId
    if (!runId) throw new Error('创建排课 Run 失败：服务端未返回 Run ID')
    await router.replace({
      path: route.path,
      query: { ...route.query, semesterCode: form.semesterCode, aiRunId: runId },
    })
    if (!await loadRun(runId, sequence)) return
    ElMessage.success('排课 Run 已创建')
  } catch (error) {
    if (sequence === runRequestSequence.value) showError(error)
  } finally {
    if (sequence === runRequestSequence.value) creating.value = false
  }
}

const replyToRun = async () => {
  const message = clarificationReply.value.trim()
  if (!message || !run.value?.id) return ElMessage.warning('请输入澄清回复')
  const runId = run.value.id
  const sequence = runRequestSequence.value
  replying.value = true
  try {
    await replyAiSchedulingRun(runId, {
      answer: message,
      expectedPlanVersion: run.value.planVersion,
    })
    if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
    clarificationReply.value = ''
    if (!await loadRun(runId, sequence)) return
    ElMessage.success('澄清回复已提交')
  } catch (error) {
    showError(error)
  } finally {
    replying.value = false
  }
}

const confirmRun = async () => {
  if (!run.value?.id) return
  const runId = run.value.id
  const sequence = runRequestSequence.value
  confirming.value = true
  try {
    await confirmAiSchedulingRun(runId, {
      expectedPlanVersion: run.value.planVersion,
    })
    if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
    if (!await loadRun(runId, sequence)) return
    ElMessage.success('解析计划已确认')
  } catch (error) {
    showError(error)
  } finally {
    confirming.value = false
  }
}

const generateRun = async () => {
  if (!run.value?.id) return
  const runId = run.value.id
  const sequence = runRequestSequence.value
  generating.value = true
  try {
    await generateAiSchedulingRun(runId)
    if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
    if (!await loadRun(runId, sequence)) return
    ElMessage.success('候选方案生成请求已提交')
  } catch (error) {
    showError(error)
  } finally {
    generating.value = false
  }
}

const cancelRun = async () => {
  if (!run.value?.id) return
  const runId = run.value.id
  const sequence = runRequestSequence.value
  try {
    await ElMessageBox.confirm('确认取消当前排课 Run？', '取消确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showError(error)
    return
  }
  if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
  cancelling.value = true
  try {
    await cancelAiSchedulingRun(runId)
    if (sequence !== runRequestSequence.value || run.value?.id !== runId) return
    if (!await loadRun(runId, sequence)) return
    ElMessage.success('排课 Run 已取消')
  } catch (error) {
    showError(error)
  } finally {
    cancelling.value = false
  }
}

const statusMessage = status => run.value?.errorMessage || run.value?.message || ({
  FAILED: '服务端生成失败，请查看错误信息后重新创建 Run。',
  CANCELLED: '该 Run 已取消。',
  EXPIRED: '该 Run 已过期，请重新创建 Run。',
}[status] || '')
const showCandidatePreview = async candidate => {
  const runId = run.value?.id
  if (!runId) return
  previewCandidate.value = null
  previewDiff.value = null
  try {
    const response = await previewAiSchedulingCandidate(runId, candidate.id)
    if (run.value?.id !== runId) return
    previewCandidate.value = candidate
    previewDiff.value = unwrapData(response)
  } catch (error) {
    showError(error)
  }
  const showModelExplanation = async candidate => {
    const runId = run.value?.id
    if (!runId) return
    const sequence = ++explanationRequestSequence
    modelExplanation.value = null
    try {
      const response = await explainAiSchedulingCandidate(runId, candidate.id)
      if (run.value?.id !== runId || sequence !== explanationRequestSequence) return
      modelExplanation.value = unwrapData(response)
    } catch (error) {
      if (sequence === explanationRequestSequence) showError(error)
    }
  }
}
const selectCandidates = rows => {
  candidateSelection.value = rows
  comparedCandidates.value = []
}
const compareCandidates = async () => {
  const runId = run.value?.id
  if (!runId || selectedCandidateRows.value.length < 2) return
  try {
    const response = await compareAiSchedulingCandidates(
      runId, selectedCandidateRows.value.map(candidate => candidate.id),
    )
    if (run.value?.id !== runId) return
    comparedCandidates.value = unwrapList(response)
  } catch (error) {
    showError(error)
  }
}

onMounted(async () => {
  await loadInitial()
  const runId = route.query.aiRunId
  if (typeof runId === 'string' && runId) await loadRun(runId)
})
onBeforeUnmount(() => {
  runRequestSequence.value += 1
  explanationRequestSequence += 1
  stopPolling()
})
</script>

<template>
  <div class="ai-scheduling-page">
    <header class="page-header">
      <div>
        <h2>AI 智能排课工作台</h2>
        <p>用自然语言描述排课目标，逐步检查解析结果并查看候选方案。</p>
      </div>
      <div class="header-actions">
        <SchedulingModeSwitch :semester-code="form.semesterCode" :run-id="run?.id" />
        <el-tag :type="statusType">当前状态：{{ statusLabel }}</el-tag>
      </div>
    </header>

    <el-card shadow="never" class="request-card">
      <template #header><span class="card-title">创建排课 Run</span></template>
      <el-form :model="form" label-width="110px" @submit.prevent="createRun">
        <el-form-item label="学期" required>
          <el-select v-model="form.semesterCode" placeholder="选择学期" filterable @change="changeSemester">
            <el-option v-for="term in terms" :key="term.id || term.termCode" :label="term.termName" :value="term.termCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="排课模式" required>
          <el-radio-group v-model="form.mode">
            <el-radio-button value="GLOBAL">GLOBAL 全量</el-radio-button>
            <el-radio-button value="LOCAL">LOCAL 局部</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'LOCAL'" label="教学任务" required>
          <el-select
            v-model="form.selectedOfferingIds"
            multiple
            filterable
            :loading="loadingOfferings"
            placeholder="选择需要局部重排的教学任务"
            class="offering-select">
            <el-option v-for="offering in offerings" :key="offering.id" :label="offeringLabel(offering)" :value="offering.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="候选数量">
          <el-input-number v-model="form.candidateCount" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="自然语言需求" required>
          <el-input
            v-model="form.requestText"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="例如：张老师周三下午不能上课；PLC 实训尽量连堂；PLC 实训单周第1周到第17周。保留现有课表项可写“保留PLC 实训周三第3节”；有歧义的规则会要求澄清。"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createRun">创建 Run</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="run" shadow="never" class="run-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">Run {{ run.id }}</span>
          <div class="header-actions">
            <el-tag :type="statusType">{{ currentStatus }} · {{ statusLabel }}</el-tag>
            <el-button v-if="canCancel" type="danger" plain :loading="cancelling" @click="cancelRun">取消 Run</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="statusMessage(currentStatus)" :title="statusMessage(currentStatus)" :type="currentStatus === 'FAILED' ? 'error' : 'warning'" show-icon :closable="false" />
      <el-alert
        v-if="unsupportedItems.length"
        title="以下规则暂不支持，无法确认或执行；请修改需求后重试"
        type="warning"
        show-icon
        :closable="false"
        class="unsupported-alert"
      >
        <ul><li v-for="(item, index) in unsupportedItems" :key="index">{{ item }}</li></ul>
      </el-alert>

      <section v-if="['DRAFT', 'NEEDS_CLARIFICATION'].includes(currentStatus)" class="workflow-section">
        <h3>需求澄清</h3>
        <el-alert v-if="!clarificationItems.length" title="Run 正在准备解析结果。" type="info" :closable="false" />
        <ul v-else class="clarification-list">
          <li v-for="(item, index) in clarificationItems" :key="index">{{ typeof item === 'string' ? item : formatJson(item) }}</li>
        </ul>
        <div v-if="unresolvedItems.length && !unsupportedItems.length" class="reply-box">
          <div>
            <p>请用完整新规则替换下列待澄清原文；多条时逐行输入“编号：完整规则”，并写明课程或教师、教学周、节次等必要信息。未回复的规则会继续等待澄清。</p>
            <ol><li v-for="(item, index) in unresolvedItems" :key="index">{{ item }}</li></ol>
          </div>
          <el-input v-model="clarificationReply" type="textarea" :rows="3" placeholder="例如：1：张老师周三第3节不能上课" />
          <el-button type="primary" :loading="replying" @click="replyToRun">提交回复</el-button>
        </div>
        <el-alert v-else-if="unsupportedItems.length || currentStatus === 'NEEDS_CLARIFICATION'" title="请修改原需求、新建 Run 以解决不支持的规则或学期/模式冲突。" type="info" :closable="false" />
      </section>

      <section v-if="currentStatus === 'READY_FOR_CONFIRMATION'" class="workflow-section">
        <h3>解析计划与约束草稿</h3>
        <div class="json-grid">
          <div><h4>解析计划</h4><pre>{{ formatJson(parsedPlan) }}</pre></div>
          <div><h4>约束草稿</h4><pre>{{ formatJson(constraintDraft) }}</pre></div>
        </div>
        <el-button type="primary" :loading="confirming" @click="confirmRun">确认解析计划</el-button>
      </section>

      <section v-if="['CONFIRMED', 'QUEUED', 'RUNNING'].includes(currentStatus)" class="workflow-section">
        <h3>候选方案生成</h3>
        <p>本轮确认的动态规则仅作用于候选生成；临时锁课不会修改正式课表的锁定标志，软偏好不保证全部满足。</p>
        <el-alert
          v-if="currentStatus === 'QUEUED' || currentStatus === 'RUNNING'"
          title="候选方案正在生成，页面会自动刷新状态。"
          type="info"
          show-icon
          :closable="false"
        />
        <div class="generation-row">
          <el-button v-if="canGenerate" type="primary" :loading="generating" @click="generateRun">生成候选方案</el-button>
          <el-tag v-if="currentStatus === 'CONFIRMED'" type="success">等待生成指令</el-tag>
          <el-progress v-else :percentage="run.progress ?? 0" :status="currentStatus === 'RUNNING' ? undefined : 'success'" />
        </div>
      </section>

      <section v-if="currentStatus === 'CANDIDATES_READY'" class="workflow-section">
        <div class="section-heading">
          <div><h3>候选方案</h3><p>候选仅供比较和预览，当前 Run 不会直接改变正式课表。</p></div>
          <el-button :loading="loadingCandidates" @click="loadCandidates(run.id)">刷新候选</el-button>
        </div>
        <el-empty v-if="!candidates.length" description="当前 Run 暂无候选方案" />
        <el-table v-else :data="candidates" border @selection-change="selectCandidates">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="name" label="方案" min-width="180">
            <template #default="scope">{{ scope.row.name || scope.row.planName || scope.row.id }}</template>
          </el-table-column>
          <el-table-column prop="score" label="综合指标" width="120">
            <template #default="scope">{{ scope.row.score ?? scope.row.totalScore ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="指标明细" min-width="260">
            <template #default="scope">
              <span v-for="(entry, index) in metricEntries(scope.row)" :key="entry[0]" class="metric">
                {{ entry[0] }}: {{ entry[1] }}<i v-if="index < metricEntries(scope.row).length - 1"> · </i>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="方案依据" min-width="320">
            <template #default="scope">{{ candidateExplanation(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="只读操作" width="170" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="showCandidatePreview(scope.row)">预览</el-button>
              <el-button link type="primary" @click="showModelExplanation(scope.row)">模型解读</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="selectedCandidateRows.length >= 2" class="compare-panel">
          <h3>候选对比</h3>
          <el-button @click="compareCandidates">对比所选方案</el-button>
          <el-table v-if="comparedCandidates.length" :data="compareMetricRows" border size="small">
            <el-table-column prop="metric" label="指标" min-width="180" />
            <el-table-column v-for="(candidate, index) in comparedCandidates" :key="candidate.id" :label="candidate.name || candidate.planName || candidate.id">
              <template #default="scope">{{ scope.row.values[index] }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <el-alert v-if="currentStatus === 'CANDIDATES_READY' && previewCandidate" class="preview-alert" title="候选预览（只读）" type="info" :closable="false">
        <pre>{{ formatJson(previewDiff) }}</pre>
      </el-alert>
      <el-alert v-if="currentStatus === 'CANDIDATES_READY' && modelExplanation" class="preview-alert" title="模型筛选的真实指标（仅供参考）" type="info" :closable="false">
        <p v-for="fact in modelExplanation.facts" :key="fact.code">{{ fact.text }}</p>
      </el-alert>
    </el-card>

    <el-empty v-else description="填写需求后创建一个 AI 排课 Run" />
  </div>
</template>

<style scoped>
.ai-scheduling-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
.page-header, .card-header, .section-heading, .generation-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-header { margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
h3 { margin: 0 0 14px; }
h4 { margin: 0 0 8px; }
p { margin: 0; color: #84909a; }
.card-title { font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.request-card, .run-card { margin-bottom: 18px; }
.request-card :deep(.el-select), .offering-select { width: min(720px, 100%); }
.workflow-section { margin-top: 22px; }
.clarification-list { margin: 0 0 16px; padding: 12px 12px 12px 30px; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; background: var(--el-fill-color-light); }
.reply-box { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; max-width: 900px; }
.reply-box .el-textarea { width: 100%; }
.reply-box ol { margin: 8px 0 0; padding-left: 26px; }
.json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
pre { margin: 0; max-height: 320px; overflow: auto; padding: 12px; white-space: pre-wrap; word-break: break-word; border-radius: 4px; background: #f6f8fa; color: #334155; font: 13px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.generation-row { justify-content: flex-start; }
.generation-row .el-progress { flex: 1; max-width: 520px; }
.metric { white-space: nowrap; }
.metric i { font-style: normal; color: #b3bac2; }
.compare-panel { margin-top: 22px; }
.preview-alert { margin-top: 22px; }
.preview-alert pre { margin-top: 8px; background: transparent; padding: 0; }
@media (max-width: 800px) {
  .json-grid { grid-template-columns: 1fr; }
  .reply-box, .page-header, .card-header { align-items: stretch; flex-direction: column; }
}
</style>
