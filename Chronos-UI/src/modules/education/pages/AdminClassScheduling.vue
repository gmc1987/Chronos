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
        <div class="toolbar">
          <el-button type="primary" @click="openEntry()">新增排课</el-button>
        </div>
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
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="entryDialog" title="课表安排" width="600px">
      <el-form label-width="90px">
        <el-form-item label="教学任务"><el-select v-model="entryForm.offeringId" filterable><el-option v-for="item in offerings" :key="item.id" :label="`${item.courseName} / ${item.teachingClassName} / ${item.teacherName}`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="教室"><el-select v-model="entryForm.classroomId" filterable><el-option v-for="item in classrooms" :key="item.id" :label="`${item.roomName}（${item.capacity}人）`" :value="item.id" /></el-select></el-form-item>
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
import { onMounted, reactive, ref } from 'vue'
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
  listCourseCatalog,
  listCourseOfferings,
  listEducationTeachers,
  updateClassroom,
  updateCourseOffering,
  updateScheduleEntry,
  dictionaryOptions,
} from '../../../api/admin'

const semesterCode = ref('2026-2027-1')
const activeTab = ref('schedule')
const offerings = ref([])
const classrooms = ref([])
const terms = ref([])
const courses = ref([])
const teachers = ref([])
const roomTypes = ref([])
const schedule = ref([])
const entryDialog = ref(false)
const offeringDialog = ref(false)
const classroomDialog = ref(false)
const entryForm = reactive({})
const offeringForm = reactive({})
const classroomForm = reactive({})
const dayName = (day) => ['一', '二', '三', '四', '五', '六', '日'][day - 1]
const reset = (target, value) => { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, value) }
const loadAll = async () => {
  const [termResponse, courseResponse, teacherResponse, offeringResponse, classroomResponse, scheduleResponse, roomTypeResponse] = await Promise.all([
    listAcademicTerms(),
    listCourseCatalog(),
    listEducationTeachers(),
    listCourseOfferings(semesterCode.value),
    listClassrooms(),
    listClassSchedule(semesterCode.value),
    dictionaryOptions('EDU_ROOM_TYPE'),
  ])
  terms.value = termResponse.data || []
  courses.value = courseResponse.data || []
  teachers.value = teacherResponse.data || []
  offerings.value = offeringResponse.data || []
  classrooms.value = classroomResponse.data || []
  schedule.value = scheduleResponse.data || []
  roomTypes.value = (roomTypeResponse?.data || []).map(item => ({ label: item.dictName, value: item.dictValue }))
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
onMounted(loadAll)
</script>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
header h2 { margin: 0 0 6px; }
header p { margin: 0; color: #84909a; }
header .el-input { width: 260px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.separator { margin: 0 12px; color: #84909a; }
</style>
