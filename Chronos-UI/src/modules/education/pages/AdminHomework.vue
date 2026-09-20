<template>
  <section class="homework-page">
    <header class="head">
      <div>
        <h1>作业管理</h1>
        <p>从教务中心选择本人负责的教学班，完成作业布置、提交查看、批改和评分。</p>
      </div>
      <el-button type="primary" :disabled="!filters.offeringId" @click="openCreate">新建作业</el-button>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-form inline class="filters">
      <el-form-item label="教学班">
        <el-select v-model="filters.offeringId" clearable filterable placeholder="选择负责的教学班" @change="loadReferences(); load()">
          <el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="offeringLabel(item)" />
        </el-select>
      </el-form-item>
      <el-button @click="load">查询</el-button>
    </el-form>

    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="title" label="作业名称" min-width="220" />
      <el-table-column prop="instructionsJson" label="说明" min-width="240" show-overflow-tooltip />
      <el-table-column prop="dueAt" label="截止时间" width="180" />
      <el-table-column prop="maxScore" label="总分" width="80" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="操作" width="410" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link @click="showSubmissions(row)">提交情况</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="close(row)">关闭</el-button>
          <el-button v-if="['PUBLISHED','CLOSED'].includes(row.status)" link type="success" @click="publishGrades(row)">发布成绩</el-button>
          <el-button v-if="row.status === 'CLOSED'" link type="info" @click="archive(row)">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="当前教学班暂无作业" />

    <el-dialog v-model="dialog" :title="editing ? '编辑作业' : '新建作业'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="教学班"><el-input :model-value="offeringLabel(selectedOffering)" disabled /></el-form-item>
        <el-form-item label="作业名称" prop="title"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="作业类型"><el-select v-model="form.type" style="width:100%"><el-option label="作业" value="HOMEWORK" /><el-option label="测验" value="QUIZ" /><el-option label="项目" value="PROJECT" /></el-select></el-form-item>
        <el-form-item label="作业说明"><el-input v-model="form.instructionsJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="截止时间" prop="dueAt"><el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="总分" prop="maxScore"><el-input-number v-model="form.maxScore" :min="1" :precision="0" /></el-form-item>
        <el-form-item label="提交次数"><el-input-number v-model="form.attemptLimit" :min="1" :max="100" :precision="0" /></el-form-item>
        <el-form-item label="教学计划项"><el-select v-model="form.teachingPlanItemId" clearable filterable style="width:100%" placeholder="可选：选择当前教学班章节"><el-option v-for="item in planItems" :key="item.id" :value="item.id" :label="`${item.chapterNo || ''} · ${item.chapterName}`" /></el-select></el-form-item>
        <el-form-item label="备课记录"><el-select v-model="form.preparationId" clearable filterable style="width:100%" placeholder="可选：选择当前教学班备课"><el-option v-for="item in preparations" :key="item.id" :value="item.id" :label="item.title" /></el-select></el-form-item>
        <el-form-item label="教案"><el-select v-model="form.lessonPlanId" clearable filterable style="width:100%" placeholder="可选：选择当前教学班教案"><el-option v-for="item in lessonPlans" :key="item.id" :value="item.id" :label="item.title" /></el-select></el-form-item>
        <el-form-item label="题目/附件快照"><el-input v-model="form.questionSnapshotJson" type="textarea" :rows="6" placeholder="可填写题目、要求或附件引用 JSON；发布后形成快照" /></el-form-item>
        <el-form-item label="已发布题目版本"><el-input v-model="form.questionVersionRefsJson" type="textarea" :rows="3" placeholder='必须填写 [{"questionId":"...","versionId":"...","maxScore":10}]' /></el-form-item>
        <el-form-item label="允许迟交"><el-switch v-model="form.allowLate" /></el-form-item>
        <el-form-item label="发布对象"><el-select v-model="form.publishAudience" style="width:100%"><el-option label="有效选课学生" value="ENROLLED_STUDENTS" /><el-option label="全体学生" value="ALL_STUDENTS" /></el-select></el-form-item>
        <el-form-item label="附件快照"><el-input v-model="form.attachmentSnapshotJson" type="textarea" :rows="2" placeholder="附件引用 JSON" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="submissionDialog" title="学生提交与批改" width="980px">
      <el-table v-loading="submissionLoading" :data="submissions" stripe border>
        <el-table-column prop="studentId" label="学生" width="220" />
        <el-table-column prop="status" label="提交状态" width="150" />
        <el-table-column prop="submittedAt" label="提交时间" width="180" />
        <el-table-column prop="score" label="得分" width="90" />
        <el-table-column prop="teacherFeedback" label="评语" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :disabled="!['SUBMITTED','RETURNED_FOR_REVISION'].includes(row.status)" @click="openGrade(row)">批改</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!submissionLoading && !submissions.length" description="暂无学生提交" />
    </el-dialog>

    <el-dialog v-model="gradeDialog" title="批改作业" width="620px">
      <el-form :model="gradeForm" label-width="90px">
        <el-form-item label="学生"><el-input :model-value="grading?.studentId || '-'" disabled /></el-form-item>
        <el-form-item label="答案"><el-input :model-value="grading?.answerSnapshotJson || '-'" type="textarea" :rows="6" disabled /></el-form-item>
        <el-form-item label="得分"><el-input-number v-model="gradeForm.score" :min="0" :max="selectedHomework?.maxScore || 100" :precision="2" /></el-form-item>
        <el-form-item label="逐题得分"><el-input v-model="gradeForm.questionScoresJson" type="textarea" :rows="3" placeholder='按题目 ID 填写，例如 {"question-id":8}' /></el-form-item>
        <el-form-item label="评语"><el-input v-model="gradeForm.feedback" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="处理"><el-radio-group v-model="gradeForm.result"><el-radio value="GRADED">完成评分</el-radio><el-radio value="RETURNED_FOR_REVISION">退回重做</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="gradeDialog=false">取消</el-button><el-button type="primary" @click="grade">保存批改</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createHomework, gradeHomeworkSubmission, homeworkPage, homeworkSubmissions, publishHomework, publishHomeworkGrades, closeHomework, archiveHomework, teachingCenterOfferings, teachingProduction, teachingPlanDetail, updateHomework } from '../api/teachingCenter'

const offerings = ref([])
const rows = ref([])
const submissions = ref([])
const loading = ref(false)
const submissionLoading = ref(false)
const loadError = ref('')
const dialog = ref(false)
const submissionDialog = ref(false)
const gradeDialog = ref(false)
const editing = ref('')
const selectedHomework = ref(null)
const grading = ref(null)
const formRef = ref()
const planItems = ref([])
const preparations = ref([])
const lessonPlans = ref([])
const filters = reactive({ offeringId: '' })
const form = reactive({ type: 'HOMEWORK', title: '', instructionsJson: '', dueAt: '', startAt: '', maxScore: 100, attemptLimit: 1, questionSnapshotJson: '[]', questionVersionRefsJson: '[]', attachmentSnapshotJson: '[]', teachingPlanItemId: '', preparationId: '', lessonPlanId: '', allowLate: false, lateRule: 'REJECT', publishAudience: 'ENROLLED_STUDENTS' })
const gradeForm = reactive({ score: 0, feedback: '', questionScoresJson: '{}', result: 'GRADED' })
const rules = { title: [{ required: true, message: '请输入作业名称' }], dueAt: [{ required: true, message: '请选择截止时间' }] }
const unwrap = response => response?.data?.content || response?.data || []
const offeringLabel = item => item ? [item.semesterCode, item.courseName, item.teachingClassName].filter(Boolean).join(' · ') : '-'
const selectedOffering = computed(() => offerings.value.find(item => item.id === filters.offeringId))

const load = async () => {
  if (!filters.offeringId) { rows.value = []; return }
  loading.value = true
  loadError.value = ''
  try { rows.value = unwrap(await homeworkPage({ offeringId: filters.offeringId, page: 0, size: 100 })) }
  catch (error) { loadError.value = error.message || '作业加载失败' }
  finally { loading.value = false }
}
const resetForm = value => Object.assign(form, { type: 'HOMEWORK', title: '', instructionsJson: '', dueAt: '', startAt: '', maxScore: 100, attemptLimit: 1, questionSnapshotJson: '[]', questionVersionRefsJson: '[]', attachmentSnapshotJson: '[]', teachingPlanItemId: '', preparationId: '', lessonPlanId: '', allowLate: false, lateRule: 'REJECT', publishAudience: 'ENROLLED_STUDENTS' }, value || {})
const openCreate = async () => { editing.value = ''; resetForm(); await loadReferences(); dialog.value = true }
const loadReferences = async () => {
  if (!filters.offeringId) { planItems.value = []; preparations.value = []; lessonPlans.value = []; return }
  try {
    const response = await teachingProduction(filters.offeringId)
    const data = response.data || {}
    preparations.value = data.preparations || []
    lessonPlans.value = data.lessons || []
    const plans = data.plans || []
    const details = await Promise.all(plans.map(plan => teachingPlanDetail(plan.id)))
    planItems.value = details.flatMap(detail => detail.data?.items || [])
  } catch (error) {
    planItems.value = []; preparations.value = []; lessonPlans.value = []
    ElMessage.error(error.message || '教学生产资源加载失败')
  }
}
const edit = async row => { editing.value = row.id; resetForm(row); await loadReferences(); dialog.value = true }
const save = async () => {
  try {
    await formRef.value.validate()
    const body = { ...form, offeringId: filters.offeringId }
    if (editing.value) await updateHomework(editing.value, body)
    else await createHomework(body)
    dialog.value = false; ElMessage.success('作业草稿已保存'); await load()
  } catch (error) { ElMessage.error(error.message || '保存失败') }
}
const publish = async row => {
  await ElMessageBox.confirm('发布后学生将可以查看并提交，是否继续？', '确认发布')
  try { await publishHomework(row.id); ElMessage.success('作业已发布'); await load() } catch (error) { ElMessage.error(error.message) }
}
const close = async row => {
  await ElMessageBox.confirm('关闭后不再接受新的提交，是否继续？', '确认关闭')
  try { await closeHomework(row.id); ElMessage.success('作业已关闭'); await load() } catch (error) { ElMessage.error(error.message) }
}
const publishGrades = async row => {
  await ElMessageBox.confirm('发布后学生可以查看成绩，并按逐题得分沉淀错题，是否继续？', '确认发布成绩')
  try {
    const response = await publishHomeworkGrades(row.id)
    ElMessage.success(`已发布 ${response?.data || 0} 份成绩`)
    await load()
  } catch (error) { ElMessage.error(error.message || '成绩发布失败') }
}
const archive = async row => {
  await ElMessageBox.confirm('归档后将从默认列表隐藏，是否继续？', '确认归档')
  try { await archiveHomework(row.id); ElMessage.success('作业已归档'); await load() } catch (error) { ElMessage.error(error.message) }
}
const showSubmissions = async row => {
  selectedHomework.value = row; submissionDialog.value = true; submissionLoading.value = true
  try { submissions.value = unwrap(await homeworkSubmissions(row.id, { page: 0, size: 200 })) } catch (error) { ElMessage.error(error.message) }
  finally { submissionLoading.value = false }
}
const openGrade = row => { grading.value = row; Object.assign(gradeForm, { score: row.score || 0, feedback: row.teacherFeedback || '', questionScoresJson: row.questionScoresJson || '{}', result: 'GRADED' }); gradeDialog.value = true }
const grade = async () => {
  try { await gradeHomeworkSubmission(grading.value.id, { score: gradeForm.score, teacherFeedback: gradeForm.feedback, questionScoresJson: gradeForm.questionScoresJson, returnForRevision: gradeForm.result === 'RETURNED_FOR_REVISION' }); gradeDialog.value = false; ElMessage.success('批改结果已保存'); await showSubmissions(selectedHomework.value) }
  catch (error) { ElMessage.error(error.message || '保存批改失败') }
}
onMounted(async () => {
  try { offerings.value = unwrap(await teachingCenterOfferings()); if (!filters.offeringId && offerings.value[0]) filters.offeringId = offerings.value[0].id; await loadReferences(); await load() }
  catch (error) { loadError.value = error.message || '教学班加载失败' }
})
</script>

<style scoped>
.homework-page{padding:24px}.head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:18px}.head h1{margin:0 0 6px}.head p{margin:0;color:#667085}.filters{margin:18px 0}.filters .el-select{width:360px}.el-pagination{margin-top:18px;justify-content:flex-end}.el-alert{margin-bottom:16px}
</style>
