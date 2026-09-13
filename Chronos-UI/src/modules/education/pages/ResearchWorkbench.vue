<template>
  <section class="workbench">
    <header class="head"><div><h1>教研工作台</h1><p>管理教研组、活动日历、签到请假、纪要与成果审核</p></div><el-button type="primary" @click="openCreate">新建{{ tabLabel }}</el-button></header>
    <el-tabs v-model="tab" @tab-change="load"><el-tab-pane name="groups" label="教研组" /><el-tab-pane name="activities" label="活动日历" /><el-tab-pane name="results" label="成果审核" /></el-tabs>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-form inline @submit.prevent="load"><el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="搜索名称或主题" /></el-form-item><el-button @click="load">查询</el-button></el-form>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="name" label="名称/主题" min-width="220" />
      <el-table-column v-if="tab==='groups'" prop="leaderTeacherName" label="组长" width="140" />
      <el-table-column v-if="tab==='activities'" prop="activityTime" label="活动时间" width="180" />
      <el-table-column v-if="tab==='activities'" prop="location" label="地点" width="160" />
      <el-table-column v-if="tab==='results'" prop="resultType" label="成果类型" width="150"><template #default="{row}">{{ labelOf(resultTypes,row.resultType) }}</template></el-table-column>
      <el-table-column label="状态" width="130"><template #default="{row}">{{ labelOf(statuses,row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="300" fixed="right"><template #default="{row}">
        <el-button link @click="edit(row)">编辑</el-button><el-button v-if="tab==='activities'" link @click="openAttendance(row)">签到/请假</el-button><el-button v-if="tab==='activities'" link @click="openMinutes(row)">纪要</el-button><el-button v-if="tab==='results'" link type="success" :disabled="!canSubmit(row)" @click="submit(row)">提交审核</el-button>
      </template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无数据" />
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" @change="load" />
    <el-dialog v-model="dialog" :title="`${editing?'编辑':'新建'}${tabLabel}`" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="tab==='groups'?'组名':tab==='activities'?'主题':'成果名称'" prop="name"><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item v-if="tab==='groups'" label="组长"><el-select v-model="form.leaderTeacherId" filterable clearable style="width:100%"><el-option v-for="x in teachers" :key="x.id" :value="x.id" :label="personLabel(x)" /></el-select></el-form-item>
        <el-form-item v-if="tab==='groups'" label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <template v-if="tab==='activities'"><el-form-item label="所属教研组" prop="groupId"><el-select v-model="form.groupId" filterable style="width:100%"><el-option v-for="x in groups" :key="x.id" :value="x.id" :label="x.name" /></el-select></el-form-item><el-form-item label="活动类型"><el-select v-model="form.activityType" clearable style="width:100%"><el-option v-for="x in activityTypes" :key="x.value" v-bind="x" /></el-select></el-form-item><el-form-item label="活动时间"><el-date-picker v-model="form.activityTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="议程"><el-input v-model="form.agenda" type="textarea" /></el-form-item></template>
        <template v-if="tab==='results'"><el-form-item label="成果类型" prop="resultType"><el-select v-model="form.resultType" style="width:100%"><el-option v-for="x in resultTypes" :key="x.value" v-bind="x" /></el-select></el-form-item><el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item></template>
      </el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="!!loadError" @click="save">保存草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="attendanceDialog" title="活动签到/请假" width="560px"><el-form label-width="90px"><el-form-item label="成员"><el-select v-model="attendance.teacherId" filterable style="width:100%"><el-option v-for="x in teachers" :key="x.id" :value="x.id" :label="personLabel(x)" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="attendance.status" style="width:100%"><el-option v-for="x in attendanceStatuses" :key="x.value" v-bind="x" /></el-select></el-form-item></el-form><template #footer><el-button @click="attendanceDialog=false">取消</el-button><el-button type="primary" @click="saveAttendance">保存</el-button></template></el-dialog>
    <el-dialog v-model="minutesDialog" title="活动纪要与材料" width="650px"><el-input v-model="minutes" type="textarea" :rows="8" placeholder="记录议题、结论和后续事项" /><template #footer><el-button @click="minutesDialog=false">取消</el-button><el-button type="primary" @click="saveMinutes">保存纪要</el-button></template></el-dialog>
  </section>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dictionaryOptions, employees } from '../../iam/api'
import { createResearch, createResearchChild, researchChildren, researchPage, updateResearch } from '../api/teachingCenter'
const tab=ref('groups'), rows=ref([]), groups=ref([]), teachers=ref([]), loading=ref(false), loadError=ref(''), page=ref(1), size=ref(20), total=ref(0), keyword=ref(''), dialog=ref(false), editing=ref(''), formRef=ref()
const statuses=ref([]), resultTypes=ref([]), activityTypes=ref([]), attendanceStatuses=ref([])
const form=reactive({name:'',groupId:'',leaderTeacherId:'',description:'',activityType:'',activityTime:'',agenda:'',resultType:'',content:''}); const attendance=reactive({activityId:'',teacherId:'',status:''}); const minutes=ref(''); const attendanceDialog=ref(false),minutesDialog=ref(false)
const tabLabel=computed(()=>tab.value==='groups'?'教研组':tab.value==='activities'?'活动':'成果'); const rules=computed(()=>({name:[{required:true,message:'请输入名称'}],groupId:tab.value==='activities'?[{required:true,message:'请选择教研组'}]:[],resultType:tab.value==='results'?[{required:true,message:'请选择成果类型'}]:[]})); const unwrap=r=>r?.data?.content||r?.data||[]; const labelOf=(a,v)=>a.find(x=>x.value===v)?.label||v||'-'; const personLabel=x=>x.name||x.employeeName||x.realName||x.id; const canSubmit=row=>row.status==='DRAFT'||row.status===''
const load=async()=>{loading.value=true;try{const r=await researchPage({page:page.value-1,size:size.value,keyword:keyword.value,type:tab.value.toUpperCase()});rows.value=unwrap(r);total.value=r?.data?.totalElements||rows.value.length;if(tab.value==='groups')groups.value=rows.value}catch(e){loadError.value=e.message||'加载失败，请稍后重试'}finally{loading.value=false}}
const openCreate=()=>{editing.value='';Object.keys(form).forEach(k=>form[k]='');form.resultType=resultTypes.value[0]?.value||'';form.activityType=activityTypes.value[0]?.value||'';dialog.value=true}; const edit=row=>{editing.value=row.id;Object.assign(form,row);dialog.value=true}
const save=async()=>{try{await formRef.value.validate();const body={...form};delete body.status;delete body.id;if(editing.value)await updateResearch(editing.value,body);else await createResearch({...body,resourceType:tab.value.toUpperCase()});dialog.value=false;ElMessage.success('草稿已保存');await load()}catch(e){ElMessage.error(e.message||'保存失败')}}
const submit=async row=>{try{await createResearchChild(row.id,'submit',{ });ElMessage.success('已提交审核');await load()}catch(e){ElMessage.error(e.message||'提交失败')}}
const openAttendance=row=>{Object.assign(attendance,{activityId:row.id,teacherId:'',status:attendanceStatuses.value[0]?.value||''});attendanceDialog.value=true}; const saveAttendance=async()=>{try{await createResearchChild(attendance.activityId,'attendance',{teacherId:attendance.teacherId,attendanceStatus:attendance.status});attendanceDialog.value=false;ElMessage.success('已记录')}catch(e){ElMessage.error(e.message)}}
const openMinutes=row=>{attendance.activityId=row.id;minutes.value=row.minutes||'';minutesDialog.value=true}; const saveMinutes=async()=>{try{await updateResearch(attendance.activityId,{minutes:minutes.value});minutesDialog.value=false;ElMessage.success('纪要已保存')}catch(e){ElMessage.error(e.message)}}
onMounted(async()=>{try{const [s,r,a,at,t]=await Promise.all([dictionaryOptions('EDU_RESEARCH_STATUS'),dictionaryOptions('EDU_RESEARCH_RESULT_TYPE'),dictionaryOptions('EDU_RESEARCH_ACTIVITY_TYPE'),dictionaryOptions('EDU_RESEARCH_ATTENDANCE_STATUS'),employees({page:0,size:100})]);statuses.value=(s?.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));resultTypes.value=(r?.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));activityTypes.value=(a?.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));attendanceStatuses.value=(at?.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));teachers.value=unwrap(t);await load()}catch(e){loadError.value=e.message||'字典加载失败，请检查配置'}})
</script>
<style scoped>.workbench{padding:24px}.head{display:flex;justify-content:space-between;margin-bottom:18px}.head h1{margin:0 0 6px}.head p{margin:0;color:#7b8794}.el-pagination{margin-top:18px;justify-content:flex-end}.el-alert{margin-bottom:16px}</style>
