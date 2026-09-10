<template>
  <div class="portal-page">
    <div class="page-title"><div><span>OA WORKFLOW</span><h1>发起流程</h1><p>选择业务流程并填写申请表单，提交后自动进入审批链路。</p></div><el-button @click="$router.push('/portal/tasks')">返回任务中心</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="runtime-layout" v-loading="loading">
      <el-card shadow="never" class="flow-list"><template #header><div class="card-title"><strong>可发起流程</strong><el-tag>{{ flows.length }}</el-tag></div></template>
        <button v-for="flow in flows" :key="flow.id" class="flow-item" :class="{active:selectedId===flow.id}" @click="selectFlow(flow.id)"><i>{{ flow.flowName.slice(0,1) }}</i><span><strong>{{ flow.flowName }}</strong><small>{{ flow.category || '未分类' }} · {{ flow.version }}</small></span></button>
        <el-empty v-if="!loading&&!flows.length" description="当前账号没有可发起的已发布流程" />
      </el-card>
      <el-card v-if="schema" shadow="never" class="form-card"><template #header><div><div class="card-title"><strong>{{ schema.flowName }}</strong><el-tag type="success">{{ selectedFlow?.version }}</el-tag></div><p>{{ schema.description || '请填写以下申请信息' }}</p></div></template>
        <el-form-item label="业务编号"><el-input v-model="businessKey" placeholder="不填写则由系统自动生成" /></el-form-item><h3 v-if="schema.formName">{{ schema.formName }}</h3><DynamicForm v-model="formData" :fields="schema.fields" @uploading-change="uploading = $event" />
        <div class="actions"><el-button @click="reset">重置</el-button><el-button type="primary" :loading="submitting" :disabled="uploading" @click="submit">{{ uploading ? '附件上传中' : '提交申请' }}</el-button></div>
      </el-card><el-empty v-else-if="!loading&&flows.length" description="请选择一个流程" />
    </div>
  </div>
</template>
<script setup>
import { computed,onMounted,ref } from 'vue';import { ElMessage } from 'element-plus';import { useRouter } from 'vue-router';import DynamicForm from '../components/DynamicForm.vue';import { listAvailableWorkflows,getWorkflowStartForm,startWorkflow } from '../../../api/admin'
const router=useRouter(),flows=ref([]),selectedId=ref(''),schema=ref(null),formData=ref({}),businessKey=ref(''),loading=ref(false),submitting=ref(false),uploading=ref(false),error=ref('')
const selectedFlow=computed(()=>flows.value.find(f=>f.id===selectedId.value))
const load=async()=>{loading.value=true;error.value='';try{const res=await listAvailableWorkflows();flows.value=res?.data||[];if(flows.value.length)await selectFlow(flows.value[0].id)}catch(e){error.value=`流程加载失败：${e?.message||'请求失败'}`}finally{loading.value=false}}
const selectFlow=async(id)=>{selectedId.value=id;schema.value=null;try{const res=await getWorkflowStartForm(id);schema.value=res?.data;reset()}catch(e){error.value=`表单加载失败：${e?.message||'请求失败'}`}}
const reset=()=>{formData.value={};businessKey.value='';(schema.value?.fields||[]).forEach(f=>{if(f.fieldType==='CHECKBOX')formData.value[f.fieldKey]=[];if(f.fieldType==='BOOLEAN')formData.value[f.fieldKey]=false})}
const submit = async () => {
  if (uploading.value) {
    return ElMessage.warning('请等待附件上传完成')
  }
  const missing = (schema.value?.fields || []).filter((field) =>
    field.required && (
      formData.value[field.fieldKey] === undefined ||
      formData.value[field.fieldKey] === null ||
      formData.value[field.fieldKey] === '' ||
      (Array.isArray(formData.value[field.fieldKey]) && !formData.value[field.fieldKey].length)
    )
  )
  if (missing.length) {
    return ElMessage.warning(`请填写：${missing.map((field) => field.fieldLabel).join('、')}`)
  }

  submitting.value = true
  try {
    const response = await startWorkflow(selectedId.value, {
      // 每次用户主动提交生成一个新键；HTTP 重试仍复用同一请求体，从而不会重复发起实例。
      idempotencyKey: crypto.randomUUID(),
      businessKey: businessKey.value,
      formData: formData.value,
      variablesJson: JSON.stringify(formData.value),
    })
    ElMessage.success('流程发起成功')
    if (response?.data?.id) {
      router.push(`/portal/workflow-instances/${response.data.id}/forms`)
    }
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>
<style scoped>.page-title{display:flex;justify-content:space-between;align-items:flex-start}.page-title span{font-size:11px;letter-spacing:1.8px;font-weight:800;color:#087b68}.page-title h1{margin:5px 0}.page-title p,.form-card p{margin:0;color:#8492a6}.runtime-layout{display:grid;grid-template-columns:300px minmax(500px,1fr);gap:18px}.card-title{display:flex;align-items:center;justify-content:space-between}.flow-list{min-height:480px}.flow-item{width:100%;display:flex;align-items:center;gap:12px;padding:13px;margin-bottom:8px;border:1px solid #e5e9ed;border-radius:12px;background:#fff;text-align:left;cursor:pointer}.flow-item.active{border-color:#087b68;background:#eef8f6}.flow-item i{width:38px;height:38px;display:grid;place-items:center;border-radius:10px;background:#e6f4f1;color:#087b68;font-style:normal;font-weight:800}.flow-item span{display:flex;flex-direction:column}.flow-item small{margin-top:4px;color:#8b98a3}.form-card h3{margin:22px 0}.actions{text-align:right;margin-top:20px}@media(max-width:900px){.runtime-layout{grid-template-columns:1fr}}</style>
