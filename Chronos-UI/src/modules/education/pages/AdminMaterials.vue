<template>
  <section class="materials-page">
    <header class="page-head">
      <div><h1>教学材料</h1><p>管理课本、习题、讲义、参考资料和工作表；教学材料与课件相互独立。</p></div>
      <el-button type="primary" @click="openCreate">新建教学材料</el-button>
    </header>
    <el-form inline @submit.prevent="load">
      <el-form-item label="教学任务"><el-select v-model="filters.offeringId" clearable filterable style="width:320px"><el-option v-for="x in offerings" :key="x.id" :value="x.id" :label="offeringLabel(x)" /></el-select></el-form-item>
      <el-form-item label="材料分类"><el-select v-model="filters.materialType" clearable style="width:180px"><el-option v-for="x in materialTypes" :key="x.value" v-bind="x" /></el-select></el-form-item>
      <el-button @click="load">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="title" label="材料名称" min-width="240" />
      <el-table-column label="分类" width="140"><template #default="{row}">{{ labelOf(materialTypes,row.materialType||row.category) }}</template></el-table-column>
      <el-table-column label="共享范围" width="140"><template #default="{row}">{{ labelOf(scopes,row.shareScope) }}</template></el-table-column>
      <el-table-column prop="publishedVersionNo" label="当前版本" width="100" />
      <el-table-column label="操作" width="260" fixed="right"><template #default="{row}"><el-button link @click="versions(row)">版本</el-button><el-button link @click="edit(row)">编辑</el-button><el-button link type="success" @click="submit(row)">提交审核</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" @change="load" />
    <el-dialog v-model="dialog" :title="editing ? '编辑教学材料' : '新建教学材料'" width="650px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="教学任务" prop="offeringId"><el-select v-model="form.offeringId" filterable style="width:100%"><el-option v-for="x in offerings" :key="x.id" :value="x.id" :label="offeringLabel(x)" /></el-select></el-form-item>
        <el-form-item label="材料名称" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="材料分类" prop="materialType"><el-select v-model="form.materialType" style="width:100%"><el-option v-for="x in materialTypes" :key="x.value" v-bind="x" /></el-select></el-form-item>
        <el-form-item label="共享范围" prop="shareScope"><el-select v-model="form.shareScope" style="width:100%"><el-option v-for="x in scopes" :key="x.value" v-bind="x" /></el-select></el-form-item>
        <el-form-item label="使用说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="文件"><el-upload :show-file-list="false" :before-upload="selectFile"><el-button>选择材料文件</el-button></el-upload><span>{{ fileName }}</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="versionDialog" title="材料版本" width="760px"><el-table :data="versionRows"><el-table-column prop="versionNo" label="版本" /><el-table-column prop="status" label="状态" /><el-table-column prop="uploadedAt" label="上传时间" /><el-table-column label="操作"><template #default="{row}"><el-button link @click="preview(row)">查看文件</el-button></template></el-table-column></el-table></el-dialog>
  </section>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dictionaryOptions, uploadManagedFile, downloadManagedFile } from '../../../api/admin'
import { addResourceVersion, createResource, resourcePage, resourceVersions, submitResource, teachingCenterOfferings, updateResource } from '../api/teachingCenter'
const rows=ref([]), offerings=ref([]), materialTypes=ref([]), scopes=ref([]), versionRows=ref([]), total=ref(0), page=ref(1), size=ref(20), loading=ref(false), dialog=ref(false), versionDialog=ref(false), editing=ref(''), selected=ref(null), formRef=ref(), pendingFile=ref(null), fileName=ref('')
const filters=reactive({offeringId:'',materialType:''}); const form=reactive({offeringId:'',title:'',materialType:'',shareScope:'',description:''}); const rules={offeringId:[{required:true,message:'请选择教学任务'}],title:[{required:true,message:'请输入材料名称'}],materialType:[{required:true,message:'请选择材料分类'}],shareScope:[{required:true,message:'请选择共享范围'}]}
const unwrap=r=>r?.data?.content||r?.data||[]; const labelOf=(list,v)=>list.find(x=>x.value===v)?.label||v||'-'; const offeringLabel=x=>[x.semesterCode,x.courseName,x.teachingClassName].filter(Boolean).join(' · ')
const load=async()=>{loading.value=true;try{const r=await resourcePage('materials',{...filters,page:page.value-1,size:size.value});rows.value=unwrap(r);total.value=r?.data?.totalElements||rows.value.length}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
const openCreate=()=>{editing.value='';pendingFile.value=null;fileName.value='';Object.assign(form,{offeringId:filters.offeringId||offerings.value[0]?.id||'',title:'',materialType:materialTypes.value[0]?.value||'',shareScope:scopes.value[0]?.value||'',description:''});dialog.value=true}
const edit=row=>{editing.value=row.id;pendingFile.value=null;Object.assign(form,row);dialog.value=true}
const selectFile=file=>{pendingFile.value=file;fileName.value=file.name;return false}
const save=async()=>{try{await formRef.value.validate();const body={...form,materialType:form.materialType};let result=editing.value?await updateResource('materials',editing.value,body):await createResource('materials',body);const id=editing.value||result?.data?.id;if(pendingFile.value&&id){const uploaded=await uploadManagedFile(pendingFile.value);if(!uploaded?.data?.id)throw new Error('材料文件上传失败');await addResourceVersion('materials',id,{fileId:uploaded.data.id})}dialog.value=false;ElMessage.success('教学材料已保存');await load()}catch(e){ElMessage.error(e.message)}}
const versions=async row=>{selected.value=row;versionRows.value=unwrap(await resourceVersions('materials',row.id));versionDialog.value=true}
const preview=async row=>{if(!row.fileId)return ElMessage.info('该版本没有文件');const blob=await downloadManagedFile(row.fileId);window.open(URL.createObjectURL(blob),'_blank')}
const submit=async row=>{try{const versions=unwrap(await resourceVersions('materials',row.id));const draft=versions.find(x=>['DRAFT','REJECTED'].includes(x.status));if(!draft)throw new Error('没有可提交审核的版本');await submitResource('materials',draft.id);ElMessage.success('已提交审核')}catch(e){ElMessage.error(e.message)}}
onMounted(async()=>{try{const [t,s,o]=await Promise.all([dictionaryOptions('EDU_MATERIAL_CATEGORY'),dictionaryOptions('EDU_RESOURCE_SHARE_SCOPE'),teachingCenterOfferings()]);materialTypes.value=(t.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));scopes.value=(s.data||[]).map(x=>({value:x.dictValue,label:x.dictName}));offerings.value=unwrap(o);await load()}catch(e){ElMessage.error(e.message)}})
</script>
<style scoped>.materials-page{padding:24px}.page-head{display:flex;justify-content:space-between;margin-bottom:18px}.page-head h1{margin:0 0 6px}.page-head p{margin:0;color:#7b8794}.el-pagination{margin-top:18px;justify-content:flex-end}</style>
