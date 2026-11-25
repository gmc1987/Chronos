<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveSupervisionPlan, completeSupervisionPlan, createSupervisionAssignment, createSupervisionIssue, createSupervisionPlan, listSupervisionIssues, listSupervisionPlans, listSupervisionRecords, publishSupervisionPlan, reviewSupervisionIssue, startSupervisionPlan } from '../../../api/admin'
import { createSupervisionObservation, supervisionObservationTeachers, supervisionAssignmentSupervisors, supervisionAssignmentOfferings, supervisionAssignmentSchedules } from '../../../api/admin'
import { getDeliveryContext, hasAdminPermission } from '../../../store/auth'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import SupervisionTeacherSetup from '../components/SupervisionTeacherSetup.vue'

const customer = getDeliveryContext().mode === 'customer'
const canManage = !customer || hasAdminPermission('education:supervision:plan:create')
const teachers = ref([])
const supervisors = ref([])
const organizationSelect = ref(null)
const schoolId = ref('')
const schoolOptions = computed(() => organizationSelect.value?.options || [])
const schoolName = id => schoolOptions.value.find(option => option.id === id)?.organizationName
  || (id === 'ALL' ? '历史未指定机构' : '—')
const assignmentLoading = ref(false)
let listVersion = 0
let scheduleVersion = 0
const observation = ref({ course: '', location: '', startsAt: '', endsAt: '' })

const plans = ref([])
const offerings = ref([])
const schedules = ref([])
const records = ref([])
const issues = ref([])
const loading = ref(false)
const dialog = ref(false)
const assignmentDialog = ref(false)
const issueDialog = ref(false)
const reviewDialog = ref(false)
const draft = ref({ schoolId: '', name: '', startDate: '', endDate: '' })
const assignment = ref({ planId: '', supervisorId: '', offeringId: '', teacherId: '', scheduleEntryId: '' })
const issue = ref({ recordId: '', severity: 'NORMAL', title: '', description: '', ownerId: '', dueAt: '' })
const review = ref({ issueId: '', approved: true, comment: '' })
const unwrap = response => response?.data?.content || response?.data || []
const offeringLabel = item => `${item.semesterCode || ''} · ${item.courseName || ''} · ${item.teachingClassName || ''} · ${item.teacherName || ''}`
const scheduleLabel = item => `${item.weekday || item.dayOfWeek || ''} ${item.period || item.periodNo || ''} · ${item.courseName || item.location || item.id}`

async function load() {
  const selected = schoolId.value
  const version = ++listVersion
  loading.value = true
  try {
    const [planResult, recordResult, issueResult] = await Promise.all([
      listSupervisionPlans(selected), canManage && selected ? listSupervisionRecords(selected) : null,
      canManage && selected ? listSupervisionIssues(selected) : null
    ])
    if (version !== listVersion) return
    plans.value = unwrap(planResult)
    records.value = unwrap(recordResult)
    issues.value = unwrap(issueResult)
  }
  catch (cause) { if (version === listVersion) ElMessage.error(cause?.message || '督导计划加载失败') }
  finally { if (version === listVersion) loading.value = false }
}
function openPlan() {
  draft.value = { schoolId: schoolId.value, name: '', startDate: '', endDate: '' }
  dialog.value = true
}

async function save() {
  if (!draft.value.schoolId) return ElMessage.warning('请选择督导计划所属机构')
  try {
    await createSupervisionPlan(draft.value)
    dialog.value = false
    ElMessage.success('督导计划已保存')
    if (schoolId.value === draft.value.schoolId) await load()
    else schoolId.value = draft.value.schoolId
  } catch (cause) { ElMessage.error(cause?.message || '督导计划保存失败') }
}
async function transition(row) {
  try {
    if (row.status === 'DRAFT') await publishSupervisionPlan(row.id)
    else if (row.status === 'PUBLISHED') await startSupervisionPlan(row.id)
    else if (row.status === 'IN_PROGRESS') await completeSupervisionPlan(row.id)
    else if (row.status === 'COMPLETED') await archiveSupervisionPlan(row.id)
    ElMessage.success('计划状态已更新')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '状态更新失败') }
}
async function openAssignment(row) {
  ++scheduleVersion
  assignment.value = { planId: row.id, schoolId: row.schoolId, supervisorId: '', offeringId: '', teacherId: '', scheduleEntryId: '' }
  schedules.value = []
  supervisors.value = []
  teachers.value = []
  offerings.value = []
  observation.value = { course: '', location: '', startsAt: '', endsAt: '' }
  assignmentDialog.value = true
  assignmentLoading.value = true
  try {
    const [accountResult, lessonResult] = await Promise.all([
      supervisionAssignmentSupervisors(row.id),
      customer ? supervisionObservationTeachers(row.id) : supervisionAssignmentOfferings(row.id),
    ])
    if (assignment.value.planId !== row.id) return
    supervisors.value = unwrap(accountResult)
    if (customer) teachers.value = unwrap(lessonResult)
    else offerings.value = unwrap(lessonResult)
  } catch (cause) { ElMessage.error(cause?.message || '本校督导分派选项加载失败') }
  finally { if (assignment.value.planId === row.id) assignmentLoading.value = false }
}

async function selectOffering(id) {
  const version = ++scheduleVersion
  assignment.value.teacherId = offerings.value.find(item => item.id === id)?.teacherId || ''
  assignment.value.scheduleEntryId = ''
  schedules.value = []
  if (!id) return
  try {
    const result = await supervisionAssignmentSchedules(assignment.value.planId, id)
    if (scheduleVersion === version) schedules.value = unwrap(result)
  } catch (cause) { if (scheduleVersion === version) ElMessage.error(cause?.message || '已发布课表加载失败') }
}
async function saveAssignment() {
  const value = assignment.value
  if (!value.supervisorId || !value.teacherId || (!customer && !value.scheduleEntryId)) {
    ElMessage.warning(customer ? '请选择督导账号和授课教师' : '请选择督导账号、教学班和已发布课次')
    return
  }
  try {
    const base = { planId: value.planId, supervisorId: value.supervisorId, teacherId: value.teacherId }
    if (customer) await createSupervisionObservation({ ...base, ...observation.value })
    else await createSupervisionAssignment({ ...base, scheduleEntryId: value.scheduleEntryId })
    assignmentDialog.value = false
    ElMessage.success('督导任务已分配')
  } catch (cause) { ElMessage.error(cause?.message || '任务分配失败') }
}
function openIssue(row) {
  issue.value = { recordId: row.id, severity: 'NORMAL', title: '', description: '', ownerId: '', dueAt: '' }
  issueDialog.value = true
}
async function saveIssue() {
  if (!issue.value.title.trim() || !issue.value.ownerId.trim()) {
    ElMessage.warning('请填写问题标题和整改负责人登录账号')
    return
  }
  try {
    await createSupervisionIssue(issue.value.recordId, {
      severity: issue.value.severity,
      title: issue.value.title.trim(),
      description: issue.value.description,
      ownerId: issue.value.ownerId.trim(),
      dueAt: issue.value.dueAt || null
    })
    issueDialog.value = false
    ElMessage.success('督导问题已登记')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '登记问题失败') }
}
function openReview(row) {
  review.value = { issueId: row.id, approved: true, comment: '' }
  reviewDialog.value = true
}
async function saveReview() {
  try {
    await reviewSupervisionIssue(review.value.issueId, {
      approved: review.value.approved,
      comment: review.value.comment
    })
    reviewDialog.value = false
    ElMessage.success(review.value.approved ? '整改复查已通过' : '整改已退回')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '复查失败') }
}
watch(schoolId, () => {
  plans.value = []
  records.value = []
  issues.value = []
  load()
}, { immediate: true })
</script>

<template>
  <section class="page">
    <header><div><h1>督导中心</h1><p>管理督导计划、评价记录与整改复查。</p></div><div><el-button @click="load">刷新</el-button><SupervisionTeacherSetup v-if="customer && canManage" /><el-button v-if="canManage" type="primary" @click="openPlan">新建计划</el-button></div></header>
    <OrganizationSelect ref="organizationSelect" v-model="schoolId" school-only
      placeholder="按所属机构查询" style="width: 280px; margin-bottom: 12px" />
    <el-table v-loading="loading" :data="plans" border>
      <el-table-column prop="name" label="计划名称" min-width="220" />
      <el-table-column label="所属机构" min-width="160">
        <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始日期" />
      <el-table-column prop="endDate" label="结束日期" />
      <el-table-column prop="status" label="状态" />
      <AdaptiveActionColumn v-if="canManage" label="操作" width="180"><template #default="{ row }">
        <el-button v-if="['DRAFT', 'PUBLISHED', 'IN_PROGRESS', 'COMPLETED'].includes(row.status)" link type="primary" @click="transition(row)">{{ { DRAFT: '发布', PUBLISHED: '开始', IN_PROGRESS: '完成', COMPLETED: '归档' }[row.status] }}</el-button>
        <el-button v-if="row.status === 'PUBLISHED' && row.schoolId && row.schoolId !== 'ALL'" link type="primary" @click="openAssignment(row)">分配任务</el-button>
      </template></AdaptiveActionColumn>
    </el-table>
    <el-empty v-if="!loading && !plans.length" description="暂无督导计划" />
    <h2>督导评价</h2>
    <el-table :data="records" border>
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
      <el-table-column prop="supervisorId" label="督导账号" min-width="160" />
      <el-table-column prop="teacherId" label="授课教师" min-width="180" />
      <AdaptiveActionColumn label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openIssue(row)">登记问题</el-button></template></AdaptiveActionColumn>
    </el-table>
    <h2>整改与复查</h2>
    <el-table :data="issues" border>
      <el-table-column prop="title" label="问题" min-width="220" />
      <el-table-column prop="ownerId" label="整改负责人" min-width="160" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="dueAt" label="截止时间" width="180" />
      <AdaptiveActionColumn label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === 'REVIEWING'" link type="primary" @click="openReview(row)">复查</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="dialog" title="新建督导计划" width="520px"><el-form label-width="90px"><el-form-item label="所属机构" required>
      <el-select v-model="draft.schoolId" filterable placeholder="选择督导计划所属机构" style="width: 100%">
        <el-option v-for="option in schoolOptions" :key="option.id" :label="option.organizationName" :value="option.id" />
      </el-select>
    </el-form-item><el-form-item label="名称"><el-input v-model="draft.name" maxlength="200" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="draft.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="draft.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="assignmentDialog" title="分配督导任务" width="640px">
      <el-form label-width="110px">
        <el-form-item label="所属机构"><el-input :model-value="schoolName(assignment.schoolId)" disabled /></el-form-item>
        <el-form-item label="督导登录账号" required>
          <el-select v-model="assignment.supervisorId" filterable :loading="assignmentLoading"
            placeholder="选择本校督导账号" no-data-text="本校暂无有督导权限且已绑定教师档案的账号">
            <el-option v-for="account in supervisors" :key="account.username" :value="account.username"
              :label="`${account.name}（${account.username}）`" />
          </el-select>
        </el-form-item>
        <template v-if="customer">
          <el-form-item label="授课教师">
            <el-select v-model="assignment.teacherId" filterable placeholder="选择本校教师">
              <el-option v-for="option in teachers" :key="option.id" :value="option.id" :label="option.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程名称"><el-input v-model="observation.course" maxlength="150" /></el-form-item>
          <el-form-item label="上课地点"><el-input v-model="observation.location" maxlength="150" /></el-form-item>
          <el-form-item label="课次开始"><el-date-picker v-model="observation.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
          <el-form-item label="课次结束"><el-date-picker v-model="observation.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        </template>
        <template v-else>
        <el-form-item label="教学班"><el-select v-model="assignment.offeringId" filterable style="width:100%" placeholder="选择教学班" @change="selectOffering"><el-option v-for="item in offerings" :key="item.id" :label="offeringLabel(item)" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="授课教师"><el-input :model-value="offerings.find(item => item.id === assignment.offeringId)?.teacherName || assignment.teacherId" disabled /></el-form-item>
        <el-form-item label="已发布课次"><el-select v-model="assignment.scheduleEntryId" filterable style="width:100%" placeholder="选择已发布课次"><el-option v-for="item in schedules" :key="item.id" :label="scheduleLabel(item)" :value="item.id" /></el-select></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="assignmentDialog = false">取消</el-button><el-button type="primary"  :disabled="assignmentLoading || !assignment.supervisorId" @click="saveAssignment">确认分配</el-button></template>
    </el-dialog>
    <el-dialog v-model="issueDialog" title="登记督导问题" width="600px">
      <el-form label-width="110px">
        <el-form-item label="严重程度"><el-select v-model="issue.severity"><el-option label="一般" value="NORMAL" /><el-option label="严重" value="CRITICAL" /></el-select></el-form-item>
        <el-form-item label="问题标题"><el-input v-model="issue.title" maxlength="200" /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="issue.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="整改负责人"><el-input v-model="issue.ownerId" placeholder="输入授课教师登录账号" /></el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="issue.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="issueDialog = false">取消</el-button><el-button type="primary" @click="saveIssue">登记</el-button></template>
    </el-dialog>
    <el-dialog v-model="reviewDialog" title="整改复查" width="520px">
      <el-form label-width="100px">
        <el-form-item label="复查结论"><el-radio-group v-model="review.approved"><el-radio :value="true">通过</el-radio><el-radio :value="false">退回整改</el-radio></el-radio-group></el-form-item>
        <el-form-item label="复查意见"><el-input v-model="review.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewDialog = false">取消</el-button><el-button type="primary" @click="saveReview">提交复查</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h1 { margin: 0 0 6px; } h2 { margin: 24px 0 12px; font-size: 18px; } p { color: #84909a; }</style>
