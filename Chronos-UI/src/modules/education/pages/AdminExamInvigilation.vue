<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addExamTeacherQualification,
  assignExamInvigilator,
  addExamStandby,
  autoAssignExamInvigilators,
  decideInvigilationChange,
  emergencyInvigilationChange,
  listAcademicTerms,
  listAvailableInvigilators,
  listClassrooms,
  listEducationTeachers,
  listEducationSubjects,
  listExamInvigilators,
  listExamPlans,
  listExamRooms,
  listExamSessions,
  listExamStandbys,
  listInvigilationChanges,
  listExamTeacherQualifications,
  removeExamTeacherQualification,
  removeExamInvigilator,
  markExamInvigilatorAbsent,
} from '../../../api/admin'

const terms = ref([])
const semesterCode = ref('')
const plans = ref([])
const sessions = ref([])
const rooms = ref([])
const assignments = ref([])
const standbys = ref([])
const changes = ref([])
const teachers = ref([])
const classrooms = ref([])
const subjects = ref([])
const qualificationTeacherId = ref('')
const qualificationSubjectId = ref('')
const qualifications = ref([])
const availableTeachers = ref([])
const selectedPlan = ref(null)
const selectedSession = ref(null)
const selectedRoom = ref(null)
const busy = ref(false)
const replacementByChange = reactive({})
const assignDialog = ref(false)
const emergencyDialog = ref(false)
const standbyDialog = ref(false)
const standbyTeacherId = ref('')
const assignmentForm = reactive({ teacherId: '', dutyRole: 'ASSISTANT' })
const emergencyForm = reactive({ assignmentId: '', proposedTeacherId: '', reason: '' })

const unwrap = (response) => response?.data?.content || response?.data || []
const teacherName = (id) => teachers.value.find((item) => item.id === id)?.teacherName || id || ''
const classroomName = (id) => classrooms.value.find((item) => item.id === id)?.roomName || id
const subjectName = (id) => subjects.value.find((item) => item.id === id)?.subjectName || id

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
  selectedPlan.value = plans.value.find((item) => item.id === selectedPlan.value?.id) || null
  if (selectedPlan.value) await selectPlan(selectedPlan.value)
}

async function selectPlan(plan) {
  selectedPlan.value = plan
  selectedSession.value = null
  selectedRoom.value = null
  sessions.value = plan ? unwrap(await listExamSessions(plan.id)) : []
  rooms.value = []
  assignments.value = []
  standbys.value = []
}

async function selectSession(session) {
  selectedSession.value = session
  selectedRoom.value = null
  rooms.value = session ? unwrap(await listExamRooms(session.id)) : []
  assignments.value = []
  standbys.value = session ? unwrap(await listExamStandbys(session.id)) : []
}

async function selectRoom(room) {
  selectedRoom.value = room
  assignments.value = room ? unwrap(await listExamInvigilators(room.id)) : []
}

async function refreshChanges() {
  changes.value = unwrap(await listInvigilationChanges())
}

async function loadQualifications() {
  qualifications.value = qualificationTeacherId.value
    ? unwrap(await listExamTeacherQualifications(qualificationTeacherId.value)) : []
}

async function addQualification() {
  if (!qualificationTeacherId.value || !qualificationSubjectId.value) {
    return ElMessage.warning('请选择教师和科目')
  }
  await run(async () => {
    await addExamTeacherQualification(qualificationTeacherId.value, qualificationSubjectId.value)
    qualificationSubjectId.value = ''
    await loadQualifications()
  }, '监考资格已保存')
}

async function removeQualification(subjectId) {
  await run(async () => {
    await removeExamTeacherQualification(qualificationTeacherId.value, subjectId)
    await loadQualifications()
  }, '监考资格已移除')
}

async function autoAssign() {
  await run(async () => {
    await autoAssignExamInvigilators(selectedPlan.value.id)
    if (selectedRoom.value) await selectRoom(selectedRoom.value)
  }, '自动排班已完成')
}

async function openAssignment() {
  await run(async () => {
    availableTeachers.value = unwrap(await listAvailableInvigilators(selectedRoom.value.id))
    assignmentForm.teacherId = ''
    assignmentForm.dutyRole = assignments.value.some((item) => item.dutyRole === 'CHIEF')
      ? 'ASSISTANT' : 'CHIEF'
    assignDialog.value = true
  })
}

async function saveAssignment() {
  if (!assignmentForm.teacherId) return ElMessage.warning('请选择监考教师')
  await run(async () => {
    await assignExamInvigilator(selectedRoom.value.id, assignmentForm)
    assignDialog.value = false
    await selectRoom(selectedRoom.value)
  }, '监考教师已安排')
}

async function openStandby() {
  if (!selectedRoom.value) return
  await run(async () => {
    availableTeachers.value = unwrap(await listAvailableInvigilators(selectedRoom.value.id))
    standbyTeacherId.value = ''
    standbyDialog.value = true
  })
}

async function saveStandby() {
  if (!standbyTeacherId.value) return ElMessage.warning('请选择机动监考教师')
  await run(async () => {
    await addExamStandby(selectedRoom.value.id, standbyTeacherId.value)
    standbyDialog.value = false
    standbys.value = unwrap(await listExamStandbys(selectedSession.value.id))
  }, '机动监考已安排')
}

async function markAbsent(assignment) {
  try {
    await ElMessageBox.confirm(
      '确认教师未报到？系统将尝试启用当前场次可用的机动监考。',
      '登记缺勤',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await run(async () => {
    const result = await markExamInvigilatorAbsent(assignment.id)
    await Promise.all([
      selectRoom(selectedRoom.value),
      listExamStandbys(selectedSession.value.id).then((value) => { standbys.value = unwrap(value) }),
    ])
    if (!result.data?.replacedById) {
      ElMessage.warning('暂无可用机动监考，请立即手工安排应急替补')
    }
  }, '缺勤已登记')
}

async function removeAssignment(assignment) {
  try {
    await ElMessageBox.confirm('确认移除该监考安排？', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await run(async () => {
    await removeExamInvigilator(selectedRoom.value.id, assignment.id)
    await selectRoom(selectedRoom.value)
  }, '监考安排已移除')
}

async function decide(change, approve) {
  const replacementTeacherId = replacementByChange[change.id] || change.proposedTeacherId
  if (approve && !replacementTeacherId) return ElMessage.warning('请选择接替教师')
  await run(async () => {
    await decideInvigilationChange(change.id, { approve, replacementTeacherId })
    await refreshChanges()
    if (selectedRoom.value) await selectRoom(selectedRoom.value)
  }, approve ? '调换已批准' : '调换已驳回')
}

function openEmergency(assignment) {
  Object.assign(emergencyForm, {
    assignmentId: assignment.id,
    proposedTeacherId: '',
    reason: '',
  })
  emergencyDialog.value = true
}

async function saveEmergency() {
  if (!emergencyForm.proposedTeacherId || !emergencyForm.reason.trim()) {
    return ElMessage.warning('请选择接替教师并填写原因')
  }
  await run(async () => {
    await emergencyInvigilationChange(emergencyForm.assignmentId, emergencyForm)
    emergencyDialog.value = false
    await Promise.all([refreshChanges(), selectRoom(selectedRoom.value)])
  }, '应急替补已完成')
}

onMounted(() => run(async () => {
  const [termResult, teacherResult, classroomResult, subjectResult] = await Promise.all([
    listAcademicTerms(),
    listEducationTeachers(),
    listClassrooms(),
    listEducationSubjects(),
  ])
  terms.value = unwrap(termResult)
  teachers.value = unwrap(teacherResult)
  classrooms.value = unwrap(classroomResult)
  subjects.value = unwrap(subjectResult)
  semesterCode.value = terms.value.find((item) => item.currentTerm)?.termCode
    || terms.value[0]?.termCode || ''
  await Promise.all([loadPlans(), refreshChanges()])
}))
</script>

<template>
  <div class="exam-page">
    <header class="page-header">
      <div>
        <h2>监考排班与调换</h2>
        <p>按考试计划、场次和考场安排监考；处理教师调换和应急替补。</p>
      </div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadPlans">
        <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
      </el-select>
    </header>

    <div class="selector-row">
      <el-select v-model="selectedPlan" value-key="id" placeholder="选择考试计划" @change="selectPlan">
        <el-option v-for="plan in plans" :key="plan.id" :label="plan.planName" :value="plan" />
      </el-select>
      <el-select v-model="selectedSession" value-key="id" placeholder="选择场次" @change="selectSession">
        <el-option v-for="session in sessions" :key="session.id" :label="`${session.examDate} ${session.startTime}`" :value="session" />
      </el-select>
      <el-select v-model="selectedRoom" value-key="id" placeholder="选择考场" @change="selectRoom">
        <el-option v-for="room in rooms" :key="room.id" :label="classroomName(room.classroomId)" :value="room" />
      </el-select>
      <el-button
        v-if="selectedPlan?.status === 'DRAFT'"
        v-permission="['education:exam:invigilation:manage']"
        :loading="busy"
        @click="autoAssign">自动排班</el-button>
    </div>

    <div class="section-heading">
      <h3>考场监考安排</h3>
      <div class="selector-row">
        <el-button
          v-if="selectedRoom && selectedPlan?.status === 'DRAFT'"
          v-permission="['education:exam:invigilation:manage']"
          @click="openStandby">安排机动监考</el-button>
        <el-button
          v-if="selectedRoom && selectedPlan?.status === 'DRAFT'"
          v-permission="['education:exam:invigilation:create', 'education:exam:invigilation:manage']"
          type="primary"
          @click="openAssignment">安排教师</el-button>
      </div>
    </div>
    <el-table :data="assignments" border empty-text="请选择考场查看排班">
      <el-table-column label="监考教师" min-width="160"><template #default="scope">{{ teacherName(scope.row.teacherId) }}</template></el-table-column>
      <el-table-column label="岗位" width="120"><template #default="scope">{{ scope.row.dutyRole === 'CHIEF' ? '主监考' : '副监考' }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="acknowledgedAt" label="确认时间" min-width="170" />
      <el-table-column prop="checkedInAt" label="报到时间" min-width="170" />
      <el-table-column label="操作" width="230">
        <template #default="scope">
          <el-button
            v-if="selectedPlan?.status === 'DRAFT'"
            v-permission="['education:exam:invigilation:delete', 'education:exam:invigilation:manage']"
            link type="danger" @click="removeAssignment(scope.row)">移除</el-button>
          <el-button
            v-else
            v-permission="['education:exam:invigilation:manage']"
            link type="warning" @click="openEmergency(scope.row)">应急替补</el-button>
          <el-button
            v-if="selectedPlan?.status === 'PUBLISHED' && !scope.row.checkedInAt"
            v-permission="['education:exam:invigilation:manage']"
            link type="danger" @click="markAbsent(scope.row)">登记缺勤</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-heading"><h3>本场次机动监考池</h3></div>
    <el-table :data="standbys" border empty-text="暂无机动监考">
      <el-table-column label="教师" min-width="160">
        <template #default="scope">{{ teacherName(scope.row.teacherId) }}</template>
      </el-table-column>
      <el-table-column label="待命考场" min-width="160">
        <template #default="scope">{{ classroomName(rooms.find((room) => room.id === scope.row.roomId)?.classroomId) }}</template>
      </el-table-column>
      <el-table-column prop="checkedInAt" label="报到时间" min-width="170" />
    </el-table>

    <div class="section-heading">
      <h3>教师学科监考资格</h3>
    </div>
    <div class="selector-row">
      <el-select
        v-model="qualificationTeacherId"
        filterable
        placeholder="选择教师"
        @change="loadQualifications">
        <el-option
          v-for="teacher in teachers"
          :key="teacher.id"
          :label="teacher.teacherName"
          :value="teacher.id" />
      </el-select>
      <el-select v-model="qualificationSubjectId" filterable placeholder="选择科目">
        <el-option
          v-for="subject in subjects"
          :key="subject.id"
          :label="subject.subjectName"
          :value="subject.id" />
      </el-select>
      <el-button
        v-permission="['education:exam:invigilation:manage']"
        :disabled="!qualificationTeacherId || !qualificationSubjectId"
        @click="addQualification">添加资格</el-button>
    </div>
    <el-table :data="qualifications" border empty-text="请选择教师查看资格">
      <el-table-column label="科目" min-width="180">
        <template #default="scope">{{ subjectName(scope.row.subjectId) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button
            v-permission="['education:exam:invigilation:manage']"
            link type="danger"
            @click="removeQualification(scope.row.subjectId)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-heading">
      <h3>监考调换申请</h3>
      <el-button @click="refreshChanges">刷新</el-button>
    </div>
    <el-table :data="changes" border>
      <el-table-column prop="createTime" label="申请时间" min-width="170" />
      <el-table-column prop="requestedBy" label="申请人" width="140" />
      <el-table-column prop="reason" label="原因" min-width="200" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="接替教师" min-width="170">
        <template #default="scope">
          <el-select v-if="scope.row.status === 'PENDING'" v-model="replacementByChange[scope.row.id]" filterable clearable>
            <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
          </el-select>
          <span v-else>{{ teacherName(scope.row.proposedTeacherId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="处理" width="150">
        <template #default="scope">
          <template v-if="scope.row.status === 'PENDING'">
            <el-button v-permission="['education:exam:invigilation:manage']" link type="success" @click="decide(scope.row, true)">批准</el-button>
            <el-button v-permission="['education:exam:invigilation:manage']" link type="danger" @click="decide(scope.row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="assignDialog" title="安排监考教师" width="480px">
      <el-form label-width="100px">
        <el-form-item label="教师">
          <el-select v-model="assignmentForm.teacherId" filterable>
            <el-option v-for="teacher in availableTeachers" :key="teacher.id" :label="`${teacher.teacherName}（本学期 ${teacher.semesterDutyCount} 场）`" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-radio-group v-model="assignmentForm.dutyRole">
            <el-radio value="CHIEF">主监考</el-radio><el-radio value="ASSISTANT">副监考</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="assignDialog = false">取消</el-button><el-button type="primary" @click="saveAssignment">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="standbyDialog" title="安排机动监考" width="480px">
      <el-form label-width="100px">
        <el-form-item label="待命教师">
          <el-select v-model="standbyTeacherId" filterable>
            <el-option
              v-for="teacher in availableTeachers"
              :key="teacher.id"
              :label="teacher.teacherName"
              :value="teacher.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="standbyDialog = false">取消</el-button>
        <el-button type="primary" @click="saveStandby">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="emergencyDialog" title="应急替补" width="480px">
      <el-form label-width="100px">
        <el-form-item label="接替教师">
          <el-select v-model="emergencyForm.proposedTeacherId" filterable>
            <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因"><el-input v-model="emergencyForm.reason" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="emergencyDialog = false">取消</el-button><el-button type="primary" @click="saveEmergency">确认替补</el-button></template>
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
.selector-row > .el-select { width: 240px; }
</style>
