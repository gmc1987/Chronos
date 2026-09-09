<template>
  <div class="page">
    <header>
      <div>
        <h2>走班排课</h2>
        <p>按教学班安排教师、教室、星期、节次和授课周次，保存时自动检查冲突。</p>
      </div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadAll">
        <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
      </el-select>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="周课表" name="schedule">
        <div class="schedule-toolbar">
          <div class="dimension-filter">
            <el-select v-model="scheduleDimension" class="dimension-select" @change="changeDimension">
              <el-option label="全校课表" value="ALL" />
              <el-option label="教师课表" value="TEACHER" />
              <el-option label="教学班课表" value="TEACHING_CLASS" />
              <el-option label="行政班课表" value="ADMIN_CLASS" />
              <el-option label="学生课表" value="STUDENT" />
              <el-option label="教室课表" value="CLASSROOM" />
            </el-select>
            <el-select
              v-if="scheduleDimension !== 'ALL'"
              v-model="scheduleTargetId"
              class="target-select"
              filterable
              clearable
              placeholder="请选择查询对象"
              @change="loadSchedule">
              <el-option
                v-for="item in dimensionOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
            </el-select>
          </div>
          <div>
            <el-button type="success" @click="publishVersion">发布当前课表</el-button>
            <el-button type="primary" @click="openEntry()">新增排课</el-button>
          </div>
        </div>
        <el-empty
          v-if="scheduleDimension !== 'ALL' && !scheduleTargetId"
          description="选择教师、班级、学生或教室后查看课表" />
        <el-table :data="schedule">
          <el-table-column label="时间" width="160">
            <template #default="scope">周{{ dayName(scope.row.dayOfWeek) }} 第 {{ scope.row.periodNo }} 节</template>
          </el-table-column>
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="teachingClassName" label="教学班" />
          <el-table-column prop="teacherName" label="教师" width="120" />
          <el-table-column prop="classroomName" label="教室" width="140" />
          <el-table-column label="周次" width="120">
            <template #default="scope">{{ scope.row.startWeek }}–{{ scope.row.endWeek }} 周</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button link @click="openEntry(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeEntry(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="发布版本" name="versions">
        <div class="toolbar"><el-button @click="loadVersions">刷新</el-button></div>
        <el-table :data="versions">
          <el-table-column prop="versionNo" label="版本" width="90">
            <template #default="scope">V{{ scope.row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="entryCount" label="课表项" width="100" />
          <el-table-column prop="publishedBy" label="发布人" width="130" />
          <el-table-column label="发布时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="120">
            <template #default="scope">{{ scope.row.sourceVersionNo ? `回滚自 V${scope.row.sourceVersionNo}` : '当前草稿' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="scope"><el-button link type="warning" @click="rollbackVersion(scope.row)">回滚</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="教学任务" name="offerings">
        <div class="toolbar"><el-button type="primary" @click="openOffering()">新增教学任务</el-button></div>
        <el-table :data="offerings">
          <el-table-column prop="offeringCode" label="教学班编码" />
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="teachingClassName" label="教学班" />
          <el-table-column prop="teacherName" label="教师" />
          <el-table-column prop="studentCount" label="人数" width="90" />
          <el-table-column prop="weeklyLessons" label="周课时" width="90" />
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button link @click="openOffering(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeOffering(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="offeringPage"
          v-model:page-size="offeringPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="offeringTotal"
          @size-change="changeOfferingPageSize"
          @current-change="loadOfferingsPage"
        />
      </el-tab-pane>

      <el-tab-pane label="教室" name="classrooms">
        <div class="toolbar"><el-button type="primary" @click="openClassroom()">新增教室</el-button></div>
        <el-table :data="classrooms">
          <el-table-column prop="roomCode" label="编码" />
          <el-table-column prop="roomName" label="名称" />
          <el-table-column prop="buildingName" label="教学楼" />
          <el-table-column prop="roomType" label="类型" />
          <el-table-column prop="capacity" label="容量" width="90" />
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button link @click="openClassroom(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeClassroom(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="classroomPage"
          v-model:page-size="classroomPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="classroomTotal"
          @size-change="changeClassroomPageSize"
          @current-change="loadClassroomsPage"
        />
      </el-tab-pane>

	  <el-tab-pane label="调课回写异常" name="incidents">
		<div class="toolbar"><el-button @click="loadIncidents">刷新</el-button></div>
		<el-empty v-if="!incidents.length" description="暂无待处理异常" />
		<el-table v-else :data="incidents">
		  <el-table-column prop="workflowInstanceId" label="流程实例" min-width="210" />
		  <el-table-column prop="adjustmentType" label="调整类型" width="110" />
		  <el-table-column prop="message" label="失败原因" min-width="220" show-overflow-tooltip />
		  <el-table-column prop="retryCount" label="重试次数" width="100" />
		  <el-table-column label="操作" width="100">
			<template #default="scope"><el-button link type="primary" @click="retryIncident(scope.row)">重试</el-button></template>
		  </el-table-column>
		</el-table>
	  </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="entryDialog" title="课表安排" width="600px">
      <el-form label-width="90px">
        <el-form-item label="教学任务"><el-select v-model="entryForm.offeringId" filterable><el-option v-for="item in offeringOptions" :key="item.id" :label="`${item.courseName} / ${item.teachingClassName} / ${item.teacherName}`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="教室"><el-select v-model="entryForm.classroomId" filterable><el-option v-for="item in classroomOptions" :key="item.id" :label="`${item.roomName}（${item.capacity}人）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="星期"><el-select v-model="entryForm.dayOfWeek"><el-option v-for="day in 7" :key="day" :label="`星期${dayName(day)}`" :value="day" /></el-select></el-form-item>
        <el-form-item label="节次"><el-input-number v-model="entryForm.periodNo" :min="1" :max="20" /></el-form-item>
        <el-form-item label="授课周次"><el-input-number v-model="entryForm.startWeek" :min="1" /><span class="separator">至</span><el-input-number v-model="entryForm.endWeek" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="entryDialog = false">取消</el-button><el-button type="primary" @click="saveEntry">保存并校验</el-button></template>
    </el-dialog>

    <el-dialog v-model="offeringDialog" title="教学任务" width="620px">
      <el-form label-width="110px">
        <el-form-item label="教学班编码"><el-input v-model="offeringForm.offeringCode" /></el-form-item>
        <el-form-item label="课程">
          <el-select v-model="offeringForm.courseCode" filterable @change="selectCourse">
            <el-option v-for="course in courses" :key="course.id" :label="`${course.courseName}（${course.courseCode}）`" :value="course.courseCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学班名称"><el-input v-model="offeringForm.teachingClassName" /></el-form-item>
        <el-form-item label="任课教师">
          <el-select v-model="offeringForm.teacherId" filterable @change="selectTeacher">
            <el-option v-for="teacher in teachers" :key="teacher.id" :label="`${teacher.teacherName}（${teacher.teacherNo}）`" :value="teacher.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生人数"><el-input-number v-model="offeringForm.studentCount" :min="1" /></el-form-item>
        <el-form-item label="每周课时"><el-input-number v-model="offeringForm.weeklyLessons" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="offeringDialog = false">取消</el-button><el-button type="primary" @click="saveOffering">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="classroomDialog" title="教室" width="560px">
      <el-form label-width="90px">
        <el-form-item label="教室编码"><el-input v-model="classroomForm.roomCode" /></el-form-item>
        <el-form-item label="教室名称"><el-input v-model="classroomForm.roomName" /></el-form-item>
        <el-form-item label="教学楼"><el-input v-model="classroomForm.buildingName" /></el-form-item>
        <el-form-item label="教室类型"><el-select v-model="classroomForm.roomType"><el-option v-for="item in roomTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="classroomForm.capacity" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="classroomDialog = false">取消</el-button><el-button type="primary" @click="saveClassroom">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createClassroom,
  createCourseOffering,
  createScheduleEntry,
  deleteClassroom,
  deleteCourseOffering,
  deleteScheduleEntry,
  listClassrooms,
  listClassSchedule,
  listAcademicTerms,
  listAdministrativeClasses,
  listCourseCatalog,
  listCourseOfferings,
  listCourseAdjustmentIncidents,
  listEducationTeachers,
  listEducationStudents,
  listScheduleVersions,
  publishScheduleVersion,
  retryCourseAdjustmentIncident,
  rollbackScheduleVersion,
  updateClassroom,
  updateCourseOffering,
  updateScheduleEntry,
  dictionaryOptions,
} from '../../../api/admin'

const semesterCode = ref('2026-2027-1')
const activeTab = ref('schedule')
const offerings = ref([])
const classrooms = ref([])
const offeringOptions = ref([])
const classroomOptions = ref([])
const terms = ref([])
const courses = ref([])
const teachers = ref([])
const students = ref([])
const administrativeClasses = ref([])
const roomTypes = ref([])
const schedule = ref([])
const scheduleDimension = ref('ALL')
const scheduleTargetId = ref('')
const incidents = ref([])
const versions = ref([])
const offeringPage = ref(1)
const offeringPageSize = ref(10)
const offeringTotal = ref(0)
const classroomPage = ref(1)
const classroomPageSize = ref(10)
const classroomTotal = ref(0)
const entryDialog = ref(false)
const offeringDialog = ref(false)
const classroomDialog = ref(false)
const entryForm = reactive({})
const offeringForm = reactive({})
const classroomForm = reactive({})
const dayName = (day) => ['一', '二', '三', '四', '五', '六', '日'][day - 1]
const formatTime = value => value ? new Date(value).toLocaleString() : '-'
const dimensionOptions = computed(() => {
  if (scheduleDimension.value === 'TEACHER') {
    return teachers.value.map(item => ({ label: `${item.teacherName}（${item.teacherNo}）`, value: item.id }))
  }
  if (scheduleDimension.value === 'TEACHING_CLASS') {
    return offeringOptions.value.map(item => ({ label: `${item.teachingClassName} / ${item.courseName}`, value: item.id }))
  }
  if (scheduleDimension.value === 'ADMIN_CLASS') {
    return administrativeClasses.value.map(item => ({ label: item.className, value: item.id }))
  }
  if (scheduleDimension.value === 'STUDENT') {
    return students.value.map(item => ({ label: `${item.studentName}（${item.studentNo}）`, value: item.id }))
  }
  if (scheduleDimension.value === 'CLASSROOM') {
    return classroomOptions.value.map(item => ({ label: item.roomName, value: item.id }))
  }
  return []
})
const reset = (target, value) => { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, value) }
const loadAll = async () => {
  const [termResponse, courseResponse, teacherResponse, studentResponse, classResponse, offeringResponse, classroomResponse, roomTypeResponse, versionResponse] = await Promise.all([
    listAcademicTerms(),
    listCourseCatalog(),
    listEducationTeachers(),
    listEducationStudents(),
    listAdministrativeClasses(),
    listCourseOfferings(semesterCode.value),
    listClassrooms(),
    dictionaryOptions('EDU_ROOM_TYPE'),
    listScheduleVersions(semesterCode.value),
  ])
  terms.value = termResponse.data || []
  courses.value = courseResponse.data || []
  teachers.value = teacherResponse.data || []
  students.value = studentResponse.data || []
  administrativeClasses.value = classResponse.data || []
  offeringOptions.value = offeringResponse.data || []
  classroomOptions.value = classroomResponse.data || []
  roomTypes.value = (roomTypeResponse?.data || []).map(item => ({ label: item.dictName, value: item.dictValue }))
  versions.value = versionResponse.data || []
  offeringPage.value = 1
  classroomPage.value = 1
  await Promise.all([loadOfferingsPage(), loadClassroomsPage(), loadSchedule()])
}
const loadOfferingsPage = async () => {
  const response = await listCourseOfferings(semesterCode.value, {
    page: offeringPage.value - 1,
    size: offeringPageSize.value,
  })
  offerings.value = response.data?.content || response.data || []
  offeringTotal.value = response.data?.totalElements ?? offerings.value.length
}
const loadClassroomsPage = async () => {
  const response = await listClassrooms({
    page: classroomPage.value - 1,
    size: classroomPageSize.value,
  })
  classrooms.value = response.data?.content || response.data || []
  classroomTotal.value = response.data?.totalElements ?? classrooms.value.length
}
const changeOfferingPageSize = () => { offeringPage.value = 1; loadOfferingsPage() }
const changeClassroomPageSize = () => { classroomPage.value = 1; loadClassroomsPage() }
const loadSchedule = async () => {
  if (scheduleDimension.value !== 'ALL' && !scheduleTargetId.value) {
    schedule.value = []
    return
  }
  const response = await listClassSchedule(
    semesterCode.value,
    scheduleDimension.value,
    scheduleTargetId.value || undefined,
  )
  schedule.value = response.data || []
}
const changeDimension = async () => {
  scheduleTargetId.value = ''
  await loadSchedule()
}
const openEntry = (row) => { reset(entryForm, row ? { ...row } : { dayOfWeek: 1, periodNo: 1, startWeek: 1, endWeek: 20 }); entryDialog.value = true }
const saveEntry = async () => { const payload = { ...entryForm, semesterCode: semesterCode.value }; await (entryForm.id ? updateScheduleEntry(entryForm.id, payload) : createScheduleEntry(payload)); entryDialog.value = false; ElMessage.success('课表已保存'); await loadAll() }
const removeEntry = async (row) => { await ElMessageBox.confirm('确认删除该课表安排？', '删除'); await deleteScheduleEntry(row.id); await loadAll() }
const openOffering = (row) => { reset(offeringForm, row ? { ...row } : { studentCount: 30, weeklyLessons: 2, status: 'ACTIVE' }); offeringDialog.value = true }
const selectCourse = code => {
  offeringForm.courseName = courses.value.find(item => item.courseCode === code)?.courseName || ''
}
const selectTeacher = id => {
  offeringForm.teacherName = teachers.value.find(item => item.id === id)?.teacherName || ''
}
const saveOffering = async () => { const payload = { ...offeringForm, semesterCode: semesterCode.value }; await (offeringForm.id ? updateCourseOffering(offeringForm.id, payload) : createCourseOffering(payload)); offeringDialog.value = false; ElMessage.success('教学任务已保存'); await loadAll() }
const removeOffering = async (row) => { await ElMessageBox.confirm('确认删除该教学任务？', '删除'); await deleteCourseOffering(row.id); await loadAll() }
const openClassroom = (row) => { reset(classroomForm, row ? { ...row } : { capacity: 40, roomType: roomTypes.value[0]?.value || '', enabled: true }); classroomDialog.value = true }
const saveClassroom = async () => { await (classroomForm.id ? updateClassroom(classroomForm.id, classroomForm) : createClassroom(classroomForm)); classroomDialog.value = false; ElMessage.success('教室已保存'); await loadAll() }
const removeClassroom = async (row) => { await ElMessageBox.confirm('确认删除该教室？', '删除'); await deleteClassroom(row.id); await loadAll() }
const loadIncidents = async () => {
  incidents.value = (await listCourseAdjustmentIncidents()).data || []
}
const retryIncident = async (row) => {
  const response = await retryCourseAdjustmentIncident(row.id)
  const result = response.data
  if (result?.status === 'APPLIED') ElMessage.success('重放成功，课表已更新')
  else ElMessage.error(result?.message || '重放失败')
  await loadIncidents()
  await loadAll()
}
const loadVersions = async () => {
  versions.value = (await listScheduleVersions(semesterCode.value)).data || []
}
const publishVersion = async () => {
  await ElMessageBox.confirm('发布后会生成不可变版本快照，确认发布当前课表？', '发布课表')
  await publishScheduleVersion(semesterCode.value)
  ElMessage.success('课表版本已发布')
  await loadVersions()
}
const rollbackVersion = async row => {
  await ElMessageBox.confirm(`回滚到 V${row.versionNo}？系统会保留历史并生成一个新版本。`, '回滚课表', { type: 'warning' })
  await rollbackScheduleVersion(row.id)
  ElMessage.success('课表已回滚并生成新版本')
  await Promise.all([loadAll(), loadVersions()])
}
onMounted(async () => {
  await Promise.all([loadAll(), loadIncidents()])
})
</script>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
header h2 { margin: 0 0 6px; }
header p { margin: 0; color: #84909a; }
header .el-input { width: 260px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.schedule-toolbar { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.dimension-filter { display: flex; gap: 10px; }
.dimension-select { width: 140px; }
.target-select { width: 280px; }
.separator { margin: 0 12px; color: #84909a; }
</style>
