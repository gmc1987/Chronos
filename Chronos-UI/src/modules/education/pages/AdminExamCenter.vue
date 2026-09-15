<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createExamPlan,
  createExamSession,
  deleteExamPlan,
  deleteExamSession,
  dictionaryOptions,
  listAcademicTerms,
  listClassrooms,
  listEducationSubjects,
  listExamPlans,
  listExamSessions,
  listExamRooms,
  listExamPublishedChanges,
  requestExamPublishedChange,
  decideExamPublishedChange,
  previewExamConflicts,
  previewExamSuspensionImpacts,
  listExamSuspensionRequests,
  requestExamSuspension,
  decideExamSuspension,
  publishExamPlan,
  updateExamPlan,
  updateExamSession,
} from '../../../api/admin'

const semesterCode = ref('')
const terms = ref([])
const examTypes = ref([])
const subjects = ref([])
const classrooms = ref([])
const plans = ref([])
const sessions = ref([])
const conflicts = ref([])
const suspensionScope = ref('AFFECTED_ONLY')
const suspensionReason = ref('')
const suspensionImpacts = ref([])
const suspensionRequests = ref([])
const publishedChanges = ref([])
const changeRooms = ref([])
const selectedPlan = ref(null)
const busy = ref(false)

const dialogs = reactive({
  plan: false,
  session: false,
  publishedChange: false,
})
const planForm = reactive({})
const sessionForm = reactive({})
const publishedChangeForm = reactive({
  changeType: 'RESCHEDULE',
  sessionId: '',
  roomId: '',
  newExamDate: '',
  newStartTime: '',
  newEndTime: '',
  newClassroomId: '',
  reason: '',
})

const editable = computed(() => selectedPlan.value?.status === 'DRAFT')
const subjectName = (id) => subjects.value.find((item) => item.id === id)?.subjectName || id
const classroomName = (id) => classrooms.value.find((item) => item.id === id)?.roomName || id
const examTypeName = (value) => examTypes.value.find(
  (item) => item.dictValue === value,
)?.dictName || value

function unwrap(response) {
  return response?.data?.content || response?.data || []
}

function showError(error) {
  ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
}

async function run(action, message) {
  busy.value = true
  try {
    await action()
    if (message) ElMessage.success(message)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function loadLookups() {
  const [termResult, subjectResult, classroomResult, examTypeResult] = await Promise.all([
    listAcademicTerms(),
    listEducationSubjects(),
    listClassrooms(),
    dictionaryOptions('EDU_EXAM_TYPE'),
  ])
  terms.value = unwrap(termResult)
  subjects.value = unwrap(subjectResult)
  classrooms.value = unwrap(classroomResult)
  examTypes.value = unwrap(examTypeResult)
  semesterCode.value ||= terms.value.find((item) => item.currentTerm)?.termCode || terms.value[0]?.termCode || ''
}

async function loadPlans() {
  plans.value = semesterCode.value ? unwrap(await listExamPlans(semesterCode.value)) : []
  const current = plans.value.find((item) => item.id === selectedPlan.value?.id)
  await selectPlan(current || null)
}

async function selectPlan(plan) {
  selectedPlan.value = plan
  sessions.value = plan ? unwrap(await listExamSessions(plan.id)) : []
  conflicts.value = []
  suspensionImpacts.value = []
  suspensionRequests.value = plan
    ? unwrap(await listExamSuspensionRequests(plan.id)) : []
  publishedChanges.value = plan
    ? unwrap(await listExamPublishedChanges(plan.id)) : []
  suspensionScope.value = ['TERM', 'MIDTERM', 'ACADEMIC'].includes(plan?.examType)
    ? 'SCHOOL_WIDE' : 'AFFECTED_ONLY'
}

async function openPublishedChange() {
  Object.assign(publishedChangeForm, {
    changeType: 'RESCHEDULE',
    sessionId: '',
    roomId: '',
    newExamDate: '',
    newStartTime: '',
    newEndTime: '',
    newClassroomId: '',
    reason: '',
  })
  changeRooms.value = []
  dialogs.publishedChange = true
}

async function loadChangeRooms() {
  publishedChangeForm.roomId = ''
  changeRooms.value = publishedChangeForm.sessionId
    ? unwrap(await listExamRooms(publishedChangeForm.sessionId)) : []
}

async function submitPublishedChange() {
  if (!publishedChangeForm.reason.trim()) return ElMessage.warning('请填写变更原因')
  if (publishedChangeForm.changeType !== 'CANCEL_PLAN' && !publishedChangeForm.sessionId) {
    return ElMessage.warning('请选择考试场次')
  }
  if (publishedChangeForm.changeType === 'ROOM_CHANGE'
    && (!publishedChangeForm.roomId || !publishedChangeForm.newClassroomId)) {
    return ElMessage.warning('请选择原考场和新教室')
  }
  await run(async () => {
    await requestExamPublishedChange(selectedPlan.value.id, publishedChangeForm)
    dialogs.publishedChange = false
    publishedChanges.value = unwrap(await listExamPublishedChanges(selectedPlan.value.id))
  }, '考试变更申请已提交')
}

async function decidePublishedChange(change, approve) {
  try {
    await ElMessageBox.confirm(
      approve ? '批准后将立即切换考试版本并通知相关人员，确定继续？' : '确认驳回申请？',
      '已发布考试变更审批',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await run(async () => {
    await decideExamPublishedChange(change.id, approve)
    await loadPlans()
  }, approve ? '考试变更已批准并生效' : '考试变更已驳回')
}

function openPlan(plan) {
  Object.assign(planForm, {
    id: plan?.id || '',
    semesterCode: semesterCode.value,
    planName: plan?.planName || '',
    examType: plan?.examType || 'TERM',
    startDate: plan?.startDate || '',
    endDate: plan?.endDate || '',
    baseInvigilators: plan?.baseInvigilators ?? 2,
    extraStaffThreshold: plan?.extraStaffThreshold ?? 60,
    allowOwnClassInvigilation: plan?.allowOwnClassInvigilation ?? false,
    maxConsecutiveDuties: plan?.maxConsecutiveDuties ?? 2,
    campusTravelMinutes: plan?.campusTravelMinutes ?? 60,
    requireSubjectQualification: plan?.requireSubjectQualification ?? false,
    ruleJson: plan?.ruleJson || '',
  })
  dialogs.plan = true
}

async function savePlan() {
  await run(async () => {
    if (!planForm.planName || !planForm.startDate || !planForm.endDate) {
      throw new Error('请填写计划名称和日期范围')
    }
    if (planForm.id) await updateExamPlan(planForm.id, planForm)
    else await createExamPlan(planForm)
    dialogs.plan = false
    await loadPlans()
  }, '考试计划已保存')
}

function openSession(session) {
  Object.assign(sessionForm, {
    id: session?.id || '',
    subjectId: session?.subjectId || '',
    examDate: session?.examDate || '',
    startTime: session?.startTime || '',
    endTime: session?.endTime || '',
  })
  dialogs.session = true
}

async function saveSession() {
  await run(async () => {
    if (!selectedPlan.value || !sessionForm.subjectId || !sessionForm.examDate
      || !sessionForm.startTime || !sessionForm.endTime) {
      throw new Error('请填写科目、日期和起止时间')
    }
    if (sessionForm.id) {
      await updateExamSession(selectedPlan.value.id, sessionForm.id, sessionForm)
    } else {
      await createExamSession(selectedPlan.value.id, sessionForm)
    }
    dialogs.session = false
    await selectPlan(selectedPlan.value)
  }, '考试场次已新增')
}

async function confirmDelete(message, action, successMessage) {
  try {
    await ElMessageBox.confirm(message, '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await run(action, successMessage)
}

function removePlan(plan) {
  confirmDelete('删除草稿计划及其全部场次、考场和监考数据？', async () => {
    await deleteExamPlan(plan.id)
    selectedPlan.value = null
    await loadPlans()
  }, '考试计划已删除')
}

function removeSession(session) {
  confirmDelete('删除此场次及其全部考场安排？', async () => {
    await deleteExamSession(selectedPlan.value.id, session.id)
    await selectPlan(selectedPlan.value)
  }, '考试场次已删除')
}

async function checkConflicts() {
  await run(async () => {
    conflicts.value = unwrap(await previewExamConflicts(selectedPlan.value.id))
    if (!conflicts.value.length) ElMessage.success('未发现课程、考场或考生冲突')
  })
}

async function previewSuspension() {
  await run(async () => {
    suspensionImpacts.value = unwrap(await previewExamSuspensionImpacts(
      selectedPlan.value.id,
      suspensionScope.value,
    ))
    if (!suspensionImpacts.value.length) ElMessage.success('当前范围无须批量停课')
  })
}

async function submitSuspension() {
  if (!suspensionReason.value.trim()) return ElMessage.warning('请填写占课原因')
  await run(async () => {
    await requestExamSuspension(selectedPlan.value.id, {
      scopeMode: suspensionScope.value,
      reason: suspensionReason.value.trim(),
    })
    suspensionRequests.value = unwrap(await listExamSuspensionRequests(
      selectedPlan.value.id,
    ))
  }, '占课审批申请已提交')
}

async function decideSuspension(request, approve) {
  try {
    await ElMessageBox.confirm(
      approve ? '批准后将自动生成本批课程的日期停课记录，确定继续？' : '确认驳回申请？',
      '考试占课审批',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await run(async () => {
    await decideExamSuspension(request.id, approve)
    suspensionRequests.value = unwrap(await listExamSuspensionRequests(
      selectedPlan.value.id,
    ))
    await checkConflicts()
  }, approve ? '占课已批准并写入日期课表' : '申请已驳回')
}

async function publish() {
  try {
    await ElMessageBox.confirm('发布前请确认考场、考生和监考均已安排。发布后不能直接编辑草稿，确定发布？', '发布考试计划')
  } catch {
    return
  }
  await run(async () => {
    await publishExamPlan(selectedPlan.value.id)
    await loadPlans()
  }, '考试计划已发布')
}

onMounted(() => {
  run(async () => {
    await loadLookups()
    await loadPlans()
  })
})
</script>

<template>
  <div class="exam-page">
    <header class="page-header">
      <div>
        <h2>考试计划管理</h2>
        <p>先确定场次与考生，再配置考场和监考；发布前检查与日期课表的冲突。</p>
      </div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadPlans">
        <el-option
          v-for="term in terms"
          :key="term.id"
          :label="term.termName"
          :value="term.termCode" />
      </el-select>
    </header>

    <div>
        <div class="section-heading">
          <h3>学期考试计划</h3>
          <el-button
            v-permission="['education:exam:plan:create', 'education:exam:plan:manage']"
            type="primary"
            @click="openPlan()">新增计划</el-button>
        </div>
        <el-table :data="plans" border highlight-current-row @current-change="selectPlan">
          <el-table-column prop="planName" label="计划名称" min-width="180" />
          <el-table-column label="考试类型" width="120">
            <template #default="scope">{{ examTypeName(scope.row.examType) }}</template>
          </el-table-column>
          <el-table-column prop="startDate" label="开始日期" width="120" />
          <el-table-column prop="endDate" label="结束日期" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="操作" width="100">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === 'DRAFT'"
                v-permission="['education:exam:plan:update', 'education:exam:plan:manage']"
                link
                @click.stop="openPlan(scope.row)">编辑</el-button>
              <el-button
                v-if="scope.row.status === 'DRAFT'"
                v-permission="['education:exam:plan:delete', 'education:exam:plan:manage']"
                link
                type="danger"
                @click.stop="removePlan(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="selectedPlan">
          <div class="section-heading">
            <h3>{{ selectedPlan.planName }} · 考试场次</h3>
            <div class="actions">
              <el-button @click="checkConflicts">检查冲突</el-button>
              <el-button
                v-if="editable"
                v-permission="['education:exam:plan:create', 'education:exam:plan:manage']"
                @click="openSession()">新增场次</el-button>
              <el-button
                v-if="editable"
                v-permission="['education:exam:plan:manage']"
                type="success"
                :loading="busy"
                @click="publish">发布计划</el-button>
              <el-button
                v-if="selectedPlan.status === 'PUBLISHED'"
                v-permission="['education:exam:plan:manage']"
                type="warning"
                @click="openPublishedChange">申请变更</el-button>
            </div>
          </div>
          <el-alert
            v-if="conflicts.length"
            type="warning"
            :closable="false"
            :title="`发现 ${conflicts.length} 项冲突，请先处理`">
            <div v-for="conflict in conflicts" :key="conflict">{{ conflict }}</div>
          </el-alert>
          <div class="section-heading">
            <h3>考试占课审批</h3>
            <div class="actions">
              <el-select v-model="suspensionScope" :disabled="!editable">
                <el-option label="校级范围：停同期课程" value="SCHOOL_WIDE" />
                <el-option label="仅考试影响的课程" value="AFFECTED_ONLY" />
              </el-select>
              <el-button v-if="editable" @click="previewSuspension">预览影响课程</el-button>
            </div>
          </div>
          <el-table :data="suspensionImpacts" border empty-text="预览后显示受影响课程">
            <el-table-column prop="date" label="上课日期" width="130" />
            <el-table-column label="课程" min-width="160">
              <template #default="scope">{{ scope.row.entry?.courseName }}</template>
            </el-table-column>
            <el-table-column label="教学班" min-width="160">
              <template #default="scope">{{ scope.row.entry?.teachingClassName }}</template>
            </el-table-column>
            <el-table-column label="节次" width="90">
              <template #default="scope">{{ scope.row.effectivePeriodNo }}</template>
            </el-table-column>
          </el-table>
          <div v-if="editable && suspensionImpacts.length" class="suspension-request-row">
            <el-input
              v-model="suspensionReason"
              maxlength="1000"
              show-word-limit
              placeholder="填写考试占课原因" />
            <el-button
              v-permission="['education:exam:plan:manage']"
              type="primary"
              @click="submitSuspension">提交占课审批</el-button>
          </div>
          <el-table :data="suspensionRequests" border empty-text="暂无占课审批记录">
            <el-table-column prop="createTime" label="申请时间" min-width="170" />
            <el-table-column prop="requestedBy" label="申请人" width="130" />
            <el-table-column prop="scopeMode" label="范围" width="140" />
            <el-table-column prop="reason" label="原因" min-width="180" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="审批" width="150">
              <template #default="scope">
                <template v-if="scope.row.status === 'PENDING'">
                  <el-button
                    v-permission="['education:exam:suspension:approve']"
                    link type="success"
                    @click="decideSuspension(scope.row, true)">批准</el-button>
                  <el-button
                    v-permission="['education:exam:suspension:approve']"
                    link type="danger"
                    @click="decideSuspension(scope.row, false)">驳回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-table :data="sessions" border>
            <el-table-column label="科目" min-width="150">
              <template #default="scope">{{ subjectName(scope.row.subjectId) }}</template>
            </el-table-column>
            <el-table-column prop="examDate" label="日期" width="130" />
            <el-table-column prop="startTime" label="开始" width="110" />
            <el-table-column prop="endTime" label="结束" width="110" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column v-if="editable" label="操作" width="130">
              <template #default="scope">
                <el-button
                  v-permission="['education:exam:plan:update', 'education:exam:plan:manage']"
                  link
                  @click.stop="openSession(scope.row)">编辑</el-button>
                <el-button
                  v-permission="['education:exam:plan:delete', 'education:exam:plan:manage']"
                  link
                  type="danger"
                  @click.stop="removeSession(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <template v-if="selectedPlan.status !== 'DRAFT' || publishedChanges.length">
            <div class="section-heading"><h3>已发布考试变更记录</h3></div>
            <el-table :data="publishedChanges" border empty-text="暂无变更记录">
              <el-table-column prop="createTime" label="申请时间" min-width="170" />
              <el-table-column prop="changeType" label="变更类型" width="140" />
              <el-table-column label="场次" min-width="180">
                <template #default="scope">
                  {{ subjectName(sessions.find((item) => item.id === scope.row.sessionId)?.subjectId) }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="原因" min-width="180" />
              <el-table-column prop="basePlanVersion" label="基础版本" width="100" />
              <el-table-column prop="appliedPlanVersion" label="生效版本" width="100" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="审批" width="150">
                <template #default="scope">
                  <template v-if="scope.row.status === 'PENDING'">
                    <el-button
                      v-permission="['education:exam:change:approve']"
                      link type="success"
                      @click="decidePublishedChange(scope.row, true)">批准</el-button>
                    <el-button
                      v-permission="['education:exam:change:approve']"
                      link type="danger"
                      @click="decidePublishedChange(scope.row, false)">驳回</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </template>

    </div>

    <el-dialog v-model="dialogs.plan" title="考试计划" width="620px">
      <el-form :model="planForm" label-width="135px">
        <el-form-item label="计划名称">
          <el-input v-model="planForm.planName" />
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="planForm.examType" placeholder="选择考试类型">
            <el-option
              v-for="item in examTypes"
              :key="item.id"
              :label="item.dictName"
              :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="planForm.startDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="planForm.endDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="基础监考人数">
          <el-input-number v-model="planForm.baseInvigilators" :min="1" />
        </el-form-item>
        <el-form-item label="增配人数阈值">
          <el-input-number v-model="planForm.extraStaffThreshold" :min="1" />
        </el-form-item>
        <el-form-item label="允许监考授课班">
          <el-switch v-model="planForm.allowOwnClassInvigilation" />
        </el-form-item>
        <el-form-item label="连续监考上限">
          <el-input-number v-model="planForm.maxConsecutiveDuties" :min="1" />
        </el-form-item>
        <el-form-item label="跨校区通勤分钟">
          <el-input-number v-model="planForm.campusTravelMinutes" :min="0" :max="480" />
        </el-form-item>
        <el-form-item label="要求学科监考资格">
          <el-switch v-model="planForm.requireSubjectQualification" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogs.plan = false">取消</el-button>
        <el-button type="primary" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogs.session" :title="sessionForm.id ? '编辑考试场次' : '新增考试场次'" width="540px">
      <el-form label-width="95px">
        <el-form-item label="考试科目">
          <el-select v-model="sessionForm.subjectId" filterable>
            <el-option
              v-for="item in subjects"
              :key="item.id"
              :label="item.subjectName"
              :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试日期">
          <el-date-picker v-model="sessionForm.examDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-time-picker v-model="sessionForm.startTime" value-format="HH:mm:ss" format="HH:mm" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-time-picker v-model="sessionForm.endTime" value-format="HH:mm:ss" format="HH:mm" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogs.session = false">取消</el-button>
        <el-button type="primary" @click="saveSession">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogs.publishedChange" title="申请变更已发布考试" width="620px">
      <el-form :model="publishedChangeForm" label-width="115px">
        <el-form-item label="变更类型">
          <el-select v-model="publishedChangeForm.changeType">
            <el-option label="场次改期" value="RESCHEDULE" />
            <el-option label="更换考场" value="ROOM_CHANGE" />
            <el-option label="取消场次" value="CANCEL_SESSION" />
            <el-option label="取消整个计划" value="CANCEL_PLAN" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="publishedChangeForm.changeType !== 'CANCEL_PLAN'" label="考试场次">
          <el-select v-model="publishedChangeForm.sessionId" @change="loadChangeRooms">
            <el-option
              v-for="session in sessions.filter((item) => item.status === 'PUBLISHED')"
              :key="session.id"
              :label="`${subjectName(session.subjectId)} · ${session.examDate} ${session.startTime}`"
              :value="session.id" />
          </el-select>
        </el-form-item>
        <template v-if="publishedChangeForm.changeType === 'RESCHEDULE'">
          <el-form-item label="新考试日期">
            <el-date-picker v-model="publishedChangeForm.newExamDate" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="新开始时间">
            <el-time-picker v-model="publishedChangeForm.newStartTime" value-format="HH:mm:ss" format="HH:mm" />
          </el-form-item>
          <el-form-item label="新结束时间">
            <el-time-picker v-model="publishedChangeForm.newEndTime" value-format="HH:mm:ss" format="HH:mm" />
          </el-form-item>
        </template>
        <template v-if="publishedChangeForm.changeType === 'ROOM_CHANGE'">
          <el-form-item label="原考场">
            <el-select v-model="publishedChangeForm.roomId">
              <el-option
                v-for="room in changeRooms"
                :key="room.id"
                :label="classroomName(room.classroomId)"
                :value="room.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="新教室">
            <el-select v-model="publishedChangeForm.newClassroomId" filterable>
              <el-option
                v-for="room in classrooms"
                :key="room.id"
                :label="`${room.roomName}（容量 ${room.capacity}）`"
                :value="room.id" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="变更原因">
          <el-input
            v-model="publishedChangeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogs.publishedChange = false">取消</el-button>
        <el-button type="primary" @click="submitPublishedChange">提交审批</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.exam-page {
  padding: 20px;
}
.page-header,
.section-heading,
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-header,
.section-heading {
  justify-content: space-between;
}
.page-header {
  margin-bottom: 18px;
}
.page-header h2,
.section-heading h3 {
  margin: 0;
}
.page-header p {
  margin: 6px 0 0;
  color: #777;
}
.section-heading {
  margin: 22px 0 10px;
}
.suspension-request-row {
  display: flex;
  gap: 12px;
  margin: 12px 0;
}
</style>
