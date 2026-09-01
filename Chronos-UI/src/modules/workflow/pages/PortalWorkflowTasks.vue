<template>
  <div class="task-page">
    <div class="title"><div><h2>流程任务</h2><p>集中处理待办、查看已办和我发起的流程</p></div><el-button type="primary" @click="$router.push('/portal/workflows')">发起流程</el-button></div>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="待办" name="pending"><el-table :data="pending" v-loading="loading"><el-table-column prop="nodeName" label="当前节点" /><el-table-column prop="createTime" label="到达时间" width="180" /><el-table-column prop="dueAt" label="办理期限" width="180" /><el-table-column label="操作" width="420"><template #default="{row}"><el-button link type="primary" @click="$router.push(`/portal/workflow-instances/${row.instanceId}/forms`)">表单</el-button><el-button link type="success" @click="complete(row,true)">通过</el-button><el-button link type="danger" @click="complete(row,false)">拒绝</el-button><el-button link type="warning" @click="remind(row)">催办</el-button><el-dropdown @command="command=>operate(row,command)"><el-button link>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="return">退回</el-dropdown-item><el-dropdown-item command="transfer">转办</el-dropdown-item><el-dropdown-item command="add-sign">加签</el-dropdown-item><el-dropdown-item command="cc">抄送</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="已办" name="handled"><el-table :data="handled" v-loading="loading"><el-table-column prop="nodeName" label="节点" /><el-table-column prop="status" label="结果" width="130" /><el-table-column prop="comment" label="意见" /><el-table-column prop="completedAt" label="完成时间" width="190" /></el-table></el-tab-pane>
      <el-tab-pane label="我发起的" name="initiated"><el-table :data="initiated" v-loading="loading"><el-table-column prop="businessKey" label="业务编号" /><el-table-column prop="status" label="状态" width="130" /><el-table-column prop="currentNodeKey" label="当前节点" /><el-table-column prop="createTime" label="发起时间" width="190" /><el-table-column label="操作" width="150"><template #default="{row}"><el-button link type="primary" @click="$router.push(`/portal/workflow-instances/${row.id}/forms`)">查看</el-button><el-button v-if="row.status==='RUNNING'" link type="danger" @click="withdraw(row)">撤回</el-button></template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { onMounted,ref } from 'vue';import { ElMessage,ElMessageBox } from 'element-plus';import { pendingWorkflowTasks,handledWorkflowTasks,initiatedWorkflowInstances,completeWorkflowTask,transferWorkflowTask,addSignWorkflowTask,ccWorkflowTask,returnWorkflowTask,withdrawWorkflowInstance,remindWorkflowTask } from '../../../api/admin'
const tab=ref('pending'),pending=ref([]),handled=ref([]),initiated=ref([]),loading=ref(false)
const load=async()=>{loading.value=true;try{if(tab.value==='pending')pending.value=(await pendingWorkflowTasks())?.data||[];else if(tab.value==='handled')handled.value=(await handledWorkflowTasks())?.data||[];else initiated.value=(await initiatedWorkflowInstances())?.data||[]}finally{loading.value=false}}
const prompt=async(title,needUser=false)=>{const {value}=await ElMessageBox.prompt(needUser?'请输入目标用户账号':'请输入处理意见',title,{inputPlaceholder:needUser?'用户名':'意见（可选）',inputValidator:v=>!needUser||!!v});return value||''}
const complete=async(row,approved)=>{const comment=await prompt(approved?'审批通过':'审批拒绝');await completeWorkflowTask(row.id,{approved,comment});ElMessage.success('处理成功');await load()}
const operate=async(row,type)=>{if(type==='return'){const comment=await prompt('退回上一节点');await returnWorkflowTask(row.id,{comment})}else{const assignee=await prompt(type==='transfer'?'转办':'选择人员',true);const api=type==='transfer'?transferWorkflowTask:type==='add-sign'?addSignWorkflowTask:ccWorkflowTask;await api(row.id,{assignee})}ElMessage.success('操作成功');await load()}
const withdraw=async(row)=>{await ElMessageBox.confirm('确认撤回该流程？','撤回流程',{type:'warning'});await withdrawWorkflowInstance(row.id,{});ElMessage.success('已撤回');await load()}
const remind=async(row)=>{await remindWorkflowTask(row.id);ElMessage.success('催办记录已发送')}
onMounted(load)
</script>
<style scoped>.task-page{background:#fff;border-radius:12px;padding:24px}.title{display:flex;justify-content:space-between;align-items:center}.title h2{margin:0}.title p{color:#8492a6;margin:8px 0 20px}</style>
