<template>
  <section class="page">
    <header>
      <div><h2>培养与学籍运行</h2><p>以培养方案为源头办理学期注册、选退课和毕业资格审核；正式数据均保留版本与审计。</p></div>
    </header>
    <el-alert type="warning" :closable="false" title="培养方案提交人不能审核发布自己的方案；注册提交人与审核人、毕业核验人与批准人也必须分离。" />
    <div class="query-bar">
      <el-select v-model="schoolId" placeholder="选择学校" filterable style="width:300px" @change="reload"><el-option v-for="item in schools" :key="item.id" :label="item.organizationName || item.orgName" :value="item.id" /></el-select>
      <el-select v-model="termCode" placeholder="选择学期" filterable style="width:280px" @change="reloadTerm"><el-option v-for="item in terms" :key="item.id" :label="`${item.termName}（${item.termCode}）`" :value="item.termCode" /></el-select>
      <el-button @click="reload">刷新</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="培养方案" name="programs">
        <div class="tab-actions"><el-button type="primary" @click="programDialog=true">新增培养方案</el-button></div>
        <el-table :data="programs" border row-key="id">
          <el-table-column type="expand"><template #default="scope"><el-table :data="scope.row.courses || []" size="small"><el-table-column prop="courseCode" label="课程编码" /><el-table-column prop="courseName" label="课程名称" /><el-table-column prop="suggestedTermNo" label="建议学期" width="90" /><el-table-column prop="courseNature" label="性质" width="130" /><el-table-column prop="credits" label="学分" width="80" /><el-table-column prop="totalHours" label="课时" width="80" /></el-table></template></el-table-column>
          <el-table-column prop="programCode" label="方案编码" width="150" /><el-table-column prop="programName" label="方案名称" min-width="200" /><el-table-column prop="cohortYear" label="入学年" width="90" /><el-table-column prop="educationLevel" label="层次" width="120" /><el-table-column prop="minimumCredits" label="最低学分" width="100" /><el-table-column prop="totalHours" label="总课时" width="90" /><el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作" min-width="260"><template #default="scope"><el-button v-if="scope.row.status==='DRAFT'" link type="primary" @click="openCourse(scope.row)">添加课程</el-button><el-button v-if="scope.row.status==='DRAFT'" link type="primary" @click="submitProgram(scope.row)">提交</el-button><el-button v-if="scope.row.status==='SUBMITTED'" link type="success" @click="publishProgram(scope.row)">审核发布</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学期注册" name="registrations">
        <div class="tab-actions"><el-button type="primary" :disabled="!termCode" @click="openRegistration">提交注册</el-button></div>
        <el-table :data="registrations" border><el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="培养方案" min-width="220"><template #default="scope">{{ programName(scope.row.programId) }}</template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column label="提交时间" width="180"><template #default="scope">{{ formatDateTime(scope.row.submittedAt) }}</template></el-table-column><el-table-column prop="rejectedReason" label="驳回原因" /><el-table-column label="操作" width="160"><template #default="scope"><template v-if="scope.row.status==='SUBMITTED'"><el-button link type="success" @click="decideRegistrationRow(scope.row,true)">通过</el-button><el-button link type="danger" @click="decideRegistrationRow(scope.row,false)">驳回</el-button></template></template></el-table-column></el-table>
      </el-tab-pane>

      <el-tab-pane label="选退课" name="enrollments">
        <div class="tab-actions"><el-button type="primary" :disabled="!termCode" @click="openEnrollment">办理选课</el-button></div>
        <el-table :data="enrollments" border><el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="教学任务" min-width="220"><template #default="scope">{{ offeringName(scope.row.offeringId) }}</template></el-table-column><el-table-column prop="enrollmentSource" label="来源" width="100" /><el-table-column prop="status" label="状态" width="110" /><el-table-column label="选课时间" width="180"><template #default="scope">{{ formatDateTime(scope.row.enrolledAt) }}</template></el-table-column><el-table-column prop="withdrawnReason" label="退课原因" /><el-table-column label="操作" width="90"><template #default="scope"><el-button v-if="scope.row.status==='ENROLLED'" link type="danger" @click="withdraw(scope.row)">退课</el-button></template></el-table-column></el-table>
      </el-tab-pane>

      <el-tab-pane label="毕业审核" name="graduation">
        <div class="tab-actions"><el-button type="primary" @click="openGraduation">生成资格核验</el-button></div>
        <el-table :data="audits" border><el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="培养方案" min-width="220"><template #default="scope">{{ programName(scope.row.programId) }}</template></el-table-column><el-table-column prop="requiredCredits" label="要求学分" width="100" /><el-table-column prop="earnedCredits" label="已获学分" width="100" /><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="reviewedBy" label="核验人" width="140" /><el-table-column label="操作" width="160"><template #default="scope"><template v-if="['READY','NOT_READY'].includes(scope.row.status)"><el-button link type="success" :disabled="scope.row.status!=='READY'" @click="decideGraduationRow(scope.row,true)">批准</el-button><el-button link type="danger" @click="decideGraduationRow(scope.row,false)">驳回</el-button></template></template></el-table-column></el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="programDialog" title="新增培养方案" width="620px"><el-form label-width="110px"><el-form-item label="专业"><el-select v-model="programForm.majorId" filterable style="width:100%"><el-option v-for="item in majors" :key="item.id" :label="`${item.majorName}（${item.majorCode}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="入学年份"><el-input-number v-model="programForm.cohortYear" :min="2000" /></el-form-item><el-form-item label="培养层次"><el-select v-model="programForm.educationLevel"><el-option label="高职" value="VOCATIONAL" /><el-option label="大专" value="COLLEGE" /><el-option label="本科" value="UNDERGRADUATE" /></el-select></el-form-item><el-form-item label="方案编码"><el-input v-model="programForm.programCode" /></el-form-item><el-form-item label="方案名称"><el-input v-model="programForm.programName" /></el-form-item><el-form-item label="最低毕业学分"><el-input-number v-model="programForm.minimumCredits" :min="0" :precision="2" /></el-form-item></el-form><template #footer><el-button @click="programDialog=false">取消</el-button><el-button type="primary" @click="saveProgram">保存</el-button></template></el-dialog>
    <el-dialog v-model="courseDialog" title="添加培养方案课程" width="620px"><el-form label-width="110px"><el-form-item label="课程"><el-select v-model="courseForm.courseId" filterable style="width:100%"><el-option v-for="item in courses" :key="item.id" :label="`${item.courseName}（${item.courseCode}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="建议学期"><el-input-number v-model="courseForm.suggestedTermNo" :min="1" /></el-form-item><el-form-item label="课程性质"><el-select v-model="courseForm.courseNature"><el-option label="必修" value="REQUIRED" /><el-option label="选修" value="ELECTIVE" /><el-option label="限选" value="LIMITED_ELECTIVE" /></el-select></el-form-item><el-form-item label="计入学分"><el-input-number v-model="courseForm.credits" :min="0" :precision="2" /></el-form-item></el-form><template #footer><el-button @click="courseDialog=false">取消</el-button><el-button type="primary" @click="saveCourse">保存</el-button></template></el-dialog>
    <el-dialog v-model="registrationDialog" title="提交学期注册" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="registrationForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="培养方案"><el-select v-model="registrationForm.programId" filterable style="width:100%"><el-option v-for="item in publishedPrograms" :key="item.id" :label="item.programName" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="registrationDialog=false">取消</el-button><el-button type="primary" @click="saveRegistration">提交</el-button></template></el-dialog>
    <el-dialog v-model="enrollmentDialog" title="办理选课" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="enrollmentForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="教学任务"><el-select v-model="enrollmentForm.offeringId" filterable style="width:100%"><el-option v-for="item in offerings" :key="item.id" :label="`${item.courseName} / ${item.teachingClassName}`" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="enrollmentDialog=false">取消</el-button><el-button type="primary" @click="saveEnrollment">确认选课</el-button></template></el-dialog>
    <el-dialog v-model="graduationDialog" title="生成毕业资格核验" width="620px"><el-form label-width="100px"><el-form-item label="学生"><student-select v-model="graduationForm.studentId" :options="students" @search="searchStudents" /></el-form-item><el-form-item label="培养方案"><el-select v-model="graduationForm.programId" filterable style="width:100%"><el-option v-for="item in publishedPrograms" :key="item.id" :label="item.programName" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="graduationDialog=false">取消</el-button><el-button type="primary" @click="saveGraduation">开始核验</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAcademicTerms, listCourseCatalog, listCourseOfferings, listEducationMajors, listEducationStudents, lookupEducationStudents, orgList } from '../../../api/admin'
import { academicPrograms, addAcademicProgramCourse, courseEnrollments, createAcademicProgram, decideGraduation, decideTermRegistration, enrollCourse, evaluateGraduation, graduationAudits, publishAcademicProgram, submitAcademicProgram, submitTermRegistration, termRegistrations, withdrawCourse } from '../api/academicLifecycle'

const StudentSelect = defineComponent({ props: { modelValue: String, options: Array }, emits: ['update:modelValue', 'search'], setup(props, { emit }) { return () => h(resolveComponent('el-select'), { modelValue: props.modelValue, 'onUpdate:modelValue': value => emit('update:modelValue', value), filterable: true, remote: true, remoteMethod: value => emit('search', value), style: 'width:100%', placeholder: '输入姓名或学号查询' }, () => (props.options || []).map(item => h(resolveComponent('el-option'), { key: item.id, value: item.id, label: `${item.studentName}（${item.studentNo}）` }))) } })
const unwrap = response => response?.data?.content || response?.data || []
const tab = ref('programs'); const schoolId = ref(''); const termCode = ref('')
const schools = ref([]); const terms = ref([]); const majors = ref([]); const courses = ref([]); const students = ref([]); const offerings = ref([])
const studentLabels = ref(new Map())
const programs = ref([]); const registrations = ref([]); const enrollments = ref([]); const audits = ref([])
const programDialog = ref(false); const courseDialog = ref(false); const registrationDialog = ref(false); const enrollmentDialog = ref(false); const graduationDialog = ref(false); const activeProgram = ref(null)
const programForm = reactive({ majorId: '', cohortYear: new Date().getFullYear(), educationLevel: 'VOCATIONAL', programCode: '', programName: '', minimumCredits: 0 })
const courseForm = reactive({ courseId: '', suggestedTermNo: 1, courseNature: 'REQUIRED', credits: 0, prerequisiteCourseIds: [] })
const registrationForm = reactive({ studentId: '', programId: '' }); const enrollmentForm = reactive({ studentId: '', offeringId: '' }); const graduationForm = reactive({ studentId: '', programId: '' })
const publishedPrograms = computed(() => programs.value.filter(item => item.status === 'PUBLISHED'))
const message = error => error?.response?.data?.msg || error?.message || '操作失败'
const studentName = id => studentLabels.value.get(id) || '学生信息不可见'
const programName = id => programs.value.find(item => item.id === id)?.programName || '培养方案已归档'
const offeringName = id => { const item = offerings.value.find(value => value.id === id); return item ? `${item.courseName} / ${item.teachingClassName}` : '教学任务已归档' }
const formatDateTime = value => value ? String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/Z$/, '') : '-'
const loadStudentLabels = async records => {
  const ids = [...new Set(records.map(item => item.studentId).filter(Boolean))]
  const batches = Array.from({ length: Math.ceil(ids.length / 100) }, (_, index) => ids.slice(index * 100, index * 100 + 100))
  const results = batches.length ? (await Promise.all(batches.map(batch => lookupEducationStudents(batch)))).flatMap(unwrap) : []
  studentLabels.value = new Map(results.map(item => [item.id, `${item.studentName}（${item.studentNo}）`]))
}
const reload = async () => { if (!schoolId.value) return; try { programs.value = unwrap(await academicPrograms(schoolId.value)); audits.value = unwrap(await graduationAudits(schoolId.value)); await reloadTerm() } catch (error) { ElMessage.error(message(error)) } }
const reloadTerm = async () => { if (!schoolId.value || !termCode.value) return; try { [registrations.value, enrollments.value, offerings.value] = await Promise.all([termRegistrations(schoolId.value, termCode.value).then(unwrap), courseEnrollments(schoolId.value, termCode.value).then(unwrap), listCourseOfferings(termCode.value, { page: 0, size: 200 }).then(unwrap)]); await loadStudentLabels([...registrations.value, ...enrollments.value, ...audits.value]) } catch (error) { ElMessage.error(message(error)) } }
const searchStudents = async keyword => { students.value = unwrap(await listEducationStudents({ page: 0, size: 10, keyword })) }
const saveProgram = async () => { try { await createAcademicProgram({ schoolId: schoolId.value, ...programForm }); programDialog.value = false; ElMessage.success('培养方案草稿已创建'); await reload() } catch (error) { ElMessage.error(message(error)) } }
const openCourse = row => { activeProgram.value = row; Object.assign(courseForm, { courseId: '', suggestedTermNo: 1, courseNature: 'REQUIRED', credits: 0, prerequisiteCourseIds: [] }); courseDialog.value = true }
const saveCourse = async () => { try { await addAcademicProgramCourse(activeProgram.value.id, courseForm); courseDialog.value = false; ElMessage.success('课程已加入培养方案'); await reload() } catch (error) { ElMessage.error(message(error)) } }
const submitProgram = async row => { try { await ElMessageBox.confirm('提交后方案将不可继续编辑，确认提交审核？', '提交确认'); await submitAcademicProgram(row.id); ElMessage.success('培养方案已提交'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const publishProgram = async row => { try { await ElMessageBox.confirm('发布后将归档同专业同年级旧方案，确认发布？', '发布确认', { type: 'warning' }); await publishAcademicProgram(row.id); ElMessage.success('培养方案已发布'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openRegistration = async () => { await searchStudents(''); registrationDialog.value = true }
const saveRegistration = async () => { try { await submitTermRegistration({ schoolId: schoolId.value, termCode: termCode.value, ...registrationForm }); registrationDialog.value = false; ElMessage.success('学期注册已提交审核'); await reloadTerm() } catch (error) { ElMessage.error(message(error)) } }
const decideRegistrationRow = async (row, approved) => { try { let reason = ''; if (!approved) reason = (await ElMessageBox.prompt('填写驳回原因', '注册审核')).value; await decideTermRegistration(row.id, { approved, reason }); ElMessage.success(approved ? '注册已确认' : '注册已驳回'); await reloadTerm() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openEnrollment = async () => { await searchStudents(''); enrollmentDialog.value = true }
const saveEnrollment = async () => { try { await enrollCourse({ schoolId: schoolId.value, termCode: termCode.value, ...enrollmentForm, source: 'MANUAL' }); enrollmentDialog.value = false; ElMessage.success('选课成功'); await reloadTerm() } catch (error) { ElMessage.error(message(error)) } }
const withdraw = async row => { try { const reason = (await ElMessageBox.prompt('填写退课原因', '退课确认', { type: 'warning' })).value; await withdrawCourse(row.id, reason); ElMessage.success('退课成功'); await reloadTerm() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
const openGraduation = async () => { await searchStudents(''); graduationDialog.value = true }
const saveGraduation = async () => { try { await evaluateGraduation({ schoolId: schoolId.value, ...graduationForm }); graduationDialog.value = false; ElMessage.success('毕业资格核验已生成'); await reload() } catch (error) { ElMessage.error(message(error)) } }
const decideGraduationRow = async (row, approved) => { try { let reason = ''; if (!approved) reason = (await ElMessageBox.prompt('填写驳回原因', '毕业审核')).value; await decideGraduation(row.id, { approved, reason }); ElMessage.success(approved ? '毕业资格已批准' : '毕业审核已驳回'); await reload() } catch (error) { if (!['cancel','close'].includes(error)) ElMessage.error(message(error)) } }
onMounted(async () => { try { const [orgs, termResult, majorResult, courseResult] = await Promise.all([orgList({ page: 0, size: 500 }), listAcademicTerms({ page: 0, size: 100 }), listEducationMajors({ page: 0, size: 500 }), listCourseCatalog({ page: 0, size: 500 })]); schools.value = unwrap(orgs).filter(item => (item.organizationType || item.orgType) === 'SCHOOL'); terms.value = unwrap(termResult); majors.value = unwrap(majorResult); courses.value = unwrap(courseResult); schoolId.value = schools.value[0]?.id || ''; termCode.value = (terms.value.find(item => item.currentTerm) || terms.value[0])?.termCode || ''; await reload() } catch (error) { ElMessage.error(message(error)) } })
</script>

<style scoped>
.page { padding: 24px; } header { margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #7c8793; } .query-bar { display: flex; gap: 10px; align-items: center; margin: 16px 0 10px; } .tab-actions { display: flex; justify-content: flex-end; margin-bottom: 10px; }
</style>
