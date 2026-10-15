<template>
  <div class="portal-page"><el-page-header @back="$router.push('/portal/tasks')"><template #content><span>{{ bundle?.flow?.flowName || '流程详情' }}</span></template></el-page-header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-loading="loading" class="detail-grid" v-if="bundle">
      <main><el-card shadow="never" class="summary"><div><small>业务编号</small><strong>{{ bundle.instance?.businessKey }}</strong></div><div><small>发起人</small><strong>{{ bundle.instance?.initiator }}</strong></div><div><small>流程版本</small><strong>{{ versionInfo?.definitionVersion || bundle.flow?.version || '—' }}</strong></div><div><small>状态</small><el-tag :type="statusType(bundle.instance?.status)">{{ statusLabel(bundle.instance?.status) }}</el-tag></div></el-card>
        <el-card v-for="form in bundle.forms||[]" :key="form.formId" shadow="never" class="form-card"><template #header><strong>{{ form.formName }}</strong><el-tag class="role">{{ form.role==='MAIN'?'主表单':'附加表单' }}</el-tag><el-tag v-if="form.formVersion" class="role" type="info">{{ form.formVersion }}</el-tag></template><DynamicForm v-model="values[form.formId]" :fields="form.fields" :business-id="route.params.id" @uploading-change="setUploading(form.formId, $event)" /><div v-if="editable(form)" class="actions"><el-button :disabled="hasUploads" @click="save(form,true)">保存草稿</el-button><el-button type="primary" :disabled="hasUploads" @click="save(form,false)">提交表单</el-button></div></el-card>
        <el-card v-if="teachingPlanReview" shadow="never" class="form-card">
          <template #header><strong>本次教学计划版本</strong></template>
          <el-alert v-if="teachingPreviewError" :title="teachingPreviewError" type="error" :closable="false" />
          <template v-else-if="teachingPreview">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="计划名称">{{ teachingPreview.plan.name }}</el-descriptions-item>
              <el-descriptions-item label="提交版本">v{{ teachingPreview.versionNo }} · {{ teachingPreview.status }}</el-descriptions-item>
              <el-descriptions-item label="总课时">{{ teachingPreview.plan.totalHours }}</el-descriptions-item>
              <el-descriptions-item label="快照校验">{{ teachingPreview.snapshotHash }}</el-descriptions-item>
              <el-descriptions-item label="教学目标" :span="2">{{ teachingPreview.plan.objective }}</el-descriptions-item>
              <el-descriptions-item label="考核方式" :span="2">{{ teachingPreview.plan.assessmentMethod }}</el-descriptions-item>
            </el-descriptions>
            <el-table :data="teachingPreview.items" border class="chapter-preview">
              <el-table-column prop="chapterNo" label="章节" width="70" />
              <el-table-column prop="chapterName" label="章节名称" min-width="200" />
              <el-table-column label="周次" width="110"><template #default="{ row }">{{ row.weekStart }}–{{ row.weekEnd }}</template></el-table-column>
              <el-table-column prop="lessonHours" label="课时" width="80" />
              <el-table-column prop="objectives" label="目标" min-width="200" />
            </el-table>
          </template>
          <el-empty v-else description="正在读取提交版本" />
        </el-card>
        <el-card v-if="gradeChangeReview" shadow="never" class="form-card">
          <template #header><strong>本次成绩更正内容</strong></template>
          <el-alert v-if="gradeChangePreviewError" :title="gradeChangePreviewError" type="error" :closable="false" />
          <el-descriptions v-else-if="gradeChangePreview" :column="2" border>
            <el-descriptions-item label="学生">{{ gradeChangePreview.studentName }}（{{ gradeChangePreview.studentNo }}）</el-descriptions-item>
            <el-descriptions-item label="申请状态">{{ gradeChangeStatus(gradeChangePreview.status) }}</el-descriptions-item>
            <el-descriptions-item label="原成绩">{{ gradeChangePreview.beforeScore }}</el-descriptions-item>
            <el-descriptions-item label="拟更正成绩">{{ gradeChangePreview.afterScore }}</el-descriptions-item>
            <el-descriptions-item label="更正原因" :span="2">{{ gradeChangePreview.reason }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="正在读取成绩更正单" />
        </el-card>
        <el-empty v-if="!(bundle.forms||[]).length && !gradeChangeReview" description="当前节点没有关联表单" />
        <el-card v-if="bundle.currentTask" shadow="never" class="approval"><template #header><strong>{{ bundle.currentTask.taskKind==='STARTER_REWORK'?'修改后重新提交':'审批操作' }}</strong></template><el-input v-model="comment" type="textarea" :rows="3" placeholder="请输入处理意见" /><div class="actions"><el-button v-if="bundle.currentTask.taskKind==='STARTER_REWORK'&&canStart" type="primary" :disabled="hasUploads" @click="resubmit">重新提交</el-button><template v-else><el-button v-if="canReject&&bundle.operations?.reject!==false" type="danger" :disabled="hasUploads" @click="reject">拒绝</el-button><el-button v-if="canApprove&&bundle.operations?.approve!==false" type="primary" :disabled="hasUploads || (teachingPlanReview && !teachingPreview) || (gradeChangeReview && !gradeChangePreview)" @click="approve">同意</el-button></template></div></el-card>
      </main>
      <aside><el-card shadow="never"><template #header><strong>流程轨迹</strong></template><el-timeline><el-timeline-item v-for="item in bundle.history||[]" :key="item.id" :timestamp="item.completedAt||item.createTime" :type="historyType(item.status)"><strong>{{ item.nodeName }}</strong><p>{{ item.assignee }} · {{ taskStatus(item.status) }}</p><small v-if="item.comment">{{ item.comment }}</small></el-timeline-item></el-timeline><el-empty v-if="!(bundle.history||[]).length" description="暂无审批记录" /></el-card></aside>
    </div>
  </div>
</template>
<script setup>
import { computed,onMounted,ref } from 'vue';import { useRoute,useRouter } from 'vue-router';import { ElMessage } from 'element-plus';import DynamicForm from '../components/DynamicForm.vue';import { getWorkflowRuntimeForms,getWorkflowInstanceVersion,getGradeChangeReview,saveWorkflowRuntimeForm,completeWorkflowTask,rejectWorkflowTask,resubmitWorkflowTask } from '../../../api/admin';import { teachingPlanVersions } from '../../education/api/teachingCenter';import { hasAdminPermission } from '../../../store/auth'
const route=useRoute(),router=useRouter(),bundle=ref(null),values=ref({}),loading=ref(false),error=ref(''),comment=ref('')
const versionInfo = ref(null)
const teachingPlanReview = ref(false), teachingPreview = ref(null), teachingPreviewError = ref('')
const gradeChangeReview = ref(false), gradeChangePreview = ref(null), gradeChangePreviewError = ref('')
const uploadingForms=ref(new Set()),hasUploads=computed(()=>uploadingForms.value.size>0)
const setUploading=(formId,active)=>{const next=new Set(uploadingForms.value);if(active)next.add(formId);else next.delete(formId);uploadingForms.value=next}
const canApprove=hasAdminPermission('workflow:task:approve'),canReject=hasAdminPermission('workflow:task:reject'),canStart=hasAdminPermission('workflow:instance:start')
const load = async () => {
  loading.value = true
  error.value = ''
  teachingPlanReview.value = false
  teachingPreview.value = null
  teachingPreviewError.value = ''
  gradeChangeReview.value = false
  gradeChangePreview.value = null
  gradeChangePreviewError.value = ''
  try {
    const response = await getWorkflowRuntimeForms(route.params.id)
    bundle.value = response?.data
    for (const form of bundle.value?.forms || []) {
      values.value[form.formId] = { ...(form.data || {}) }
    }
    const reviewForm = (bundle.value?.forms || []).find(form => form.data?.resourceType === 'PLAN')
    if (bundle.value?.flow?.flowCode === 'EDU_TEACHING_CONTENT_REVIEW' && reviewForm?.data?.resourceId) {
      teachingPlanReview.value = true
      try {
        const response = await teachingPlanVersions(reviewForm.data.resourceId)
        const version = (response.data || []).find(item => item.status === 'SUBMITTED')
          || (response.data || [])[0]
        if (!version?.snapshotJson) throw new Error('提交版本快照不存在')
        const snapshot = JSON.parse(version.snapshotJson)
        teachingPreview.value = { ...snapshot, versionNo: version.versionNo, status: version.status, snapshotHash: version.snapshotHash }
      } catch (failure) {
        teachingPreviewError.value = `无法读取本次教学计划版本：${failure?.message || '请求失败'}`
      }
    }
    if (bundle.value?.flow?.flowCode === 'EDU_GRADE_CHANGE_REVIEW') {
      gradeChangeReview.value = true
      try {
        const response = await getGradeChangeReview(bundle.value.instance.businessKey, route.params.id)
        gradeChangePreview.value = response?.data || null
        if (!gradeChangePreview.value) throw new Error('成绩更正内容不存在')
      } catch (failure) {
        gradeChangePreviewError.value = `无法读取本次成绩更正：${failure?.message || '请求失败'}`
      }
    }
    // Version details are supplementary; they must never block task handling.
    try {
      const versionResponse = await getWorkflowInstanceVersion(route.params.id)
      versionInfo.value = versionResponse?.data || null
    } catch {
      versionInfo.value = null
    }
  } catch (failure) {
    error.value = `流程详情加载失败：${failure?.message || '请求失败'}`
  } finally {
    loading.value = false
  }
}
const editable=form=>bundle.value?.canEdit&&(form.fields||[]).some(f=>f.permission==='EDIT')
const editableData=form=>Object.fromEntries((form.fields||[]).filter(f=>f.permission==='EDIT').map(f=>[f.fieldKey,values.value[form.formId]?.[f.fieldKey]]))
const ensureUploaded=()=>{if(!hasUploads.value)return true;ElMessage.warning('请等待附件上传完成');return false}
const save=async(form,draft)=>{if(!ensureUploaded())return;await saveWorkflowRuntimeForm(route.params.id,form.formId,{data:editableData(form),draft});ElMessage.success(draft?'草稿已保存':'表单已提交');await load()}
const approve=async()=>{if(!ensureUploaded())return;for(const form of bundle.value.forms||[])if(editable(form))await saveWorkflowRuntimeForm(route.params.id,form.formId,{data:editableData(form),draft:false});await completeWorkflowTask(bundle.value.currentTask.id,{approved:true,comment:comment.value});ElMessage.success('审批已通过');router.push('/portal/tasks')}
const reject=async()=>{if(bundle.value.currentTask.rejectPolicy==='SELECTABLE')return ElMessage.warning('请选择从任务中心执行拒绝并指定退回节点');await rejectWorkflowTask(bundle.value.currentTask.id,{comment:comment.value});ElMessage.success('拒绝处理完成');router.push('/portal/tasks')}
const resubmit=async()=>{if(!ensureUploaded())return;for(const form of bundle.value.forms||[])if(editable(form))await saveWorkflowRuntimeForm(route.params.id,form.formId,{data:editableData(form),draft:false});await resubmitWorkflowTask(bundle.value.currentTask.id,{comment:comment.value});ElMessage.success('已重新提交');router.push('/portal/tasks')}
const statusLabel=s=>({RUNNING:'审批中',COMPLETED:'已完成',REJECTED:'已拒绝',WITHDRAWN:'已撤回'}[s]||s),statusType=s=>({COMPLETED:'success',REJECTED:'danger',WITHDRAWN:'info',RUNNING:'warning'}[s]||'info')
const gradeChangeStatus=s=>({REVIEWING:'审核中',APPROVED:'已批准',REJECTED:'已拒绝'}[s]||s)
const taskStatus=s=>({PENDING:'待处理',APPROVED:'已通过',REJECTED:'已拒绝',RETURNED:'已退回',CANCELLED:'已取消',COMPLETED:'已结束',CC:'已抄送'}[s]||s),historyType=s=>({APPROVED:'success',REJECTED:'danger',RETURNED:'warning',PENDING:'primary',CANCELLED:'info',CC:'warning'}[s]||'info')
onMounted(load)
</script>
<style scoped>.detail-grid{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:18px}.summary{margin-bottom:16px}.summary :deep(.el-card__body){display:grid;grid-template-columns:repeat(4,1fr);gap:18px}.summary div{display:flex;flex-direction:column;gap:7px}.summary small{color:#8492a6}.form-card,.approval{margin-bottom:16px}.role{margin-left:10px}.actions{text-align:right;margin-top:16px}.el-timeline p{margin:4px 0;color:#64748b}.el-timeline small{color:#8492a6}@media(max-width:1000px){.detail-grid{grid-template-columns:1fr}.summary :deep(.el-card__body){grid-template-columns:repeat(2,1fr)}}</style>
