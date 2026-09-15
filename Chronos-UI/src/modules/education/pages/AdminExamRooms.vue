<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addExamCandidates,
  addExamClassCandidates,
  createExamRoom,
  deleteExamRoom,
  listAcademicTerms,
  listAdministrativeClasses,
  listClassrooms,
  listEducationStudents,
  listEducationSubjects,
  listExamCandidates,
  listExamPlans,
  listExamRooms,
  listExamSessions,
  removeExamCandidate,
} from '../../../api/admin'

const terms = ref([])
const semesterCode = ref('')
const plans = ref([])
const sessions = ref([])
const rooms = ref([])
const candidates = ref([])
const classrooms = ref([])
const students = ref([])
const subjects = ref([])
const classes = ref([])
const selectedPlan = ref(null)
const selectedSession = ref(null)
const selectedRoom = ref(null)
const roomDialog = ref(false)
const candidateDialog = ref(false)
const roomForm = reactive({ classroomId: '', requiredInvigilators: 2 })
const candidateClassId = ref('')
const candidateIds = ref([])
const busy = ref(false)

const unwrap = (response) => response?.data?.content || response?.data || []
const classroom = (id) => classrooms.value.find((item) => item.id === id)
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
  selectedPlan.value = null
  await selectPlan(null)
}

async function selectPlan(plan) {
  selectedPlan.value = plan
  selectedSession.value = null
  selectedRoom.value = null
  sessions.value = plan ? unwrap(await listExamSessions(plan.id)) : []
  rooms.value = []
  candidates.value = []
}

async function selectSession(session) {
  selectedSession.value = session
  selectedRoom.value = null
  rooms.value = session ? unwrap(await listExamRooms(session.id)) : []
  candidates.value = []
}

async function selectRoom(room) {
  selectedRoom.value = room
  candidates.value = room ? unwrap(await listExamCandidates(room.id)) : []
}

function openRoom() {
  Object.assign(roomForm, {
    classroomId: '',
    requiredInvigilators: selectedPlan.value?.baseInvigilators || 2,
  })
  roomDialog.value = true
}

async function saveRoom() {
  if (!roomForm.classroomId) return ElMessage.warning('请选择教室')
  await run(async () => {
    await createExamRoom(selectedSession.value.id, roomForm)
    roomDialog.value = false
    await selectSession(selectedSession.value)
  }, '考场已新增')
}

async function removeRoom(room) {
  try {
    await ElMessageBox.confirm('删除考场及其考生座位与监考安排？', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await run(async () => {
    await deleteExamRoom(selectedSession.value.id, room.id)
    await selectSession(selectedSession.value)
  }, '考场已删除')
}

async function refreshCandidates() {
  const roomId = selectedRoom.value.id
  rooms.value = unwrap(await listExamRooms(selectedSession.value.id))
  await selectRoom(rooms.value.find((item) => item.id === roomId))
}

async function addClass() {
  if (!candidateClassId.value) return ElMessage.warning('请选择行政班')
  await run(async () => {
    await addExamClassCandidates(selectedRoom.value.id, candidateClassId.value)
    candidateDialog.value = false
    await refreshCandidates()
  }, '该班考生已分配')
}

async function addStudents() {
  if (!candidateIds.value.length) return ElMessage.warning('请选择考生')
  await run(async () => {
    await addExamCandidates(selectedRoom.value.id, candidateIds.value)
    candidateDialog.value = false
    await refreshCandidates()
  }, '考生座位已分配')
}

async function removeCandidate(candidate) {
  try {
    await ElMessageBox.confirm('移除此考生座位？', '确认移除', { type: 'warning' })
  } catch {
    return
  }
  await run(async () => {
    await removeExamCandidate(selectedRoom.value.id, candidate.id)
    await refreshCandidates()
  }, '考生座位已移除')
}

onMounted(() => run(async () => {
  const [termResult, classroomResult, studentResult, subjectResult, classResult] = await Promise.all([
    listAcademicTerms(),
    listClassrooms(),
    listEducationStudents(),
    listEducationSubjects(),
    listAdministrativeClasses(),
  ])
  terms.value = unwrap(termResult)
  classrooms.value = unwrap(classroomResult)
  students.value = unwrap(studentResult)
  subjects.value = unwrap(subjectResult)
  classes.value = unwrap(classResult)
  semesterCode.value = terms.value.find((item) => item.currentTerm)?.termCode
    || terms.value[0]?.termCode || ''
  await loadPlans()
}))
</script>

<template>
  <div class="exam-page">
    <header class="page-header">
      <div><h2>考场管理</h2><p>按考试计划和科目场次配置教室、考生与座位。</p></div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadPlans">
        <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
      </el-select>
    </header>
    <div class="selector-row">
      <el-select v-model="selectedPlan" value-key="id" placeholder="选择考试计划" @change="selectPlan">
        <el-option v-for="plan in plans" :key="plan.id" :label="plan.planName" :value="plan" />
      </el-select>
      <el-select v-model="selectedSession" value-key="id" placeholder="选择考试场次" @change="selectSession">
        <el-option v-for="session in sessions" :key="session.id" :label="`${subjectName(session.subjectId)} · ${session.examDate} ${session.startTime}`" :value="session" />
      </el-select>
    </div>

    <div class="section-heading">
      <h3>场次考场</h3>
      <el-button
        v-if="selectedSession && selectedPlan?.status === 'DRAFT'"
        v-permission="['education:exam:room:manage']"
        type="primary" @click="openRoom">新增考场</el-button>
    </div>
    <el-table :data="rooms" border highlight-current-row empty-text="请选择考试场次" @current-change="selectRoom">
      <el-table-column label="教室" min-width="160"><template #default="scope">{{ classroom(scope.row.classroomId)?.roomName || scope.row.classroomId }}</template></el-table-column>
      <el-table-column label="教室容量" width="110"><template #default="scope">{{ classroom(scope.row.classroomId)?.capacity }}</template></el-table-column>
      <el-table-column prop="requiredInvigilators" label="监考人数" width="110" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column v-if="selectedPlan?.status === 'DRAFT'" label="操作" width="100">
        <template #default="scope">
          <el-button v-permission="['education:exam:room:delete', 'education:exam:room:manage']" link type="danger" @click.stop="removeRoom(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-heading">
      <h3>考生座位</h3>
      <el-button
        v-if="selectedRoom && selectedPlan?.status === 'DRAFT'"
        v-permission="['education:exam:room:manage']"
        @click="candidateIds = []; candidateClassId = ''; candidateDialog = true">分配考生</el-button>
    </div>
    <el-table :data="candidates" border empty-text="请选择考场查看考生">
      <el-table-column prop="seatNo" label="座位号" width="100" />
      <el-table-column label="考生" min-width="160"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column>
      <el-table-column v-if="selectedPlan?.status === 'DRAFT'" label="操作" width="100">
        <template #default="scope">
          <el-button v-permission="['education:exam:room:delete', 'education:exam:room:manage']" link type="danger" @click="removeCandidate(scope.row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="roomDialog" title="新增考场" width="500px">
      <el-form label-width="110px">
        <el-form-item label="教室">
          <el-select v-model="roomForm.classroomId" filterable>
            <el-option v-for="item in classrooms" :key="item.id" :label="`${item.roomName}（容量 ${item.capacity}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所需监考人数"><el-input-number v-model="roomForm.requiredInvigilators" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="roomDialog = false">取消</el-button><el-button type="primary" @click="saveRoom">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="candidateDialog" title="分配考生" width="560px">
      <div class="candidate-picker">
        <el-select v-model="candidateClassId" filterable clearable placeholder="按行政班选择">
          <el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" />
        </el-select>
        <el-button @click="addClass">导入该班</el-button>
      </div>
      <el-select v-model="candidateIds" multiple filterable class="full-width" placeholder="或手动选择考生">
        <el-option v-for="item in students" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" />
      </el-select>
      <template #footer><el-button @click="candidateDialog = false">取消</el-button><el-button type="primary" @click="addStudents">分配座位</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exam-page { padding: 20px; }
.page-header, .section-heading, .selector-row, .candidate-picker { display: flex; align-items: center; gap: 12px; }
.page-header, .section-heading { justify-content: space-between; }
.page-header { margin-bottom: 18px; }
.page-header h2, .section-heading h3 { margin: 0; }
.page-header p { margin: 6px 0 0; color: #777; }
.section-heading { margin: 24px 0 12px; }
.selector-row > .el-select { width: 300px; }
.candidate-picker { margin-bottom: 16px; }
.full-width { width: 100%; }
</style>
