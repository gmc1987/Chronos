<template>
  <section class="page">
    <header>
      <h2>{{ titles[section] }}</h2>
    </header>
    <div class="query-bar">
      <OrganizationSelect v-model="schoolId" school-only placeholder="请选择学校" style="width:300px" />
      <el-select v-if="usesTerm" v-model="termCode" :disabled="!schoolId || !terms.length" placeholder="选择学期" filterable style="width:280px" @change="reloadTerm"><el-option v-for="item in terms" :key="item.id" :label="`${item.termName}（${item.termCode}）`" :value="item.termCode" /></el-select>
      <el-input v-model="keyword" clearable placeholder="输入关键词查询" style="width:220px" />
      <el-button :disabled="!schoolId" @click="reload">刷新</el-button>
    </div>

      <div v-if="section === 'programs'">
        <div class="tab-actions"><span v-permission="['education:academic-program:manage']"><AcademicCatalogImport kind="PROGRAMS" button-label="批量导入方案" label="培养方案" endpoint="/admin/education/academic-lifecycle/program-import/PROGRAMS" :organization-id="schoolId" @imported="reload" /><AcademicCatalogImport kind="PROGRAM_COURSES" label="培养方案课程"
          endpoint="/admin/education/academic-lifecycle/program-import/PROGRAM_COURSES" :organization-id="schoolId" @imported="reload" button-label="批量导入课程" /></span>
          <el-button v-if="canManagePrograms" type="primary" plain :loading="batchSubmitting"
            :disabled="!schoolId || !selectedDraftIds.length" @click="submitSelectedPrograms">批量提交（{{ selectedDraftIds.length }}）</el-button>
          <el-button v-permission="['education:academic-program:manage']" type="primary" :disabled="!schoolId" @click="openProgram()">新增培养方案</el-button>
        </div>
        <el-table ref="programTable" :data="pageRows" border row-key="id" @selection-change="selectedPrograms = $event">
          <el-table-column v-if="canManagePrograms" type="selection" width="48" reserve-selection :selectable="isDraftProgram" />
          <el-table-column type="expand"><template #default="scope"><el-table :data="scope.row.courses || []" size="small"><el-table-column prop="courseCode" label="课程编码" /><el-table-column prop="courseName" label="课程名称" /><el-table-column prop="suggestedTermNo" label="建议学期" width="90" /><el-table-column prop="courseNature" label="性质" width="130" /><el-table-column prop="credits" label="学分" width="80" /><el-table-column prop="totalHours" label="课时" width="80" /></el-table></template></el-table-column>
          <el-table-column prop="programCode" label="方案编码" width="150" /><el-table-column prop="programName" label="方案名称" min-width="200" /><el-table-column prop="cohortYear" label="入学年" width="90" /><el-table-column prop="educationLevel" label="层次" width="120" /><el-table-column prop="minimumCredits" label="最低学分" width="100" /><el-table-column prop="totalHours" label="总课时" width="90" /><el-table-column prop="status" label="状态" width="100" />
          <AdaptiveActionColumn label="操作" width="300">
            <template #default="{ row }">
              <div v-if="row.status === 'DRAFT'" v-permission="['education:academic-program:manage']" class="row-actions">
                <el-button link type="primary" @click="openProgram(row)">编辑</el-button>
                <el-button link type="primary" @click="openCourse(row)">添加课程</el-button>
                <el-button link type="primary" @click="submitProgram(row)">提交</el-button>
                <el-button link type="danger" @click="removeProgram(row)">删除</el-button>
              </div>
              <el-button v-if="row.status === 'SUBMITTED'" v-permission="['education:academic-program:review']"
                link type="success" @click="publishProgram(row)">审核发布</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </div>

      <div v-if="section === 'registrations'">
        <div class="tab-actions"><el-button v-permission="['education:term-registration:manage']" type="primary" :disabled="!termCode" @click="openRegistration">提交注册</el-button></div>
        <el-table :data="pageRows" border><el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="培养方案" min-width="220"><template #default="scope">{{ programName(scope.row.programId) }}</template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column label="提交时间" width="180"><template #default="scope">{{ formatDateTime(scope.row.submittedAt) }}</template></el-table-column><el-table-column prop="rejectedReason" label="驳回原因" /><el-table-column label="操作" width="160"><template #default="scope"><template v-if="scope.row.status==='SUBMITTED'"><el-button link type="success" @click="decideRegistrationRow(scope.row,true)">通过</el-button><el-button link type="danger" @click="decideRegistrationRow(scope.row,false)">驳回</el-button></template></template></el-table-column></el-table>
      </div>

      <div v-if="section === 'enrollments'">
        <div class="tab-actions"><el-button v-permission="['education:course-enrollment:manage']" type="primary" :disabled="!termCode" @click="openEnrollment">办理选课</el-button></div>
        <el-table :data="pageRows" border><el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="教学任务" min-width="220"><template #default="scope">{{ offeringName(scope.row.offeringId) }}</template></el-table-column><el-table-column prop="enrollmentSource" label="来源" width="100" /><el-table-column prop="status" label="状态" width="110" /><el-table-column label="选课时间" width="180"><template #default="scope">{{ formatDateTime(scope.row.enrolledAt) }}</template></el-table-column><el-table-column prop="withdrawnReason" label="退课原因" /><el-table-column label="操作" width="90"><template #default="scope"><el-button v-if="scope.row.status==='ENROLLED'" link type="danger" @click="withdraw(scope.row)">退课</el-button></template></el-table-column></el-table>
      </div>

      <div v-if="section === 'graduation'">
        <div class="tab-actions"><el-button v-permission="['education:graduation-audit:manage']" type="primary" :disabled="!schoolId" @click="openGraduation">生成资格核验</el-button></div>
        <el-table :data="pageRows" border>
          <el-table-column label="学生" min-width="180"><template #default="{ row }">{{ studentName(row.studentId) }}</template></el-table-column>
          <el-table-column label="培养方案" min-width="220"><template #default="{ row }">{{ programName(row.programId) }}</template></el-table-column>
          <el-table-column prop="requiredCredits" label="要求学分" width="100" />
          <el-table-column prop="earnedCredits" label="已获学分" width="100" />
          <el-table-column label="状态" width="130"><template #default="{ row }">{{ graduationStatus(row.status) }}</template></el-table-column>
          <el-table-column prop="reviewedBy" label="核验人" width="140" />
          <AdaptiveActionColumn label="操作">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectedGraduation = row">查看详情</el-button>
              <el-button v-if="row.status !== 'APPROVED' && publishedPrograms.some(program => program.id === row.programId)"
                v-permission="['education:graduation-audit:manage']"
                link type="primary" @click="reevaluateGraduation(row)">重新核验</el-button>
              <template v-if="['READY', 'NOT_READY'].includes(row.status) && row.reviewedBy !== currentUsername">
                <el-button v-if="row.status === 'READY'" v-permission="['education:graduation-audit:approve']"
                  link type="success" @click="decideGraduationRow(row, true)">批准</el-button>
                <el-button v-permission="['education:graduation-audit:approve']"
                  link type="danger" @click="decideGraduationRow(row, false)">驳回</el-button>
              </template>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </div>

    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next" :total="filteredRows.length" />

    <el-dialog v-model="programDialog" :title="editingProgram ? '编辑培养方案' : '新增培养方案'" width="620px"><el-form label-width="110px"><el-form-item label="专业"><el-select v-model="programForm.majorId" filterable style="width:100%"><el-option v-for="item in majors" :key="item.id" :label="`${item.majorName}（${item.majorCode}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="入学年份"><el-input-number v-model="programForm.cohortYear" :min="2000" /></el-form-item><el-form-item label="培养层次"><el-select v-model="programForm.educationLevel"><el-option label="高职" value="VOCATIONAL" /><el-option label="大专" value="COLLEGE" /><el-option label="本科" value="UNDERGRADUATE" /></el-select></el-form-item><el-form-item label="方案编码"><el-input v-model="programForm.programCode" maxlength="64" /></el-form-item><el-form-item label="方案名称"><el-input v-model="programForm.programName" maxlength="200" /></el-form-item><el-form-item label="最低毕业学分"><el-input-number v-model="programForm.minimumCredits" :min="0" :precision="2" :max="999999.99" /></el-form-item>
      <el-form-item label="生效日期"><el-date-picker v-model="programForm.effectiveStartDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="失效日期"><el-date-picker v-model="programForm.effectiveEndDate" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form><template #footer><el-button @click="programDialog=false">取消</el-button><el-button type="primary" :loading="programSaving" @click="saveProgram">保存</el-button></template></el-dialog>
    <el-dialog v-model="courseDialog" title="添加培养方案课程" width="620px"><el-form label-width="110px"><el-form-item label="课程"><el-select v-model="courseForm.courseId" :loading="courseOptionsLoading" no-data-text="请在课程管理中维护适用专业" filterable style="width:100%"><el-option v-for="item in courses" :key="item.id" :label="`${item.courseName}（${item.courseCode}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="建议学期"><el-input-number v-model="courseForm.suggestedTermNo" :min="1" /></el-form-item><el-form-item label="课程性质"><el-select v-model="courseForm.courseNature"><el-option label="必修" value="REQUIRED" /><el-option label="选修" value="ELECTIVE" /><el-option label="限选" value="LIMITED_ELECTIVE" /></el-select></el-form-item><el-form-item label="计入学分"><el-input-number v-model="courseForm.credits" :min="0" :precision="2" /></el-form-item></el-form><template #footer><el-button @click="courseDialog=false">取消</el-button><el-button type="primary" :disabled="!courseForm.courseId" @click="saveCourse">保存</el-button></template></el-dialog>
    <el-dialog v-model="registrationDialog" title="提交学期注册" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="registrationForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="培养方案"><el-select v-model="registrationForm.programId" filterable style="width:100%"><el-option v-for="item in publishedPrograms" :key="item.id" :label="item.programName" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="registrationDialog=false">取消</el-button><el-button type="primary" @click="saveRegistration">提交</el-button></template></el-dialog>
    <el-dialog v-model="enrollmentDialog" title="办理选课" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="enrollmentForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="教学任务"><el-select v-model="enrollmentForm.offeringId" filterable remote :remote-method="searchOfferings" style="width:100%"><el-option v-for="item in offerings" :key="item.id" :label="`${item.courseName} / ${item.teachingClassName}`" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="enrollmentDialog=false">取消</el-button><el-button type="primary" @click="saveEnrollment">确认选课</el-button></template></el-dialog>
    <el-dialog v-model="graduationDialog" title="生成毕业资格核验" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="graduationForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="培养方案"><el-select v-model="graduationForm.programId" filterable style="width:100%"><el-option v-for="item in publishedPrograms" :key="item.id" :label="item.programName" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="graduationDialog=false">取消</el-button><el-button type="primary" @click="saveGraduation">开始核验</el-button></template></el-dialog>
    <el-dialog :model-value="!!selectedGraduation" title="毕业审核详情" width="720px"
      @update:model-value="value => { if (!value) selectedGraduation = null }">
      <template v-if="selectedGraduation">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生">{{ studentName(selectedGraduation.studentId) }}</el-descriptions-item>
          <el-descriptions-item label="培养方案">{{ programName(selectedGraduation.programId) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ graduationStatus(selectedGraduation.status) }}</el-descriptions-item>
          <el-descriptions-item label="学分">{{ selectedGraduation.earnedCredits }} / {{ selectedGraduation.requiredCredits }}</el-descriptions-item>
          <el-descriptions-item label="核验人">{{ selectedGraduation.reviewedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="核验时间">{{ formatDateTime(selectedGraduation.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ selectedGraduation.approvedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ formatDateTime(selectedGraduation.approvedAt) }}</el-descriptions-item>
        </el-descriptions>
        <p v-if="['READY', 'NOT_READY'].includes(selectedGraduation.status) && selectedGraduation.reviewedBy === currentUsername">
          请由其他具备审批权限的人员处理。
        </p>
        <el-table v-if="unmetGraduationRequirements.length" :data="unmetGraduationRequirements" border class="graduation-requirements">
          <el-table-column prop="type" label="未满足项" min-width="140">
            <template #default="{ row }">{{ row.courseName || '最低毕业学分' }}</template>
          </el-table-column>
          <el-table-column prop="courseCode" label="课程编码" />
          <el-table-column prop="reason" label="原因" min-width="180" />
          <el-table-column label="缺少学分"><template #default="{ row }">{{ row.missingCredits ?? '-' }}</template></el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref, watch, resolveComponent } from 'vue'
import { useRoute } from 'vue-router'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'
import AcademicCatalogImport from '../components/AcademicCatalogImport.vue'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEducationMajors } from '../../../api/admin'
import { academicOfferingOptions, academicProgramCourseOptions, academicPrograms, academicStudentOptions, academicTermOptions, addAcademicProgramCourse, courseEnrollments, createAcademicProgram, deleteAcademicProgram, decideGraduation, decideTermRegistration, enrollCourse, evaluateGraduation, graduationAudits, publishAcademicProgram, submitAcademicProgram, submitAcademicPrograms, submitTermRegistration, termRegistrations, updateAcademicProgram, withdrawCourse } from '../api/academicLifecycle'

const StudentSelect = defineComponent({ props: { modelValue: String, options: Array }, emits: ['update:modelValue', 'search'], setup(props, { emit }) { return () => h(resolveComponent('el-select'), { modelValue: props.modelValue, 'onUpdate:modelValue': value => emit('update:modelValue', value), filterable: true, remote: true, remoteMethod: value => emit('search', value), style: 'width:100%', placeholder: '输入姓名或学号查询' }, () => (props.options || []).map(item => h(resolveComponent('el-option'), { key: item.id, value: item.id, label: `${item.studentName}（${item.studentNo}）` }))) } })
const unwrap = response => response?.data?.content || response?.data || []
const { section } = defineProps({ section: { type: String, required: true } })
const route = useRoute()
const titles = { programs: '培养方案', registrations: '学期注册', enrollments: '选退课', graduation: '毕业审核' }
const usesTerm = computed(() => ['registrations', 'enrollments'].includes(section))
const schoolId = ref(section === 'registrations' && typeof route.query.schoolId === 'string' ? route.query.schoolId : '')
const termCode = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const terms = ref([]); const majors = ref([]); const courses = ref([]); const students = ref([]); const offerings = ref([])
const studentLabels = ref(new Map())
const offeringLabels = ref(new Map())
const programs = ref([]); const registrations = ref([]); const enrollments = ref([]); const audits = ref([])
const programTable = ref(null)
const selectedPrograms = ref([])
const batchSubmitting = ref(false)
const canManagePrograms = hasAdminPermission('education:academic-program:manage')
const isDraftProgram = row => row.status === 'DRAFT'
const selectedDraftIds = computed(() => {
  const draftIds = new Set(programs.value.filter(isDraftProgram).map(row => row.id))
  return selectedPrograms.value.map(row => row.id).filter(id => draftIds.has(id))
})
const clearProgramSelection = () => {
  programTable.value?.clearSelection()
  selectedPrograms.value = []
}
const programDialog = ref(false); const courseDialog = ref(false); const registrationDialog = ref(false); const enrollmentDialog = ref(false); const graduationDialog = ref(false); const activeProgram = ref(null)
const emptyProgram = () => ({
  majorId: '', cohortYear: new Date().getFullYear(), educationLevel: 'VOCATIONAL',
  programCode: '', programName: '', minimumCredits: 0, effectiveStartDate: null, effectiveEndDate: null,
})
const editingProgram = ref(null)
const programSaving = ref(false)
const programForm = reactive(emptyProgram())
const courseForm = reactive({ courseId: '', suggestedTermNo: 1, courseNature: 'REQUIRED', credits: 0, prerequisiteCourseIds: [] })
const registrationForm = reactive({ studentId: '', programId: '' }); const enrollmentForm = reactive({ studentId: '', offeringId: '' }); const graduationForm = reactive({ studentId: '', programId: '' })
const selectedGraduation = ref(null)
const currentUsername = getAdminUsername()
const graduationStatus = status => ({
  DRAFT: '草稿', READY: '达标 · 待审批', NOT_READY: '未达标 · 待处理', APPROVED: '已批准', REJECTED: '已驳回',
})[status] || status
const unmetGraduationRequirements = computed(() => {
  try {
    const items = JSON.parse(selectedGraduation.value?.unmetRequirementsJson || '[]')
    return Array.isArray(items) ? items : []
  } catch {
    return []
  }
})
const publishedPrograms = computed(() => programs.value.filter(item => item.status === 'PUBLISHED'))
const message = error => error?.response?.data?.msg || error?.message || '操作失败'
const studentName = id => studentLabels.value.get(id) || '学生信息不可见'
const programName = id => programs.value.find(item => item.id === id)?.programName || '培养方案已归档'
const offeringName = id => offeringLabels.value.get(id) || '教学任务不可见'
const formatDateTime = value => value ? String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/Z$/, '') : '-'
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const rows = { programs, registrations, enrollments, graduation: audits }[section].value
  if (!query) return rows
  return rows.filter(row => [
    row.programCode, row.programName, row.cohortYear, row.status, section === 'graduation' && graduationStatus(row.status),
    row.studentId && studentName(row.studentId), row.programId && programName(row.programId),
    row.offeringId && offeringName(row.offeringId),
  ].some(value => String(value ?? '').toLowerCase().includes(query)))
})
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
watch([keyword, pageSize, schoolId, termCode, () => section], () => { page.value = 1 })
watch(keyword, clearProgramSelection)
watch(() => filteredRows.value.length, total => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize.value)))
})
const loadStudentLabels = async records => {
  const id = schoolId.value
  const term = termCode.value
  const ids = [...new Set(records.map(item => item.studentId).filter(Boolean))]
  const batches = Array.from({ length: Math.ceil(ids.length / 100) }, (_, index) => ids.slice(index * 100, index * 100 + 100))
  const results = batches.length ? (await Promise.all(batches.map(batch => academicStudentOptions(id, { ids: batch.join(',') })))).flatMap(unwrap) : []
  if (schoolId.value !== id || termCode.value !== term) return
  studentLabels.value = new Map(results.map(item => [item.id, `${item.studentName}（${item.studentNo}）`]))
}
const reload = async () => {
  const id = schoolId.value
  if (!id) return
  try {
    if (section !== 'enrollments') {
      const programRows = unwrap(await academicPrograms(id, section))
      if (schoolId.value !== id) return
      programs.value = programRows
    }
    if (section === 'graduation') {
      const records = unwrap(await graduationAudits(id))
      if (schoolId.value !== id) return
      audits.value = records
      await loadStudentLabels(records)
    }
    if (usesTerm.value) await reloadTerm()
  } catch (error) { ElMessage.error(message(error)) }
}
const reloadTerm = async () => {
  const id = schoolId.value
  const term = termCode.value
  if (!id) return
  try {
    if (!term) return
    if (section === 'registrations') {
      const records = unwrap(await termRegistrations(id, term))
      if (schoolId.value !== id || termCode.value !== term) return
      registrations.value = records
      await loadStudentLabels(records)
    }
    if (section === 'enrollments') {
      const records = unwrap(await courseEnrollments(id, term))
      if (schoolId.value !== id || termCode.value !== term) return
      enrollments.value = records
      const ids = [...new Set(records.map(row => row.offeringId).filter(Boolean))]
      const batches = Array.from({ length: Math.ceil(ids.length / 100) }, (_, index) => ids.slice(index * 100, index * 100 + 100))
      const [labels] = await Promise.all([
        Promise.all(batches.map(batch => academicOfferingOptions(id, term, { ids: batch.join(',') }))),
        loadStudentLabels(records),
      ])
      if (schoolId.value !== id || termCode.value !== term) return
      offeringLabels.value = new Map(labels.flatMap(unwrap).map(item => [item.id, `${item.courseName} / ${item.teachingClassName}`]))
    }
  } catch (error) { ElMessage.error(message(error)) }
}
const searchStudents = async keyword => {
  const id = schoolId.value
  const response = await academicStudentOptions(id, { keyword })
  if (schoolId.value === id) students.value = unwrap(response)
}
const openProgram = row => {
  editingProgram.value = row || null
  const defaults = emptyProgram()
  for (const field of Object.keys(defaults)) programForm[field] = row?.[field] ?? defaults[field]
  programDialog.value = true
}
const saveProgram = async () => {
  const targetSchool = schoolId.value
  const draft = editingProgram.value
  const payload = { schoolId: targetSchool, ...programForm }
  programSaving.value = true
  try {
    if (draft) await updateAcademicProgram(draft.id, draft.rowVersion, payload)
    else await createAcademicProgram(payload)
    if (schoolId.value !== targetSchool) return
    programDialog.value = false
    ElMessage.success(draft ? '培养方案草稿已更新' : '培养方案草稿已创建')
    await reload()
  } catch (error) {
    ElMessage.error(message(error))
  } finally {
    programSaving.value = false
  }
}
const removeProgram = async row => {
  try {
    await ElMessageBox.confirm(`确定删除「${row.programName}」及其中的方案课程？`, '删除培养方案', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    await deleteAcademicProgram(row.id, row.rowVersion)
    ElMessage.success('培养方案草稿已删除')
    await reload()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(message(error))
  }
}
const courseOptionsLoading = ref(false)
const openCourse = async row => {
  activeProgram.value = row
  courses.value = []
  Object.assign(courseForm, { courseId: '', suggestedTermNo: 1, courseNature: 'REQUIRED', credits: 0, prerequisiteCourseIds: [] })
  courseDialog.value = true
  courseOptionsLoading.value = true
  try {
    const response = await academicProgramCourseOptions(row.id)
    if (activeProgram.value === row) courses.value = unwrap(response)
  } finally {
    if (activeProgram.value === row) courseOptionsLoading.value = false
  }
}
const saveCourse = async () => { try { await addAcademicProgramCourse(activeProgram.value.id, courseForm); courseDialog.value = false; ElMessage.success('课程已加入培养方案'); await reload() } catch (error) { ElMessage.error(message(error)) } }
const submitProgram = async row => { try { await ElMessageBox.confirm('提交后方案将不可继续编辑，确认提交审核？', '提交确认'); await submitAcademicProgram(row.id); ElMessage.success('培养方案已提交'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const submitSelectedPrograms = async () => {
  const targetSchool = schoolId.value
  const programIds = selectedDraftIds.value
  if (batchSubmitting.value || !targetSchool || !programIds.length) return

  batchSubmitting.value = true
  try {
    await ElMessageBox.confirm(
      `确认提交选中的 ${programIds.length} 份培养方案？若有方案未通过校验，本批次均不提交。`,
      '批量提交确认',
      { confirmButtonText: '提交审核', cancelButtonText: '取消' },
    )
    if (schoolId.value !== targetSchool || section !== 'programs') return
    const result = await submitAcademicPrograms({ schoolId: targetSchool, programIds })
    ElMessage.success(`已提交 ${result.data} 份培养方案`)
    if (schoolId.value === targetSchool && section === 'programs') {
      clearProgramSelection()
      await reload()
    }
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(message(error))
  } finally {
    batchSubmitting.value = false
  }
}
const publishProgram = async row => { try { await ElMessageBox.confirm('发布后将归档同专业同年级旧方案，确认发布？', '发布确认', { type: 'warning' }); await publishAcademicProgram(row.id); ElMessage.success('培养方案已发布'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openRegistration = async () => {
  if (!registrationForm.studentId) await searchStudents('')
  registrationDialog.value = true
}
const saveRegistration = async () => { try { await submitTermRegistration({ schoolId: schoolId.value, termCode: termCode.value, ...registrationForm }); registrationDialog.value = false; ElMessage.success('学期注册已提交审核'); await reloadTerm() } catch (error) { ElMessage.error(message(error)) } }
const decideRegistrationRow = async (row, approved) => { try { let reason = ''; if (!approved) reason = (await ElMessageBox.prompt('填写驳回原因', '注册审核')).value; await decideTermRegistration(row.id, { approved, reason }); ElMessage.success(approved ? '注册已确认' : '注册已驳回'); await reloadTerm() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openEnrollment = async () => {
  await searchStudents('')
  await searchOfferings('')
  enrollmentDialog.value = true
}
const searchOfferings = async keyword => {
  const organizationId = schoolId.value
  const term = termCode.value
  const result = await academicOfferingOptions(organizationId, term, { keyword })
  if (schoolId.value === organizationId && termCode.value === term) offerings.value = unwrap(result)
}
const saveEnrollment = async () => { try { await enrollCourse({ schoolId: schoolId.value, termCode: termCode.value, ...enrollmentForm, source: 'MANUAL' }); enrollmentDialog.value = false; ElMessage.success('选课成功'); await reloadTerm() } catch (error) { ElMessage.error(message(error)) } }
const withdraw = async row => { try { const reason = (await ElMessageBox.prompt('填写退课原因', '退课确认', { type: 'warning' })).value; await withdrawCourse(row.id, reason); ElMessage.success('退课成功'); await reloadTerm() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openGraduation = async () => { await searchStudents(''); graduationDialog.value = true }
const saveGraduation = async () => { try { await evaluateGraduation({ schoolId: schoolId.value, ...graduationForm }); graduationDialog.value = false; ElMessage.success('毕业资格核验已生成'); await reload() } catch (error) { ElMessage.error(message(error)) } }
const reevaluateGraduation = async row => {
  const targetSchool = schoolId.value
  try {
    await ElMessageBox.confirm('使用当前成绩重新核验这名学生的毕业资格？', '重新核验毕业资格', { confirmButtonText: '确定', cancelButtonText: '取消' })
    if (schoolId.value !== targetSchool) return
    await evaluateGraduation({ schoolId: targetSchool, studentId: row.studentId, programId: row.programId })
    ElMessage.success('毕业资格已重新核验')
    await reload()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(message(error))
  }
}
const decideGraduationRow = async (row, approved) => { try { let reason = ''; if (!approved) reason = (await ElMessageBox.prompt('填写驳回原因', '毕业审核')).value; await decideGraduation(row.id, { approved, reason }); ElMessage.success(approved ? '毕业资格已批准' : '毕业审核已驳回'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const loadContext = async organizationId => {
  clearProgramSelection()
  termCode.value = ''
  for (const rows of [terms, majors, courses, programs, registrations, enrollments, audits, offerings, students]) rows.value = []
  for (const dialog of [programDialog, courseDialog, registrationDialog, enrollmentDialog, graduationDialog]) dialog.value = false
  studentLabels.value = new Map()
  offeringLabels.value = new Map()
  selectedGraduation.value = null
  activeProgram.value = null
  programForm.majorId = ''
  Object.assign(registrationForm, { studentId: '', programId: '' })
  Object.assign(enrollmentForm, { studentId: '', offeringId: '' })
  Object.assign(graduationForm, { studentId: '', programId: '' })
  if (!organizationId) return
  try {
    const [termResult, majorResult] = await Promise.all([
      usesTerm.value ? academicTermOptions(organizationId) : Promise.resolve(),
      section === 'programs' ? listEducationMajors({ organizationId, includeShared: true }) : Promise.resolve(),
    ])
    if (schoolId.value !== organizationId) return
    terms.value = unwrap(termResult)
    majors.value = unwrap(majorResult)
    const fromAdmission = section === 'registrations' && route.query.schoolId === organizationId && typeof route.query.studentId === 'string'
    const cohortTerm = fromAdmission ? terms.value.find(item => item.termNo === 1
      && String(item.academicYear || '').startsWith(String(route.query.admissionYear))) : null
    termCode.value = (fromAdmission ? cohortTerm : terms.value.find(item => item.currentTerm) || terms.value[0])?.termCode || ''
    await reload()
    if (fromAdmission) {
      const [student] = unwrap(await academicStudentOptions(organizationId, { ids: route.query.studentId }))
      if (student && schoolId.value === organizationId) {
        students.value = [student]
        registrationForm.studentId = student.id
        const matches = publishedPrograms.value.filter(program =>
          program.majorId === student.majorId && program.cohortYear === student.gradeYear)
        registrationForm.programId = matches.length === 1 ? matches[0].id : ''
        registrationDialog.value = !!termCode.value
        if (!termCode.value) ElMessage.warning('未找到该届新生的首学期，请先选择正确的学期')
      }
    }
  } catch (error) {
    if (schoolId.value === organizationId) ElMessage.error(message(error))
  }
}
watch([schoolId, () => section], ([id]) => loadContext(id), { immediate: true })
</script>

<style scoped>
.page { padding: 24px; } header { margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #7c8793; } .query-bar { display: flex; gap: 10px; align-items: center; margin: 16px 0 10px; } .graduation-requirements { margin-top: 14px; } .row-actions { display: flex; align-items: center; white-space: nowrap; } .el-pagination { justify-content: flex-end; margin-top: 10px; } .tab-actions, .tab-actions > span { display: flex; justify-content: flex-end; gap: 10px; margin-bottom: 10px; }
</style>
