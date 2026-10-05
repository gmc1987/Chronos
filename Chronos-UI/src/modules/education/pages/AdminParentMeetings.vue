<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsername } from '../../../store/auth'
import {
  createParentMeeting, listAdminParentMeetings, listAdministrativeClasses,
  listEducationGrades, listEducationStudents, listMeetingRooms, publishParentMeeting,
} from '../../../api/admin'

const meetings = ref([])
const classes = ref([])
const grades = ref([])
const students = ref([])
const rooms = ref([])
const busy = ref(false)
const dialog = ref(false)
const form = ref({ title: '', agenda: '', scopeType: 'CLASS', scopeId: '', meetingType: 'ONLINE', startTime: '', endTime: '', joinUrl: '', roomId: '' })
const unwrap = response => response?.data?.content || response?.data || []
const scopeOptions = computed(() => form.value.scopeType === 'CLASS' ? classes.value.map(item => ({ id: item.id, label: `${item.className} (${item.classCode})` }))
  : form.value.scopeType === 'GRADE' ? grades.value.map(item => ({ id: item.id, label: item.gradeName }))
    : students.value.map(item => ({ id: item.id, label: `${item.studentName}（${item.studentNo}）` })))

async function load() {
  const [meetingResponse, classResponse, gradeResponse, studentResponse, roomResponse] = await Promise.all([
    listAdminParentMeetings(), listAdministrativeClasses(),
    listEducationGrades({ page: 0, size: 200 }), listEducationStudents({ page: 0, size: 200 }),
    listMeetingRooms(),
  ])
  meetings.value = unwrap(meetingResponse)
  classes.value = unwrap(classResponse)
  grades.value = unwrap(gradeResponse)
  students.value = unwrap(studentResponse)
  rooms.value = unwrap(roomResponse)
}
function openCreate() {
  form.value = { title: '', agenda: '', scopeType: 'CLASS', scopeId: '', meetingType: 'ONLINE', startTime: '', endTime: '', joinUrl: '', roomId: '' }
  dialog.value = true
}
async function save() {
  if (!form.value.title.trim() || !form.value.scopeId || !form.value.startTime || !form.value.endTime) {
    ElMessage.warning('请填写主题、范围和起止时间')
    return
  }
  if (form.value.meetingType !== 'ONSITE' && !form.value.joinUrl.trim()) return ElMessage.warning('线上或混合会议需填写加入链接')
  if (form.value.meetingType !== 'ONLINE' && !form.value.roomId) return ElMessage.warning('线下或混合会议需选择会议室')
  busy.value = true
  try {
    await createParentMeeting({ ...form.value, roomId: form.value.meetingType === 'ONLINE' ? null : form.value.roomId, joinUrl: form.value.meetingType === 'ONSITE' ? null : form.value.joinUrl })
    dialog.value = false
    ElMessage.success('家长会草稿已创建，请发布以通知家长')
    await load()
  } finally { busy.value = false }
}
async function publish(row) {
  await ElMessageBox.confirm(`发布家长会“${row.meeting.title}”？`, '发布确认')
  await publishParentMeeting(row.meeting.id)
  ElMessage.success('家长会已发布')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>家长会管理</h2><p>按班级、年级或学生邀请有效监护人，并跟踪会议状态。</p></div><el-button type="primary" @click="openCreate">新建家长会</el-button></header>
    <el-table :data="meetings" border>
      <el-table-column prop="meeting.title" label="主题" min-width="220" />
      <el-table-column prop="meeting.meetingType" label="类型" width="110" />
      <el-table-column prop="meeting.startTime" label="开始时间" width="180" />
      <el-table-column prop="meeting.endTime" label="结束时间" width="180" />
      <el-table-column prop="meeting.status" label="状态" width="110" />
      <el-table-column label="受邀人数" width="100"><template #default="{ row }">{{ row.participants?.length || 0 }}</template></el-table-column>
      <AdaptiveActionColumn label="操作" width="100"><template #default="{ row }"><el-button v-if="row.meeting.status === 'DRAFT' && row.meeting.organizerUsername === getAdminUsername()" v-permission="'education:parent-meeting:manage'" link type="primary" @click="publish(row)">发布</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="dialog" title="新建家长会" width="650px">
      <el-form label-width="110px">
        <el-form-item label="会议主题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="会议范围">
          <div class="scope-row"><el-select v-model="form.scopeType" @change="form.scopeId = ''"><el-option label="班级" value="CLASS" /><el-option label="年级" value="GRADE" /><el-option label="学生" value="STUDENT" /></el-select>
          <el-select v-model="form.scopeId" filterable placeholder="选择范围"><el-option v-for="item in scopeOptions" :key="item.id" :label="item.label" :value="item.id" /></el-select></div>
        </el-form-item>
        <el-form-item label="会议类型"><el-select v-model="form.meetingType"><el-option label="线上" value="ONLINE" /><el-option label="线下" value="ONSITE" /><el-option label="混合" value="HYBRID" /></el-select></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item v-if="form.meetingType !== 'ONSITE'" label="线上链接"><el-input v-model="form.joinUrl" /></el-form-item>
        <el-form-item v-if="form.meetingType !== 'ONLINE'" label="会议室"><el-select v-model="form.roomId" filterable placeholder="选择会议室" style="width: 100%"><el-option v-for="item in rooms.filter(room => room.enabled)" :key="item.id" :label="`${item.roomName}（${item.capacity}人）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="议程"><el-input v-model="form.agenda" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button v-permission="['education:parent-meeting:create', 'education:parent-meeting:manage']" type="primary" :loading="busy" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; } header { display:flex; justify-content:space-between; align-items:center; margin-bottom:18px; } h2 { margin:0 0 6px; } p { margin:0; color:#84909a; } .scope-row { display:flex; gap:12px; width:100%; } .scope-row .el-select:first-child { width:140px; } .scope-row .el-select:last-child { flex:1; }
</style>
