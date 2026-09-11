<template>
  <div class="page">
    <header>
      <div><h2>教学班成员</h2><p>按学期和教学班维护学生选课、退课关系，保存时自动检查个人课表冲突。</p></div>
      <el-button @click="router.push('/admin/education/scheduling')">返回走班排课</el-button>
    </header>
    <div class="filters">
      <el-select v-model="semesterCode" placeholder="选择学期" @change="loadOfferings">
        <el-option v-for="item in terms" :key="item.id" :label="item.termName" :value="item.termCode" />
      </el-select>
      <el-select v-model="offeringId" filterable placeholder="选择教学班" @change="loadMembers">
        <el-option v-for="item in offerings" :key="item.id" :label="`${item.teachingClassName} / ${item.courseName} / ${item.teacherName}`" :value="item.id" />
      </el-select>
      <el-button type="primary" :disabled="!offeringId" @click="enrollDialog = true">添加学生</el-button>
    </div>
    <el-descriptions v-if="currentOffering" :column="4" border class="summary">
      <el-descriptions-item label="教学班">{{ currentOffering.teachingClassName }}</el-descriptions-item>
      <el-descriptions-item label="课程">{{ currentOffering.courseName }}</el-descriptions-item>
      <el-descriptions-item label="容量">{{ enrolledCount }} / {{ currentOffering.studentCount }}</el-descriptions-item>
      <el-descriptions-item label="周课时">{{ currentOffering.weeklyLessons }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="members" border>
      <el-table-column label="学号" width="150"><template #default="s">{{ student(s.row.studentId)?.studentNo || '-' }}</template></el-table-column>
      <el-table-column label="姓名" min-width="130"><template #default="s">{{ student(s.row.studentId)?.studentName || s.row.studentId }}</template></el-table-column>
      <el-table-column label="行政班" min-width="150"><template #default="s">{{ className(student(s.row.studentId)?.administrativeClassId) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="s.row.enrollmentStatus === 'ENROLLED' ? 'success' : 'info'">{{ s.row.enrollmentStatus === 'ENROLLED' ? '已选课' : '已退课' }}</el-tag></template></el-table-column>
      <el-table-column prop="enrolledAt" label="选课时间" width="180" /><el-table-column prop="withdrawnAt" label="退课时间" width="180" />
      <el-table-column label="操作" width="100"><template #default="s"><el-button v-if="s.row.enrollmentStatus === 'ENROLLED'" link type="danger" @click="withdraw(s.row)">退课</el-button><el-button v-else link type="primary" @click="reenroll(s.row)">重新选课</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="enrollDialog" title="添加教学班学生" width="560px">
      <el-form label-width="90px"><el-form-item label="学生"><el-select v-model="selectedStudentId" filterable class="full-width"><el-option v-for="item in selectableStudents" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" /></el-select></el-form-item></el-form>
      <el-alert type="info" :closable="false" title="提交时会检查该学生现有选课与本教学班课表是否冲突。" />
      <template #footer><el-button @click="enrollDialog = false">取消</el-button><el-button type="primary" @click="enroll">确认选课</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enrollTeachingClassStudent, listAcademicTerms, listAdministrativeClasses, listCourseOfferings, listEducationStudents, listTeachingClassMembers, withdrawTeachingClassStudent } from '../../../api/admin'

const router = useRouter(); const terms = ref([]); const offerings = ref([]); const students = ref([]); const classes = ref([]); const members = ref([])
const semesterCode = ref(''); const offeringId = ref(''); const enrollDialog = ref(false); const selectedStudentId = ref('')
const currentOffering = computed(() => offerings.value.find(item => item.id === offeringId.value))
const enrolledCount = computed(() => members.value.filter(item => item.enrollmentStatus === 'ENROLLED').length)
const enrolledIds = computed(() => new Set(members.value.filter(item => item.enrollmentStatus === 'ENROLLED').map(item => item.studentId)))
const selectableStudents = computed(() => students.value.filter(item => !enrolledIds.value.has(item.id) && item.enrollmentStatus === 'ACTIVE'))
const student = id => students.value.find(item => item.id === id)
const className = id => classes.value.find(item => item.id === id)?.className || '-'
const loadOfferings = async () => { offerings.value = (await listCourseOfferings(semesterCode.value)).data || []; offeringId.value = ''; members.value = [] }
const loadMembers = async () => { members.value = offeringId.value ? (await listTeachingClassMembers(offeringId.value)).data || [] : [] }
const enroll = async () => { if (!selectedStudentId.value) return ElMessage.warning('请选择学生'); await enrollTeachingClassStudent(offeringId.value, selectedStudentId.value); enrollDialog.value = false; selectedStudentId.value = ''; ElMessage.success('选课成功'); await loadMembers() }
const reenroll = async row => { await enrollTeachingClassStudent(offeringId.value, row.studentId); ElMessage.success('重新选课成功'); await loadMembers() }
const withdraw = async row => { await ElMessageBox.confirm(`确认将“${student(row.studentId)?.studentName || row.studentId}”退出该教学班？`, '退课确认', { type: 'warning' }); await withdrawTeachingClassStudent(offeringId.value, row.studentId); ElMessage.success('退课成功'); await loadMembers() }
onMounted(async () => { const [termResult, studentResult, classResult] = await Promise.all([listAcademicTerms(), listEducationStudents(), listAdministrativeClasses()]); terms.value = termResult.data || []; students.value = studentResult.data || []; classes.value = classResult.data || []; const current = terms.value.find(item => item.currentTerm) || terms.value[0]; if (current) { semesterCode.value = current.termCode; await loadOfferings() } })
</script>

<style scoped>
.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; } .filters { display: flex; gap: 12px; margin-bottom: 16px; } .filters .el-select:first-child { width: 220px; } .filters .el-select:nth-child(2) { width: 420px; } .summary { margin-bottom: 16px; } .full-width { width: 100%; }
</style>
