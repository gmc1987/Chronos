<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { acceptSupervisionTask, checkInSupervisionTask, completeSupervisionTask, portalSupervisionTasks, saveSupervisionEvaluationForm, submitSupervisionEvaluation, supervisionEvaluationFields, supervisionEvaluationForms } from '../../../api/portal'

const tasks = ref([])
const loading = ref(false)
const error = ref('')
const checkInDialog = ref(false)
const selectedTaskId = ref('')
const proof = ref({ providerId: '', signedProof: '' })
const evaluationDialog = ref(false)
const evaluationForms = ref([])
const evaluationFields = ref([])
const evaluation = ref({ taskId: '', formTemplateId: '', values: {} })
const unwrap = response => response?.data?.content || response?.data || []

async function load() {
  loading.value = true
  error.value = ''
  tasks.value = []
  try {
    tasks.value = unwrap(await portalSupervisionTasks())
  } catch (cause) {
    error.value = cause?.message || '督导任务加载失败'
  } finally {
    loading.value = false
  }
}

async function accept(row) {
  try {
    await acceptSupervisionTask(row.id)
    ElMessage.success('已接受督导任务')
    await load()
  } catch (cause) {
    ElMessage.error(cause?.message || '接受任务失败')
  }
}

function openCheckIn(row) {
  selectedTaskId.value = row.id
  proof.value = { providerId: '', signedProof: '' }
  checkInDialog.value = true
}

async function checkIn() {
  if (!proof.value.providerId.trim() || !proof.value.signedProof.trim()) {
    ElMessage.warning('请填写凭证服务和现场签到凭证')
    return
  }
  try {
    await checkInSupervisionTask(selectedTaskId.value, proof.value)
    checkInDialog.value = false
    ElMessage.success('现场签到成功')
    await load()
  } catch (cause) {
    ElMessage.error(cause?.message || '签到失败，请核对凭证')
  }
}

async function openEvaluation(row) {
  evaluation.value = { taskId: row.id, formTemplateId: '', values: {} }
  evaluationFields.value = []
  try {
    evaluationForms.value = unwrap(await supervisionEvaluationForms())
    if (!evaluationForms.value.length) {
      ElMessage.warning('尚无已发布的督导评价表单，请管理员先发布表单')
      return
    }
    evaluationDialog.value = true
    await selectEvaluationForm(evaluationForms.value[0].id)
  } catch (cause) { ElMessage.error(cause?.message || '评价表单加载失败') }
}

async function selectEvaluationForm(formId) {
  evaluation.value.formTemplateId = formId
  evaluation.value.values = {}
  try { evaluationFields.value = unwrap(await supervisionEvaluationFields(formId)) }
  catch (cause) { ElMessage.error(cause?.message || '表单字段加载失败') }
}

async function saveEvaluation(draft) {
  try {
    await saveSupervisionEvaluationForm(evaluation.value.taskId, {
      formTemplateId: evaluation.value.formTemplateId,
      values: evaluation.value.values,
      draft
    })
    if (!draft) {
      await submitSupervisionEvaluation(evaluation.value.taskId, {
        formTemplateId: evaluation.value.formTemplateId
      })
    }
    evaluationDialog.value = false
    ElMessage.success(draft ? '评价草稿已保存' : '督导评价已提交')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '督导评价保存失败') }
}

async function complete(row) {
  try {
    await completeSupervisionTask(row.id)
    ElMessage.success('督导任务已完成')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '任务完成失败') }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header>
      <div><h1>我的督导任务</h1><p>现场签到须提交可信服务签发的凭证，签到时间由服务端记录。</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error" />
    <el-table v-loading="loading" :data="tasks" border>
      <el-table-column prop="teacherId" label="授课教师" />
      <el-table-column prop="scheduleEntryId" label="课表项" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="checkedInAt" label="签到时间" />
      <AdaptiveActionColumn label="操作" width="220">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="primary" @click="accept(row)">接受</el-button>
          <el-button v-if="row.status === 'ACCEPTED'" link type="primary" @click="openCheckIn(row)">现场签到</el-button>
          <el-button v-if="row.status === 'CHECKED_IN'" link type="primary" @click="openEvaluation(row)">填写评价</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click="complete(row)">完成任务</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-empty v-if="!loading && !error && !tasks.length" description="暂无已分配任务" />
    <el-dialog v-model="checkInDialog" title="现场签到" width="520px">
      <el-alert title="请从学校配置的可信签到服务获取凭证。未配置服务时，系统会拒绝签到。" type="info" :closable="false" class="error" />
      <el-form label-width="100px">
        <el-form-item label="凭证服务"><el-input v-model="proof.providerId" maxlength="64" /></el-form-item>
        <el-form-item label="签到凭证"><el-input v-model="proof.signedProof" type="textarea" :rows="4" maxlength="4096" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="checkInDialog = false">取消</el-button><el-button type="primary" @click="checkIn">验证并签到</el-button></template>
    </el-dialog>
    <el-dialog v-model="evaluationDialog" title="督导评价" width="640px">
      <el-form label-width="120px">
        <el-form-item label="评价模板"><el-select :model-value="evaluation.formTemplateId" style="width:100%" @change="selectEvaluationForm"><el-option v-for="item in evaluationForms" :key="item.id" :label="item.formName" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-for="field in evaluationFields" :key="field.id" :label="field.fieldLabel" :required="field.required">
          <el-input v-if="field.fieldType === 'TEXTAREA'" v-model="evaluation.values[field.fieldKey]" type="textarea" :rows="3" />
          <el-input-number v-else-if="field.fieldType === 'NUMBER'" v-model="evaluation.values[field.fieldKey]" style="width:100%" />
          <el-input v-else v-model="evaluation.values[field.fieldKey]" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="evaluationDialog = false">取消</el-button><el-button @click="saveEvaluation(true)">保存草稿</el-button><el-button type="primary" @click="saveEvaluation(false)">提交评价</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h1 { margin: 0 0 6px; }
p { color: #84909a; }
.error { margin-bottom: 16px; }
</style>
