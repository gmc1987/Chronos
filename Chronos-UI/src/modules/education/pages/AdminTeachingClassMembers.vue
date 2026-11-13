<template>
  <div class="page">
    <header>
      <div><h2>教学班成员</h2><p>按学期和教学班维护学生选课、退课关系，保存时自动检查个人课表冲突。</p></div>
      <el-button @click="router.push({ name: 'admin-education-class-scheduling', query: { organizationId, semesterCode, schedulingTab: 'offerings' } })">返回走班排课</el-button>
    </header>
    <div class="filters">
      <OrganizationSelect v-model="organizationId" school-or-campus-only style="width: 250px" />
      <el-select v-model="semesterCode" filterable placeholder="选择学期" style="width: 220px"
        :disabled="!organizationId || !terms.length" :loading="termsLoading">
        <el-option v-for="item in terms" :key="item.id" :label="item.termName" :value="item.termCode" />
      </el-select>
      <el-select v-model="offeringId" filterable placeholder="选择教学班" style="width: 420px"
        :disabled="!semesterCode || !offerings.length" :loading="offeringsLoading">
        <el-option v-for="item in offerings" :key="item.id" :label="`${item.teachingClassName} / ${item.courseName} / ${item.teacherName}`" :value="item.id" />
      </el-select>
      <el-button type="primary" :disabled="!offeringId || isCombined" @click="openEnroll">添加学生</el-button>
    </div>
    <el-alert v-if="isCombined" type="info" :closable="false" title="此教学班是合班公共课，学生由走班排课中的“合班设置”同步，不能逐人增删。" />
    <el-descriptions v-if="currentOffering" :column="4" border class="summary">
      <el-descriptions-item label="教学班">{{ currentOffering.teachingClassName }}</el-descriptions-item>
      <el-descriptions-item label="课程">{{ currentOffering.courseName }}</el-descriptions-item>
      <el-descriptions-item label="容量">{{ enrolledCount }} / {{ currentOffering.studentCount }}</el-descriptions-item>
      <el-descriptions-item label="周课时">{{ currentOffering.weeklyLessons }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-loading="membersLoading" :data="members" border
      :empty-text="!organizationId ? '请先选择机构' : !semesterCode ? '请选择学期' : !offerings.length ? '本学期暂无教学班' : !offeringId ? '请选择教学班' : '该教学班暂无成员'">
      <el-table-column label="学号" width="150"><template #default="s">{{ student(s.row.studentId)?.studentNo || '-' }}</template></el-table-column>
      <el-table-column label="姓名" min-width="130"><template #default="s">{{ student(s.row.studentId)?.studentName || s.row.studentId }}</template></el-table-column>
      <el-table-column label="行政班" min-width="150"><template #default="s">{{ className(student(s.row.studentId)?.administrativeClassId) }}</template></el-table-column>
      <el-table-column label="加入来源" width="110"><template #default="s">{{ s.row.enrollmentSource === 'SOURCE_CLASS' ? '来源班级' : '手工' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="s.row.enrollmentStatus === 'ENROLLED' ? 'success' : 'info'">{{ s.row.enrollmentStatus === 'ENROLLED' ? '已选课' : '已退课' }}</el-tag></template></el-table-column>
      <el-table-column prop="enrolledAt" label="选课时间" width="180" /><el-table-column prop="withdrawnAt" label="退课时间" width="180" />
      <AdaptiveActionColumn label="操作" width="100"><template #default="s"><el-button v-if="!isCombined && s.row.enrollmentStatus === 'ENROLLED'" link type="danger" @click="withdraw(s.row)">退课</el-button><el-button v-else-if="!isCombined" link type="primary" @click="reenroll(s.row)">重新选课</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-pagination v-if="offeringId" class="member-pagination"
      :current-page="memberPage" :page-size="memberPageSize" :page-sizes="[10, 20, 50, 100]"
      :total="memberTotal" layout="total, sizes, prev, pager, next"
      @update:current-page="changeMemberPage" @update:page-size="changeMemberPageSize" />
    <el-dialog v-model="enrollDialog" title="添加教学班学生" width="560px">
      <el-form label-width="90px"><el-form-item label="学生"><el-select v-model="selectedStudentId" filterable remote :remote-method="searchStudents" :loading="studentLoading" class="full-width" placeholder="输入姓名或学号查询"><el-option v-for="item in selectableStudents" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" /></el-select></el-form-item></el-form>
      <el-pagination v-if="studentTotal > 10" :total="studentTotal" :page-size="10" :current-page="studentPage + 1" layout="prev, pager, next" @current-change="page => loadStudentOptions(page - 1)" />
      <el-alert type="info" :closable="false" title="提交时会检查该学生现有选课与本教学班课表是否冲突。" />
      <template #footer><el-button @click="enrollDialog = false">取消</el-button><el-button type="primary" @click="enroll">确认选课</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enrollTeachingClassStudent, listAcademicTerms, listAdministrativeClasses, listCourseOfferings, listEducationStudents, lookupEducationStudents, listTeachingClassMembers, withdrawTeachingClassStudent } from '../../../api/admin'

const router = useRouter()
const route = useRoute()
const initialOrganization = typeof route.query.organizationId === 'string' ? route.query.organizationId : ''
const initialSemester = typeof route.query.semesterCode === 'string' ? route.query.semesterCode : ''
const organizationId = ref(initialOrganization)
const termsLoading = ref(false)
const offeringsLoading = ref(false)
const membersLoading = ref(false)
const terms = ref([]); const offerings = ref([]); const memberStudents = ref([]); const studentOptions = ref([]); const classes = ref([]); const members = ref([])
const semesterCode = ref(''); const offeringId = ref(''); const enrollDialog = ref(false); const selectedStudentId = ref('')
const studentKeyword = ref(''); const studentPage = ref(0); const studentTotal = ref(0); const studentLoading = ref(false)
let termRequest = 0
let offeringRequest = 0
let memberRequest = 0
let studentRequest = 0
const currentOffering = computed(() => offerings.value.find(item => item.id === offeringId.value))
const isCombined = computed(() => currentOffering.value?.offeringMode === 'COMBINED')
const memberPage = ref(1)
const memberPageSize = ref(20)
const memberTotal = ref(0)
const enrolledCount = ref(0)
const selectableStudents = studentOptions
const student = id => memberStudents.value.find(item => item.id === id) || studentOptions.value.find(item => item.id === id)
const showError = error => ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
const className = id => classes.value.find(item => item.id === id)?.className || '-'
const loadTerms = async id => {
  const request = ++termRequest
  terms.value = []
  semesterCode.value = ''
  termsLoading.value = !!id
  if (!id) return
  try {
    const result = await listAcademicTerms({ organizationId: id })
    if (request !== termRequest) return
    terms.value = result.data || []
    const preferred = id === initialOrganization && terms.value.find(item => item.termCode === initialSemester)
    semesterCode.value = (preferred || terms.value.find(item => item.currentTerm) || terms.value[0])?.termCode || ''
  } catch (error) {
    if (request === termRequest) showError(error)
  } finally {
    if (request === termRequest) termsLoading.value = false
  }
}
const loadOfferings = async ([id, semester]) => {
  const request = ++offeringRequest
  offerings.value = []
  offeringId.value = ''
  offeringsLoading.value = !!(id && semester)
  if (!id || !semester) return
  try {
    const result = await listCourseOfferings(semester, { organizationId: id })
    if (request !== offeringRequest) return
    offerings.value = result.data?.content || result.data || []
  } catch (error) {
    if (request === offeringRequest) showError(error)
  } finally {
    if (request === offeringRequest) offeringsLoading.value = false
  }
}
const loadMembers = async () => {
  const request = ++memberRequest
  const id = offeringId.value
  membersLoading.value = !!id
  if (!id) return
  const campusId = currentOffering.value?.campusId || organizationId.value
  try {
    const result = await listTeachingClassMembers(id, { page: memberPage.value - 1, size: memberPageSize.value })
    if (request !== memberRequest) return
    const { page, enrolledCount: count } = result.data
    memberTotal.value = page.totalElements
    enrolledCount.value = count
    const lastPage = Math.max(1, page.totalPages)
    if (memberPage.value > lastPage) {
      memberPage.value = lastPage
      return loadMembers()
    }
    members.value = page.content
    memberStudents.value = []
    const ids = [...new Set(members.value.map(item => item.studentId))]
    const batches = []
    for (let index = 0; index < ids.length; index += 100) batches.push(ids.slice(index, index + 100))
    const [studentResults, classResult] = await Promise.all([
      Promise.all(batches.map(batch => lookupEducationStudents(batch))),
      classes.value.length ? Promise.resolve({ data: classes.value }) : listAdministrativeClasses({ campusId }),
    ])
    if (request !== memberRequest) return
    memberStudents.value = studentResults.flatMap(response => response.data || [])
    classes.value = classResult.data || []
  } catch (error) {
    if (request === memberRequest) showError(error)
  } finally {
    if (request === memberRequest) membersLoading.value = false
  }
}
const changeMemberPage = page => {
  if (page === memberPage.value) return
  memberPage.value = page
  loadMembers()
}
const changeMemberPageSize = size => {
  if (size === memberPageSize.value) return
  memberPageSize.value = size
  memberPage.value = 1
  loadMembers()
}
const loadStudentOptions = async (page = 0) => {
  const request = ++studentRequest
  studentLoading.value = true
  try {
    const result = await listEducationStudents({
      page, size: 10, keyword: studentKeyword.value,
      organizationId: currentOffering.value?.campusId || organizationId.value,
      excludeEnrolledOfferingId: offeringId.value,
    })
    if (request !== studentRequest) return
    studentOptions.value = result.data?.content || []
    studentTotal.value = result.data?.totalElements || 0
    studentPage.value = page
  } catch (error) { if (request === studentRequest) showError(error) }
  finally { if (request === studentRequest) studentLoading.value = false }
}
const searchStudents = keyword => { studentKeyword.value = keyword.trim(); loadStudentOptions(0) }
const openEnroll = async () => { selectedStudentId.value = ''; studentKeyword.value = ''; enrollDialog.value = true; await loadStudentOptions(0) }
const enroll = async () => {
  if (!selectedStudentId.value) return ElMessage.warning('请选择学生')
  try {
    await enrollTeachingClassStudent(offeringId.value, selectedStudentId.value)
    enrollDialog.value = false
    selectedStudentId.value = ''
    ElMessage.success('选课成功')
    await loadMembers()
  } catch (error) { showError(error) }
}
const reenroll = async row => {
  try {
    await enrollTeachingClassStudent(offeringId.value, row.studentId)
    ElMessage.success('重新选课成功')
    await loadMembers()
  } catch (error) { showError(error) }
}
const withdraw = async row => {
  const targetOffering = offeringId.value
  try {
    await ElMessageBox.confirm(`确认将“${student(row.studentId)?.studentName || row.studentId}”退出该教学班？`, '退课确认', { type: 'warning' })
  } catch { return }
  if (offeringId.value !== targetOffering) return
  try {
    await withdrawTeachingClassStudent(targetOffering, row.studentId)
    ElMessage.success('退课成功')
    await loadMembers()
  } catch (error) { showError(error) }
}
watch(organizationId, loadTerms, { immediate: true })
watch([organizationId, semesterCode], loadOfferings)
watch(offeringId, () => {
  memberPage.value = 1
  memberTotal.value = 0
  enrolledCount.value = 0
  members.value = []
  memberStudents.value = []
  classes.value = []
  enrollDialog.value = false
  selectedStudentId.value = ''
  studentOptions.value = []
  studentTotal.value = 0
  studentRequest++
  studentLoading.value = false
  loadMembers()
})
</script>

<style scoped>
.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; } .filters { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; } .summary { margin-bottom: 16px; } .member-pagination { justify-content: flex-end; margin-top: 10px; } .full-width { width: 100%; }
</style>
