<template>
  <aside v-if="open" class="agent-workspace" aria-label="智能体视图">
    <header class="agent-workspace__header">
      <div>
        <small>INTERACTIVE AGENT</small>
        <strong>智能体视图</strong>
      </div>
      <el-button circle text aria-label="隐藏智能体视图" @click="$emit('close')"><el-icon><Close /></el-icon></el-button>
    </header>

    <el-tabs v-model="tab" class="agent-workspace__tabs">
      <el-tab-pane label="执行计划" name="plans">
        <div class="agent-toolbar">
          <span>{{ plans.length }} 个计划</span>
          <div v-if="canManage" class="agent-toolbar__actions">
            <el-button size="small" @click="draftDialog = true">AI 生成计划</el-button>
            <el-button type="primary" size="small" :icon="Plus" @click="editPlan()">新增计划</el-button>
          </div>
        </div>
        <div class="agent-scroll">
          <article v-for="item in plans" :key="item.plan.id" class="agent-plan-card">
            <div class="agent-plan-card__title">
              <strong>{{ item.plan.planName }}</strong>
              <el-tag size="small" :type="item.plan.enabled ? 'success' : 'info'">{{ item.plan.enabled ? '启用' : '停用' }}</el-tag>
            </div>
            <p>{{ item.plan.objective }}</p>
            <div class="agent-plan-meta">
              <span>{{ cycleLabel(item.plan) }}</span><span>V{{ item.plan.planVersion }}</span><span>{{ stageCount(item.steps) }} 阶段 / {{ item.steps.length }} 子步骤</span>
            </div>
            <div class="agent-plan-actions">
              <el-button v-if="canExecute" size="small" type="primary" @click="start(item)">执行</el-button>
              <el-button v-if="canManage" size="small" @click="editPlan(item)">编辑</el-button>
              <el-button v-if="canManage && item.plan.ownerUsername !== 'SYSTEM'" size="small" type="danger" text @click="removePlan(item)">删除</el-button>
            </div>
          </article>
          <el-empty v-if="!loading && !plans.length" description="尚未配置执行计划" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行动态" name="runs">
        <el-select v-model="selectedRunId" class="agent-run-select" placeholder="选择运行实例" @change="loadRun">
          <el-option v-for="item in runs" :key="item.run.id" :label="`${item.planName} · ${statusLabel(item.run.status)}`" :value="item.run.id" />
        </el-select>
        <template v-if="detail">
          <section class="agent-run-summary">
            <div class="agent-run-summary__top">
              <strong>{{ detail.overview.planName }}</strong>
              <el-tag :type="statusType(detail.overview.run.status)" size="small">{{ statusLabel(detail.overview.run.status) }}</el-tag>
            </div>
            <p>{{ detail.overview.run.currentStepName || detail.overview.run.summary || '等待开始' }}</p>
            <small>下一步：{{ detail.overview.run.nextAction || '等待计划调度' }}</small>
          </section>
		  <section v-if="pendingApprovalStep" class="agent-approval-card" aria-label="人工确认检查点">
			<div class="agent-approval-card__title">
			  <div><small>HUMAN APPROVAL</small><strong>人工确认检查点</strong></div>
			  <el-tag type="warning" size="small">等待异人审核</el-tag>
			</div>
			<dl>
			  <div><dt>待确认步骤</dt><dd>{{ pendingApprovalStep.stepOrder }}. {{ pendingApprovalStep.stepName }}</dd></div>
			  <div><dt>发起人</dt><dd>{{ detail.overview.run.ownerUsername }}</dd></div>
			  <div><dt>业务工具</dt><dd>{{ approvalTool?.name || pendingApprovalStep.toolCode }}</dd></div>
			  <div><dt>风险等级</dt><dd>{{ approvalTool?.riskLevel || '按工具策略控制' }}</dd></div>
			</dl>
			<p>{{ pendingApprovalStep.message || '该步骤将写入正式业务数据，执行前必须人工确认。' }}</p>
			<section v-if="approvalEvidence" class="agent-approval-evidence">
			  <strong>候选处理结果</strong>
			  <div class="agent-approval-evidence__summary">
				<span v-if="approvalEvidence.filename">文件：{{ approvalEvidence.filename }}</span>
				<span v-if="approvalEvidence.totalRows != null">名册行数：{{ approvalEvidence.totalRows }}</span>
				<span v-if="approvalEvidence.importedRows != null">校验通过：{{ approvalEvidence.importedRows }}</span>
				<span v-if="approvalEvidence.candidateCount != null">候选数：{{ approvalEvidence.candidateCount }}</span>
				<span v-if="approvalEvidence.assigned != null">拟处理：{{ approvalEvidence.assigned }}</span>
			  </div>
			  <el-alert v-if="approvalEvidence.errors?.length" type="error" :closable="false"
				:title="`存在 ${approvalEvidence.errors.length} 条校验错误，禁止审核通过`">
				<template #default><div v-for="item in approvalEvidence.errors" :key="item">{{ item }}</div></template>
			  </el-alert>
			  <el-table v-if="approvalEvidenceRows.length" :data="approvalEvidenceRows" size="small" border max-height="260">
				<el-table-column prop="candidateNo" label="考生号" min-width="130" />
				<el-table-column prop="studentName" label="姓名" min-width="90" />
				<el-table-column prop="admissionNo" label="录取号" min-width="120" />
				<el-table-column prop="admissionYear" label="年份" width="70" />
				<el-table-column prop="className" label="拟分班级" min-width="140" />
			  </el-table>
			</section>
			<el-alert v-if="isOwner" type="warning" :closable="false" show-icon>
			  <template #title>你是本次计划负责人，不能审核自己的步骤</template>
			  <div>请由具备“学期运营智能体审核”权限的其他账号处理。</div>
			  <div v-if="detail.eligibleReviewers?.length">可审核账号：{{ detail.eligibleReviewers.join('、') }}</div>
			  <div v-else>当前没有可用审核员，请管理员先为其他账号配置审核权限。</div>
			</el-alert>
			<template v-else-if="canApprove">
			  <el-input v-model="reviewComment" type="textarea" :rows="2" resize="none"
				placeholder="审核意见（确认时可选，驳回时必填）" />
			  <div class="agent-approval-card__actions">
				<el-button type="success" :loading="reviewing" :disabled="approvalHasErrors" @click="approve">确认并继续</el-button>
				<el-button type="danger" plain :loading="reviewing" @click="rejectApproval">驳回</el-button>
			  </div>
			</template>
			<el-alert v-else type="info" :closable="false" title="当前账号没有该步骤的审核权限" />
		  </section>
          <div class="agent-run-actions">
            <el-button v-if="canPause" size="small" @click="pause">暂停</el-button>
            <el-button v-if="canResume" size="small" type="primary" @click="resume">继续</el-button>
            <el-button v-if="canRetry" size="small" type="primary" @click="retry">重试失败步骤</el-button>
			<el-button v-if="hasAdmissionSteps" size="small" @click="openAdmissions">查看新生数据</el-button>
			<el-button v-if="hasEnrollmentStep && currentStatus === 'COMPLETED'" size="small" @click="openStudents">查看学生档案</el-button>
			<el-button v-if="canPromoteCandidate" size="small" type="success" @click="promoteCandidate">确认候选并继续正式处理</el-button>
			<el-button v-if="waitingInputStep" size="small" type="warning" @click="openStepInput">补充执行资料</el-button>
            <el-button v-if="canCondition" size="small" @click="conditionDialog = true">附加条件</el-button>
            <el-button v-if="canCancel" size="small" type="danger" text @click="cancel">取消</el-button>
          </div>
          <div class="agent-scroll agent-scroll--run">
            <section class="agent-section">
              <h3>执行步骤</h3>
              <template v-for="(step, index) in detail.steps" :key="step.id">
                <div v-if="showStageHeader(detail.steps, index)" class="agent-stage-header">
                  <span>阶段 {{ step.businessStageOrder }}</span><strong>{{ step.businessStageName }}</strong>
                </div>
                <div class="agent-step" :class="`is-${step.status.toLowerCase()}`">
                  <span class="agent-step__dot"></span>
                  <div class="agent-step__content">
                    <strong>{{ step.stepOrder }}. {{ step.stepName }}</strong>
                    <small>{{ statusLabel(step.status) }} · {{ step.message || '等待执行' }}</small>
					<details v-if="step.resultJson" class="agent-step-evidence">
					  <summary>查看执行证据</summary>
					  <pre>{{ prettyEvidence(step.resultJson) }}</pre>
					</details>
                  </div>
                  <em>{{ step.progress }}%</em>
                </div>
              </template>
            </section>
            <section class="agent-section">
              <h3>动态信息</h3>
              <div class="agent-events">
                <article v-for="event in events" :key="event.id" :class="`is-${event.level.toLowerCase()}`">
                  <time>{{ formatTime(event.createTime) }}</time>
                  <strong>{{ event.title }}</strong>
                  <p>{{ event.detail }}</p>
                </article>
                <el-empty v-if="!events.length" description="暂无执行动态" :image-size="54" />
              </div>
            </section>
          </div>
          <section v-if="canInteract" class="agent-interaction">
            <div>
              <strong>告诉智能体下一步怎么做</strong>
              <small>{{ interactionHint }}</small>
            </div>
            <el-input v-model="interactionText" type="textarea" :rows="2" resize="none"
              placeholder="例如：先暂停，只处理2026级，每批最多50人；不要写入正式数据" />
            <el-button type="primary" :loading="interactionSending" @click="submitInteraction">发送给智能体</el-button>
          </section>
        </template>
        <el-empty v-else description="选择或发起一个执行计划" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="planDialog" :title="editingId ? '编辑执行计划' : (draftAnalysis ? '确认 DeepSeek 生成的执行计划' : '新增执行计划')" width="820px" append-to-body destroy-on-close>
      <el-form label-position="top">
		<section v-if="draftAnalysis" class="agent-draft-analysis">
		  <strong>智能体识别结果</strong>
		  <el-alert v-if="draftAnalysis.modelGenerated === false" type="warning" :closable="false"
		    title="DeepSeek 本次调用未完成，平台已生成安全降级计划；该计划仍会实际调用受控业务工具" />
		  <p>{{ draftAnalysis.intentSummary || '已根据目标和当前业务数据生成计划' }}</p>
		  <small>{{ draftAnalysis.rationale }}</small>
		  <div v-if="Object.keys(draftAnalysis.readiness || {}).length" class="agent-readiness">
			<span v-for="(value, key) in draftAnalysis.readiness" :key="key">{{ readinessLabel(key) }}：{{ value ?? '未指定' }}</span>
		  </div>
		  <el-alert v-if="planMissingInputs.length" type="warning" :closable="false" title="保存前还需要补充资料">
			<template #default><div v-for="item in planMissingInputs" :key="`${item.toolCode}-${item.key}`">{{ item.stepName }}：{{ item.prompt }}</div></template>
		  </el-alert>
		</section>
        <div class="agent-form-grid">
          <el-form-item label="计划名称"><el-input v-model="form.planName" /></el-form-item>
          <el-form-item label="执行周期">
            <el-select v-model="form.cycleType"><el-option label="手动执行" value="MANUAL" /><el-option label="Cron 周期" value="CRON" /></el-select>
          </el-form-item>
        </div>
        <el-form-item label="计划状态">
          <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item v-if="form.cycleType === 'CRON'" label="Cron 表达式"><el-input v-model="form.cronExpression" placeholder="例如：0 0 8 * * MON-FRI" /></el-form-item>
        <div class="agent-form-grid">
          <el-form-item label="规划开始日期"><el-date-picker v-model="form.periodStart" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="规划结束日期"><el-date-picker v-model="form.periodEnd" value-format="YYYY-MM-DD" /></el-form-item>
        </div>
        <el-form-item label="计划目标"><el-input v-model="form.objective" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="执行规则"><el-input v-model="form.ruleText" type="textarea" :rows="3" placeholder="描述范围、前置条件、异常处理和完成标准" /></el-form-item>
        <el-form-item label="知识库 ID（可选）"><el-input v-model="form.knowledgeBaseId" placeholder="绑定后，知识检索步骤会保留政策引用" /></el-form-item>
        <el-form-item label="授权业务工具">
          <el-checkbox-group v-model="form.authorizedTools">
            <el-checkbox v-for="tool in tools" :key="tool.code" :value="tool.code">{{ tool.name }}（{{ tool.riskLevel }}）</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="agent-step-editor__header"><strong>执行步骤</strong><el-button size="small" @click="addStep">增加步骤</el-button></div>
        <div v-for="(step, index) in form.steps" :key="index" class="agent-step-editor">
          <span>{{ index + 1 }}</span>
          <el-input v-model="step.stepName" placeholder="步骤名称" />
          <el-select v-model="step.toolCode" placeholder="业务工具" @change="syncTool(step)">
            <el-option v-for="tool in tools" :key="tool.code" :label="tool.name" :value="tool.code" />
          </el-select>
		  <el-checkbox v-model="step.approvalRequired">执行前需审批</el-checkbox>
          <el-button type="danger" text @click="form.steps.splice(index, 1)">移除</el-button>
		  <small class="agent-step-editor__stage">{{ toolStageLabel(step.toolCode) }}</small>
          <el-input v-model="step.description" class="agent-step-editor__detail" type="textarea" :rows="2" placeholder="步骤说明" />
          <el-input v-model="step.successCriteria" class="agent-step-editor__detail" type="textarea" :rows="2" placeholder="完成标准" />
          <el-input v-model="step.toolConfigText" class="agent-step-editor__config" placeholder='工具参数 JSON，例如 {"semesterCode":"2026-2027-1"}' />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button :loading="saving" @click="savePlan(false)">{{ draftAnalysis && !editingId ? '仅保存计划' : '保存计划' }}</el-button>
        <el-button v-if="draftAnalysis && !editingId" type="primary" :loading="saving" @click="savePlan(true)">确认并开始执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="draftDialog" title="DeepSeek 生成执行计划" width="600px" append-to-body destroy-on-close
      :close-on-click-modal="!drafting" :close-on-press-escape="!drafting" :show-close="!drafting">
      <el-form label-position="top">
        <el-form-item label="希望智能体完成什么工作">
          <el-input v-model="draftForm.request" type="textarea" :rows="5" placeholder="例如：检查 2026-2027-1 学期开学准备度，引用校内政策，只做检查，不写入正式数据" />
        </el-form-item>
        <div class="agent-form-grid">
          <el-form-item label="学期（可选）"><el-input v-model="draftForm.semesterCode" placeholder="2026-2027-1" /></el-form-item>
          <el-form-item label="入学年份（可选）"><el-input-number v-model="draftForm.admissionYear" :min="2000" :max="2100" /></el-form-item>
        </div>
		<el-form-item label="知识库 ID（可选）"><el-input v-model="draftForm.knowledgeBaseId" /></el-form-item>
		<el-form-item label="结果边界">
		  <el-switch v-model="draftForm.candidateOnly" active-text="先生成候选结果，由用户确认后再进入正式写入" />
		</el-form-item>
        <el-form-item label="新生名册（可选）">
          <el-upload
            ref="draftUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.XLSX,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            :on-change="selectDraftAdmissionFile"
            :on-remove="clearDraftAdmissionFile"
          >
            <el-button>选择 XLSX 名册</el-button>
          </el-upload>
          <small class="agent-upload-tip">支持 .xlsx 和 .XLSX。文件会先进入受管文件中心，再把可信文件 ID 注入“新生数据接入”步骤。</small>
        </el-form-item>
        <section v-if="draftProgress.length" class="agent-planning-progress" aria-live="polite">
          <div class="agent-planning-progress__header">
            <strong>{{ drafting ? '正在生成执行计划' : (draftProgressFailed ? '执行计划生成失败' : '规划过程已完成') }}</strong>
            <span>{{ formatElapsed(draftElapsed) }}</span>
          </div>
          <p>{{ draftProgressMessage }}</p>
          <div class="agent-planning-progress__steps">
            <div v-for="item in draftProgress" :key="item.key" class="agent-planning-progress__step" :class="`is-${item.status}`">
              <i></i><div><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></div>
              <em>{{ planningStatusLabel(item.status) }}</em>
            </div>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button :disabled="drafting" @click="draftDialog = false">取消</el-button>
        <el-button type="primary" :loading="drafting" @click="generateDraft">{{ draftProgressFailed ? '重新调用 DeepSeek' : '调用 DeepSeek' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="conditionDialog" title="增加附加条件" width="520px" append-to-body>
      <p class="agent-condition-tip">附加条件只影响尚未开始的步骤。已完成且已生效的数据不会被静默覆盖。</p>
      <el-input v-model="conditionText" type="textarea" :rows="5" placeholder="例如：只处理2026级高职新生，每个批次最多50人" />
      <template #footer><el-button @click="conditionDialog = false">取消</el-button><el-button type="primary" @click="submitCondition">保存并重新评估</el-button></template>
    </el-dialog>

	<el-dialog v-model="stepInputDialog" title="补充步骤执行资料" width="560px" append-to-body>
	  <p class="agent-condition-tip">{{ waitingInputStep?.message }}。资料只会合并到这个尚未开始的步骤，并保留审计记录。</p>
	  <el-input v-model="stepInputText" type="textarea" :rows="8" placeholder='例如：{"assignments":[{"teacherNo":"202600001","subjectCode":"MATH","classCode":"2026-CS-01","weeklyLessons":4}]}' />
	  <template #footer><el-button @click="stepInputDialog = false">取消</el-button><el-button type="primary" @click="submitStepInput">保存资料</el-button></template>
	</el-dialog>
  </aside>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Close, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addOperationAgentCondition, approveOperationAgentStep, cancelOperationAgentRun,
  createOperationAgentPlan, deleteOperationAgentPlan, draftOperationAgentPlan, fetchOperationAgentEvents,
  fetchOperationAgentPlans, fetchOperationAgentRun, fetchOperationAgentRuns,
  fetchOperationAgentTools, pauseOperationAgentRun, resumeOperationAgentRun,
  promoteOperationAgentCandidate, rejectOperationAgentStep, retryOperationAgentRun, startOperationAgentRun, supplyOperationAgentInput, updateOperationAgentPlan,
  uploadOperationAgentAdmissionRoster,
} from '../api'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'

const props = defineProps({ open: Boolean })
const router = useRouter()
defineEmits(['close'])
const tab = ref('plans')
const loading = ref(false)
const saving = ref(false)
const tools = ref([])
const plans = ref([])
const runs = ref([])
const selectedRunId = ref('')
const detail = ref(null)
const events = ref([])
const lastSequence = ref(0)
const planDialog = ref(false)
const draftDialog = ref(false)
const drafting = ref(false)
const draftForm = ref({ request: '', semesterCode: '', admissionYear: new Date().getFullYear(), knowledgeBaseId: '', candidateOnly: true })
const draftAdmissionFile = ref(null)
const draftUploadRef = ref(null)
const draftAnalysis = ref(null)
const draftProgress = ref([])
const draftProgressMessage = ref('')
const draftElapsed = ref(0)
const draftProgressFailed = computed(() => draftProgress.value.some(item => item.status === 'failed'))
const editingId = ref('')
const conditionDialog = ref(false)
const conditionText = ref('')
const stepInputDialog = ref(false)
const stepInputText = ref('{}')
const interactionText = ref('')
const interactionSending = ref(false)
const reviewComment = ref('')
const reviewing = ref(false)
let poller
let draftTimer

const blankForm = () => ({
  planName: '', objective: '', ruleText: '', cycleType: 'MANUAL', cronExpression: '',
  periodStart: '', periodEnd: '', knowledgeBaseId: '', visibility: 'OWNER', enabled: true,
  authorizedTools: [], steps: [],
})
const form = ref(blankForm())
const username = getAdminUsername()
const canManage = hasAdminPermission('education:operation-agent:manage')
const canExecute = hasAdminPermission('education:operation-agent:execute', 'education:operation-agent:manage')
const canReview = hasAdminPermission('education:operation-agent:review')
const currentStatus = computed(() => detail.value?.overview?.run?.status)
const isOwner = computed(() => detail.value?.overview?.run?.ownerUsername === username)
const pendingApprovalStep = computed(() => detail.value?.steps?.find(step => step.status === 'WAITING_APPROVAL') || null)
const approvalTool = computed(() => tools.value.find(tool => tool.code === pendingApprovalStep.value?.toolCode))
const approvalEvidence = computed(() => parseEvidence(pendingApprovalStep.value?.resultJson))
const approvalEvidenceRows = computed(() => approvalEvidence.value?.rows || approvalEvidence.value?.assignments || approvalEvidence.value?.candidates || [])
const approvalHasErrors = computed(() => Boolean(approvalEvidence.value?.errors?.length))
const canPause = computed(() => isOwner.value && ['RUNNING', 'WAITING_APPROVAL'].includes(currentStatus.value))
const canResume = computed(() => isOwner.value && currentStatus.value === 'PAUSED'
  && !detail.value?.steps?.some(step => ['WAITING_APPROVAL', 'WAITING_INPUT'].includes(step.status)))
const canApprove = computed(() => canReview && !isOwner.value && ['WAITING_APPROVAL', 'PAUSED'].includes(currentStatus.value) && Boolean(pendingApprovalStep.value))
const canRetry = computed(() => isOwner.value && currentStatus.value === 'FAILED')
const canCondition = computed(() => isOwner.value && ['RUNNING', 'PAUSE_REQUESTED', 'PAUSED', 'WAITING_APPROVAL'].includes(currentStatus.value))
const canCancel = computed(() => isOwner.value && ['QUEUED', 'RUNNING', 'PAUSE_REQUESTED', 'PAUSED', 'WAITING_APPROVAL'].includes(currentStatus.value))
const canInteract = computed(() => isOwner.value && ['RUNNING', 'PAUSE_REQUESTED', 'PAUSED', 'WAITING_APPROVAL'].includes(currentStatus.value))
const canPromoteCandidate = computed(() => isOwner.value && Boolean(detail.value?.candidatePromotionAvailable))
const hasAdmissionSteps = computed(() => detail.value?.steps?.some(step => step.toolCode?.startsWith('admission.')))
const hasEnrollmentStep = computed(() => detail.value?.steps?.some(step => step.toolCode === 'admission.enroll.batch'))
const interactionHint = computed(() => ['RUNNING', 'PAUSE_REQUESTED'].includes(currentStatus.value)
  ? '消息会立即记录，并在当前步骤完成后的安全检查点暂停，再影响后续步骤。'
  : '附加条件只影响尚未开始的步骤，发送后可检查影响并点击继续。')
const waitingInputStep = computed(() => isOwner.value ? detail.value?.steps?.find(step => step.status === 'WAITING_INPUT') : null)
const planMissingInputs = computed(() => {
  const missing = []
  for (const step of form.value.steps || []) {
    let config = {}
    try { config = JSON.parse(step.toolConfigText || '{}') } catch { continue }
    const tool = tools.value.find(value => value.code === step.toolCode)
    for (const requirement of tool?.inputRequirements || []) {
      const value = config[requirement.key]
      if (value == null || value === '' || (Array.isArray(value) && !value.length)) {
        missing.push({ ...requirement, stepName: step.stepName, toolCode: step.toolCode })
      }
    }
  }
  return missing
})

watch(() => props.open, async value => {
  if (!value) return stopPolling()
  await loadBase()
  startPolling()
}, { immediate: true })

async function loadBase() {
  loading.value = true
  try {
    const [toolResult, planResult, runResult] = await Promise.all([
      fetchOperationAgentTools(), fetchOperationAgentPlans(), fetchOperationAgentRuns(),
    ])
    tools.value = toolResult.data || []
    plans.value = planResult.data || []
    runs.value = runResult.data || []
    if (!selectedRunId.value && runs.value.length) selectedRunId.value = runs.value[0].run.id
    if (selectedRunId.value) await loadRun()
  } finally { loading.value = false }
}
async function loadRun() {
  if (!selectedRunId.value) return
  const result = await fetchOperationAgentRun(selectedRunId.value)
  detail.value = result.data
  syncRunOverview(result.data?.overview)
  const eventResult = await fetchOperationAgentEvents(selectedRunId.value, 0)
  events.value = eventResult.data || []
  lastSequence.value = events.value.at(-1)?.eventSequence || 0
}
async function poll() {
  if (!props.open || !selectedRunId.value) return
  try {
    const [runResult, eventResult] = await Promise.all([
      fetchOperationAgentRun(selectedRunId.value),
      fetchOperationAgentEvents(selectedRunId.value, lastSequence.value),
    ])
    detail.value = runResult.data
    syncRunOverview(runResult.data?.overview)
    if (eventResult.data?.length) {
      events.value.push(...eventResult.data)
      lastSequence.value = eventResult.data.at(-1).eventSequence
    }
  } catch { /* 公共 HTTP 层已提示服务端错误 */ }
}
function syncRunOverview(overview) {
  if (!overview?.run?.id) return
  const index = runs.value.findIndex(item => item.run.id === overview.run.id)
  if (index >= 0) runs.value[index] = overview
}
function startPolling() { stopPolling(); poller = window.setInterval(poll, 1500) }
function stopPolling() { if (poller) window.clearInterval(poller); poller = null }
onBeforeUnmount(stopPolling)
onBeforeUnmount(stopDraftTimer)

function editPlan(item) {
  draftAnalysis.value = null
  editingId.value = item?.plan?.id || ''
  if (!item) form.value = blankForm()
  else form.value = {
    planName: item.plan.planName, objective: item.plan.objective, ruleText: item.plan.ruleText,
    cycleType: item.plan.cycleType, cronExpression: item.plan.cronExpression || '',
    periodStart: item.plan.periodStart || '', periodEnd: item.plan.periodEnd || '',
    knowledgeBaseId: item.plan.knowledgeBaseId || '', visibility: item.plan.visibility,
    enabled: item.plan.enabled, authorizedTools: [...item.authorizedTools],
    steps: item.steps.map(step => ({
      stepCode: step.stepCode, stepName: step.stepName, description: step.description || '',
      toolCode: step.toolCode, approvalRequired: step.approvalRequired,
      successCriteria: step.successCriteria || '', toolConfigText: step.toolConfigJson || '{}',
    })),
  }
  planDialog.value = true
}
function addStep() {
  form.value.steps.push({ stepCode: '', stepName: '', description: '', toolCode: '', approvalRequired: false, successCriteria: '', toolConfigText: '{}' })
}
function syncTool(step) {
  const tool = tools.value.find(value => value.code === step.toolCode)
  if (tool?.approvalRequired) step.approvalRequired = true
  if (step.toolCode && !form.value.authorizedTools.includes(step.toolCode)) form.value.authorizedTools.push(step.toolCode)
}
function toolStageLabel(toolCode) {
  const tool = tools.value.find(value => value.code === toolCode)
  return tool?.businessStageOrder ? `阶段 ${tool.businessStageOrder} · ${tool.businessStageName}` : '期末或通用步骤'
}
async function selectDraftAdmissionFile(file) {
  const filename = String(file?.name || file?.raw?.name || '')
  if (!filename.toLocaleLowerCase().endsWith('.xlsx')) {
    draftAdmissionFile.value = null
    await nextTick()
    draftUploadRef.value?.clearFiles()
    return ElMessage.warning('新生名册仅支持 XLSX 文件，文件扩展名大小写均可')
  }
  draftAdmissionFile.value = file.raw
}
function clearDraftAdmissionFile() {
  draftAdmissionFile.value = null
}
function parseEvidence(value) {
  if (!value) return null
  try { return typeof value === 'string' ? JSON.parse(value) : value } catch { return { message: String(value) } }
}
function prettyEvidence(value) {
  const parsed = parseEvidence(value)
  return parsed ? JSON.stringify(parsed, null, 2) : ''
}
function beginDraftProgress(hasRoster) {
  stopDraftTimer()
  draftElapsed.value = 0
  draftProgressMessage.value = '正在校验目标、学期和上传文件。'
  draftProgress.value = [
    { key: 'validate', name: '校验规划输入', status: 'running', detail: '检查目标、学期、年份和文件格式' },
    { key: 'upload', name: '接收新生名册', status: 'pending', detail: hasRoster ? '等待上传并绑定受管文件' : '本次未提供新生名册' },
    { key: 'reason', name: '读取业务现状并调用 DeepSeek', status: 'pending', detail: '分析当前数据，只选择必要步骤' },
    { key: 'verify', name: '校验执行计划', status: 'pending', detail: '校验工具白名单、依赖顺序、权限和缺失资料' },
    { key: 'review', name: '生成计划确认详情', status: 'pending', detail: '等待用户查看并确认；候选执行不会直接写入正式数据' },
  ]
  const startedAt = Date.now()
  draftTimer = window.setInterval(() => { draftElapsed.value = Math.floor((Date.now() - startedAt) / 1000) }, 1000)
}
function stopDraftTimer() {
  if (draftTimer) window.clearInterval(draftTimer)
  draftTimer = null
}
function updateDraftProgress(key, status, detail) {
  const item = draftProgress.value.find(value => value.key === key)
  if (!item) return
  item.status = status
  if (detail) item.detail = detail
}
function failDraftProgress(error) {
  const running = draftProgress.value.find(item => item.status === 'running')
  if (running) running.status = 'failed'
  draftProgressMessage.value = error?.message || '执行计划生成失败，请检查服务端返回信息后重试。'
}
function planningStatusLabel(status) {
  return ({ pending: '等待', running: '进行中', completed: '完成', skipped: '跳过', failed: '失败' })[status] || status
}
function formatElapsed(seconds) {
  if (seconds < 60) return `${seconds} 秒`
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}
async function generateDraft() {
  if (!draftForm.value.request.trim()) return ElMessage.warning('请描述希望智能体完成的工作')
  drafting.value = true
  beginDraftProgress(Boolean(draftAdmissionFile.value))
  try {
    updateDraftProgress('validate', 'completed', '输入格式校验通过')
    let managedFileId = ''
    if (draftAdmissionFile.value) {
      updateDraftProgress('upload', 'running', '正在上传名册并绑定当前用户')
      draftProgressMessage.value = '正在将新生名册安全上传到受管文件中心。'
      const uploaded = await uploadOperationAgentAdmissionRoster(draftAdmissionFile.value)
      managedFileId = uploaded.data?.id || uploaded.data?.fileId || ''
      if (!managedFileId) throw new Error('新生名册上传后未返回文件标识')
      updateDraftProgress('upload', 'completed', `名册已绑定，文件标识 ${managedFileId}`)
    } else updateDraftProgress('upload', 'skipped', '本次未提供新生名册')
    updateDraftProgress('reason', 'running', '正在读取准备度并等待 DeepSeek 返回规划结果')
    draftProgressMessage.value = 'DeepSeek 正在结合当前学校数据识别目标并生成最小必要步骤。'
    const result = await draftOperationAgentPlan({ ...draftForm.value, managedFileId: managedFileId || null })
    updateDraftProgress('reason', 'completed', 'DeepSeek 已返回结构化规划结果')
    updateDraftProgress('verify', 'running', '正在校验工具、顺序、参数和风险边界')
    draftProgressMessage.value = '模型已返回，平台正在执行确定性安全校验。'
    const draft = result.data?.plan
    if (!draft) throw new Error('DeepSeek 未返回执行计划')
    const draftSteps = [...(draft.steps || [])]
    editingId.value = ''
    form.value = {
      ...blankForm(), ...draft,
      authorizedTools: [...new Set([...(draft.authorizedTools || []), ...(managedFileId ? ['admission.import.managed-file'] : [])])],
      steps: draftSteps.map(step => ({
        ...step,
        toolConfigText: JSON.stringify(step.toolCode === 'admission.import.managed-file'
          ? { ...(step.toolConfig || {}), managedFileId, dryRun: draftForm.value.candidateOnly }
          : (step.toolConfig || {})),
      })),
    }
	draftAnalysis.value = {
	  intentSummary: result.data?.intentSummary,
	  rationale: result.data?.rationale,
	  readiness: result.data?.readiness || {},
	  requiredInputs: result.data?.requiredInputs || [],
	  modelGenerated: result.data?.modelGenerated !== false,
	}
	if (result.data?.modelGenerated === false) {
	  updateDraftProgress('reason', 'completed', 'DeepSeek 调用未完成，已切换平台受控业务规划器')
	}
	updateDraftProgress('verify', 'completed', `已校验 ${draftSteps.length} 个可执行步骤`)
    updateDraftProgress('review', 'completed', '计划确认详情已生成，可保存或直接开始候选执行')
    draftProgressMessage.value = '规划完成，即将打开计划确认详情。'
    draftDialog.value = false
    draftAdmissionFile.value = null
    draftUploadRef.value?.clearFiles()
    await nextTick()
    planDialog.value = true
	const warnings = result.data?.warnings || []
	if (warnings.length) ElMessage.warning(warnings.join('；'))
	else ElMessage.success('DeepSeek 已生成计划，请确认保存或直接开始执行')
  } catch (error) {
    failDraftProgress(error)
  } finally {
    drafting.value = false
    stopDraftTimer()
  }
}
async function savePlan(startAfterSave = false) {
  if (!form.value.planName.trim() || !form.value.objective.trim() || !form.value.ruleText.trim()) return ElMessage.warning('请完整填写计划名称、目标和规则')
  if (!form.value.steps.length) return ElMessage.warning('请至少配置一个执行步骤')
  let steps
  try {
    steps = form.value.steps.map((step, index) => ({
      ...step, stepCode: step.stepCode || `STEP_${index + 1}`, toolConfig: JSON.parse(step.toolConfigText || '{}'),
    }))
  } catch { return ElMessage.warning('步骤工具参数必须是合法 JSON') }
	if (planMissingInputs.value.length) return ElMessage.warning(`请先补充：${planMissingInputs.value.map(item => item.label).join('、')}`)
  const payload = { ...form.value, steps }
  saving.value = true
  try {
    const result = editingId.value
      ? await updateOperationAgentPlan(editingId.value, payload)
      : await createOperationAgentPlan(payload)
    const planId = result.data?.plan?.id
    if (startAfterSave) {
      if (!planId) throw new Error('执行计划已保存，但服务端未返回计划标识')
      const runResult = await startOperationAgentRun(planId)
      selectedRunId.value = runResult.data?.run?.id
      tab.value = 'runs'
      ElMessage.success('执行计划已保存，智能体已开始候选执行')
    } else ElMessage.success('执行计划已保存')
    planDialog.value = false
    draftAnalysis.value = null
    await loadBase()
  } finally { saving.value = false }
}
async function removePlan(item) {
  await ElMessageBox.confirm(`确认删除“${item.plan.planName}”？历史运行快照仍会保留。`, '删除执行计划', { type: 'warning' })
  await deleteOperationAgentPlan(item.plan.id)
  ElMessage.success('执行计划已删除')
  await loadBase()
}
async function start(item) {
  const result = await startOperationAgentRun(item.plan.id)
  selectedRunId.value = result.data.run.id
  tab.value = 'runs'
  await loadBase()
  ElMessage.success('智能体已开始执行')
}
async function pause() { await pauseOperationAgentRun(selectedRunId.value); await loadRun() }
async function resume() { await resumeOperationAgentRun(selectedRunId.value); await loadRun() }
async function retry() { await retryOperationAgentRun(selectedRunId.value); await loadRun(); ElMessage.success('已从失败步骤重新执行') }
async function approve() {
  await ElMessageBox.confirm('确认允许当前步骤调用正式业务工具？本次确认会记录操作者和时间。', '确认执行步骤', { type: 'warning' })
	reviewing.value = true
	try {
	  await approveOperationAgentStep(selectedRunId.value, reviewComment.value.trim())
	  reviewComment.value = ''
	  await loadRun()
	  ElMessage.success('审核已通过，智能体将从当前检查点继续执行')
	} finally { reviewing.value = false }
}
async function rejectApproval() {
	const comment = reviewComment.value.trim()
	if (!comment) return ElMessage.warning('驳回时请填写审核意见')
	await ElMessageBox.confirm('确认驳回当前步骤并退回计划负责人修正？', '驳回执行步骤', { type: 'warning' })
	reviewing.value = true
	try {
	  await rejectOperationAgentStep(selectedRunId.value, comment)
	  reviewComment.value = ''
	  await loadRun()
	  ElMessage.success('步骤已驳回，计划负责人可以修正后重试')
	} finally { reviewing.value = false }
}
async function cancel() {
  await ElMessageBox.confirm('确认取消本次执行？已完成的正式业务数据不会自动回滚。', '取消执行', { type: 'warning' })
  await cancelOperationAgentRun(selectedRunId.value)
  await loadRun()
}
function openAdmissions() { router.push('/admin/education/admissions') }
function openStudents() { router.push('/admin/education/students') }
async function submitCondition() {
  if (!conditionText.value.trim()) return ElMessage.warning('请输入附加条件')
  await addOperationAgentCondition(selectedRunId.value, conditionText.value)
  conditionDialog.value = false
  conditionText.value = ''
  await loadRun()
  ElMessage.success(['RUNNING', 'PAUSE_REQUESTED'].includes(currentStatus.value)
    ? '指令已记录，智能体将在安全检查点暂停并应用到后续步骤'
    : '附加条件已保存，检查影响后可以继续执行')
}
async function submitInteraction() {
  const text = interactionText.value.trim()
  if (!text) return ElMessage.warning('请输入希望智能体调整的条件或下一步要求')
  interactionSending.value = true
  try {
    await addOperationAgentCondition(selectedRunId.value, text)
    interactionText.value = ''
    await loadRun()
    ElMessage.success(['RUNNING', 'PAUSE_REQUESTED'].includes(currentStatus.value)
      ? '智能体已接收指令，将在安全检查点暂停'
      : '智能体已理解并记录附加条件')
  } finally { interactionSending.value = false }
}
async function promoteCandidate() {
  await ElMessageBox.confirm(
    '确认候选结果并发起正式处理？系统会创建可追溯的正式运行，写入步骤仍会停在权限和审批检查点。',
    '确认候选结果', { type: 'warning' })
  const result = await promoteOperationAgentCandidate(selectedRunId.value)
  selectedRunId.value = result.data?.run?.run?.id
  tab.value = 'runs'
  await loadBase()
  ElMessage.success('候选结果已确认，正式处理运行已发起')
}
function openStepInput() {
  stepInputText.value = waitingInputStep.value?.configOverridesJson || '{}'
  stepInputDialog.value = true
}
async function submitStepInput() {
  let values
  try { values = JSON.parse(stepInputText.value || '{}') } catch { return ElMessage.warning('补充资料必须是合法 JSON') }
  await supplyOperationAgentInput(selectedRunId.value, waitingInputStep.value.stepOrder, values)
  stepInputDialog.value = false
  await loadRun()
  ElMessage.success('执行资料已保存，请检查后点击继续')
}
const cycleLabel = plan => plan.cycleType === 'CRON' ? `周期：${plan.cronExpression || '未设置'}` : '手动执行'
const stageCount = steps => new Set((steps || []).map(step => step.businessStageCode).filter(Boolean)).size || (steps?.length ? 1 : 0)
const showStageHeader = (steps, index) => Boolean(steps[index]?.businessStageCode) && (index === 0 || steps[index - 1]?.businessStageCode !== steps[index].businessStageCode)
const statusLabel = status => ({
  QUEUED: '排队中', RUNNING: '执行中', PAUSE_REQUESTED: '暂停中', PAUSED: '已暂停',
  REPLANNING: '重新规划', WAITING_APPROVAL: '等待确认', COMPLETED: '已完成',
  SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', SKIPPED: '已跳过',
  WAITING_INPUT: '等待补充资料',
}[status] || status)
const readinessLabel = key => ({
  semesterCode: '学期', admissionYear: '入学年份', termExists: '学期已建立', activeClassCount: '启用行政班',
  admissionCandidateCount: '新生候选', unassignedAdmissionCount: '待分班新生', unenrolledAdmissionCount: '待建档新生',
  activeStudentCount: '在籍学生', activeTeacherCount: '在岗教师', teachingAssignmentCount: '教师分工',
  courseOfferingCount: '开课任务', scheduleCandidateCount: '排课候选', publishedScheduleVersionCount: '已发布课表',
}[key] || key)
const statusType = status => ({ COMPLETED: 'success', SUCCEEDED: 'success', FAILED: 'danger', WAITING_APPROVAL: 'warning', WAITING_INPUT: 'warning', PAUSED: 'warning', CANCELLED: 'info' }[status] || 'primary')
const formatTime = value => value ? String(value).replace('T', ' ').slice(5, 19) : ''
</script>

<style scoped>
.agent-workspace{position:fixed;z-index:100;inset:0 0 0 auto;width:clamp(320px,16.667vw,420px);display:flex;flex-direction:column;background:#f8fafb;border-left:1px solid #dce5e7;box-shadow:-16px 0 40px rgba(28,51,63,.12);color:#243b47}
.agent-workspace__header{height:72px;padding:0 18px 0 20px;display:flex;align-items:center;justify-content:space-between;background:#173c4b;color:#fff}
.agent-workspace__header>div{display:grid;gap:3px}.agent-workspace__header small{font-size:9px;letter-spacing:1.5px;opacity:.62}.agent-workspace__header strong{font-size:17px}.agent-workspace__header :deep(.el-button){color:#fff}
.agent-workspace__tabs{min-height:0;flex:1;display:flex;flex-direction:column}.agent-workspace__tabs :deep(.el-tabs__header){margin:0;padding:0 16px;background:#fff}.agent-workspace__tabs :deep(.el-tabs__content){min-height:0;flex:1}.agent-workspace__tabs :deep(.el-tab-pane){height:100%;display:flex;flex-direction:column}
.agent-toolbar{padding:12px 14px;display:flex;align-items:center;justify-content:space-between;font-size:12px;color:#758690}
.agent-toolbar__actions{display:flex;gap:6px}.agent-toolbar__actions :deep(.el-button+.el-button){margin-left:0}
.agent-scroll{min-height:0;overflow:auto;padding:0 12px 18px;display:grid;align-content:start;gap:10px}.agent-scroll--run{padding-top:12px}
.agent-plan-card{padding:13px;border:1px solid #e0e8eb;border-radius:14px;background:#fff;box-shadow:0 6px 18px rgba(40,61,71,.04)}.agent-plan-card__title{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.agent-plan-card p{margin:8px 0;color:#6d7c84;font-size:12px;line-height:1.55}.agent-plan-meta{display:flex;gap:10px;color:#94a0a6;font-size:10px}.agent-plan-actions{display:flex;margin-top:11px}
.agent-run-select{width:calc(100% - 24px);margin:12px}.agent-run-summary{margin:0 12px;padding:13px;border-radius:14px;background:linear-gradient(135deg,#e7f5f1,#f6fbfa);border:1px solid #d2e9e3}.agent-run-summary__top{display:flex;justify-content:space-between;gap:8px}.agent-run-summary p{margin:8px 0 5px;font-size:12px}.agent-run-summary small{color:#687a81;font-size:10px;line-height:1.5}.agent-run-actions{padding:10px 12px;display:flex;flex-wrap:wrap;gap:5px}.agent-run-actions :deep(.el-button+.el-button){margin-left:0}
.agent-approval-card{margin:10px 12px 0;padding:13px;display:grid;gap:10px;border:1px solid #e8bd72;border-radius:14px;background:#fffaf0;box-shadow:0 8px 20px rgba(121,82,25,.07)}.agent-approval-card__title{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.agent-approval-card__title>div{display:grid;gap:2px}.agent-approval-card__title small{color:#a36c20;font-size:8px;letter-spacing:1.2px}.agent-approval-card__title strong{font-size:13px}.agent-approval-card dl{display:grid;gap:6px;margin:0}.agent-approval-card dl>div{display:grid;grid-template-columns:70px 1fr;gap:8px;font-size:10px}.agent-approval-card dt{color:#8b795f}.agent-approval-card dd{margin:0;color:#384b53;overflow-wrap:anywhere}.agent-approval-card>p{margin:0;color:#75644c;font-size:10px;line-height:1.55}.agent-approval-card :deep(.el-alert__content){min-width:0}.agent-approval-card :deep(.el-alert__description){font-size:10px;line-height:1.55}.agent-approval-card__actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.agent-approval-card__actions :deep(.el-button){width:100%;margin:0}
.agent-approval-evidence{display:grid;gap:8px;padding:10px;border:1px solid #eed9b3;border-radius:10px;background:#fff}.agent-approval-evidence>strong{font-size:11px}.agent-approval-evidence__summary{display:flex;flex-wrap:wrap;gap:5px}.agent-approval-evidence__summary span{padding:3px 6px;border-radius:6px;background:#f7f1e6;color:#685b47;font-size:9px}.agent-approval-evidence :deep(.el-table){font-size:10px}
.agent-section h3{margin:0 0 9px;font-size:12px}.agent-step{position:relative;display:grid;grid-template-columns:12px 1fr auto;gap:8px;padding:9px 8px;border-left:1px solid #dce5e7}.agent-step__dot{width:9px;height:9px;margin-top:4px;margin-left:-13px;border:2px solid #aab6bb;border-radius:50%;background:#fff}.agent-step div{display:grid;gap:4px}.agent-step strong{font-size:11px}.agent-step small{color:#7c8a91;font-size:9px;line-height:1.4}.agent-step em{color:#8a989e;font-size:9px;font-style:normal}.agent-step.is-succeeded .agent-step__dot{border-color:#0f8b72;background:#0f8b72}.agent-step.is-running .agent-step__dot,.agent-step.is-waiting_approval .agent-step__dot{border-color:#d99028;background:#fff4df}
.agent-step__content{min-width:0}.agent-step-evidence{margin-top:3px}.agent-step-evidence summary{cursor:pointer;color:#35766c;font-size:9px}.agent-step-evidence pre{max-height:220px;margin:5px 0 0;padding:7px;overflow:auto;border-radius:7px;background:#f2f6f7;color:#52646b;font:9px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.agent-stage-header{display:flex;align-items:center;gap:7px;margin:10px 0 2px;padding:7px 9px;border-radius:9px;background:#e8f2f1;color:#315b62}.agent-stage-header span{font-size:9px;letter-spacing:.5px}.agent-stage-header strong{font-size:11px}.agent-upload-tip{display:block;margin-left:8px;color:#7c8a91;font-size:11px;line-height:1.5}
.agent-events{display:grid;gap:8px}.agent-events article{padding:9px 10px;border-left:3px solid #9eb0b8;border-radius:0 10px 10px 0;background:#fff}.agent-events article.is-success{border-color:#0f8b72}.agent-events article.is-warn{border-color:#d99028}.agent-events article.is-error{border-color:#cf5353}.agent-events time{display:block;color:#9aa6ab;font-size:9px}.agent-events strong{display:block;margin-top:3px;font-size:11px}.agent-events p{margin:4px 0 0;color:#6f7f86;font-size:10px;line-height:1.45}
.agent-interaction{margin:0 12px 12px;padding:12px;display:grid;gap:8px;border:1px solid #cfe0e5;border-radius:14px;background:#fff;box-shadow:0 8px 20px rgba(34,66,78,.06)}.agent-interaction>div{display:grid;gap:3px}.agent-interaction strong{font-size:12px}.agent-interaction small{color:#71838b;font-size:10px;line-height:1.45}.agent-interaction :deep(.el-button){width:100%}
.agent-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.agent-step-editor__header{display:flex;justify-content:space-between;margin:12px 0}.agent-step-editor{display:grid;grid-template-columns:24px 1fr 1fr auto auto;gap:8px;align-items:center;margin-bottom:8px;padding:10px;border:1px solid #e1e8eb;border-radius:10px}.agent-step-editor__stage{grid-column:2/-1;color:#55717a}.agent-step-editor__detail,.agent-step-editor__config{grid-column:2/-1}.agent-condition-tip{color:#697a82;font-size:12px;line-height:1.6}
.agent-draft-analysis{display:grid;gap:8px;margin-bottom:16px;padding:14px;border:1px solid #cfe4df;border-radius:12px;background:#f4faf8}.agent-draft-analysis p,.agent-draft-analysis small{margin:0}.agent-draft-analysis small{color:#667b82;line-height:1.5}.agent-readiness{display:flex;flex-wrap:wrap;gap:6px}.agent-readiness span{padding:4px 7px;border-radius:7px;background:#fff;color:#536970;font-size:11px}
.agent-planning-progress{display:grid;gap:10px;margin-top:6px;padding:14px;border:1px solid #cfe0e5;border-radius:12px;background:#f7fafb}.agent-planning-progress__header{display:flex;justify-content:space-between;gap:12px}.agent-planning-progress__header span{color:#71838b;font-size:11px}.agent-planning-progress>p{margin:0;color:#60747d;font-size:12px;line-height:1.5}.agent-planning-progress__steps{display:grid;gap:7px}.agent-planning-progress__step{display:grid;grid-template-columns:12px 1fr auto;align-items:start;gap:8px}.agent-planning-progress__step i{width:10px;height:10px;margin-top:3px;border:2px solid #aebdc3;border-radius:50%;background:#fff}.agent-planning-progress__step div{display:grid;gap:2px}.agent-planning-progress__step strong{font-size:11px}.agent-planning-progress__step small,.agent-planning-progress__step em{color:#7b8c93;font-size:10px;font-style:normal}.agent-planning-progress__step.is-running i{border-color:#2f7f91;box-shadow:0 0 0 4px rgba(47,127,145,.12)}.agent-planning-progress__step.is-completed i{border-color:#14836d;background:#14836d}.agent-planning-progress__step.is-skipped i{border-color:#aebdc3;background:#e8edef}.agent-planning-progress__step.is-failed i{border-color:#c84d4d;background:#c84d4d}.agent-planning-progress__step.is-failed em{color:#c84d4d}
@media(max-width:720px){.agent-workspace{width:100%}.agent-form-grid{grid-template-columns:1fr}.agent-step-editor{grid-template-columns:24px 1fr}.agent-step-editor__config{grid-column:1/-1}}
</style>
