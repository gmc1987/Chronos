<template>
  <div class="page">
    <header>
      <div><h2>新生入学</h2><p>承接正式录取数据，完成资格复查、报到建档和学籍电子注册结果回填</p></div>
      <div class="header-actions">
        <el-button v-permission="['education:admission:import','education:admission:manage']" @click="openBatch('CLASS_ASSIGNMENT')">批量拟分班</el-button>
        <el-button v-permission="['education:admission:review','education:admission:manage']" @click="openBatch('REVIEW')">批量资格复查</el-button>
        <el-button v-permission="['education:admission:enroll','education:admission:manage']" @click="openBatch('ENROLL')">批量报到建档</el-button>
        <el-button v-permission="['education:admission:import','education:admission:manage']" @click="downloadTemplate">下载导入模板</el-button>
        <el-upload v-permission="['education:admission:import','education:admission:manage']" :show-file-list="false" accept=".xlsx" :http-request="o=>uploadAdmissions(o.file)"><el-button>导入正式录取名册</el-button></el-upload>
        <el-button v-permission="['education:admission:import','education:admission:manage']" type="primary" @click="openEdit()">录入录取数据</el-button>
      </div>
    </header>
    <div class="toolbar">
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search">
        <el-option v-for="item in admissionStatuses" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="filters.keyword" clearable placeholder="姓名、考生号或录取号" @keyup.enter="search" @clear="search" />
      <el-button @click="search">查询</el-button>
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="candidateNo" label="考生号" min-width="150" />
      <el-table-column prop="admissionNo" label="录取号" min-width="130" />
      <el-table-column prop="studentName" label="姓名" width="100" />
      <el-table-column prop="admissionYear" label="入学年份" width="100" />
      <el-table-column prop="educationLevel" label="培养层次" width="120"><template #default="s">{{ levelName(s.row.educationLevel) }}</template></el-table-column>
      <el-table-column prop="admittedMajorId" label="录取专业" min-width="150"><template #default="s">{{ majorName(s.row.admittedMajorId) }}</template></el-table-column>
      <el-table-column prop="admissionStatus" label="入学状态" width="110"><template #default="s"><el-tag :type="statusType(s.row.admissionStatus)">{{ statusName(s.row.admissionStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="registrationStatus" label="电子注册" width="110"><template #default="s">{{ registrationName(s.row.registrationStatus) }}</template></el-table-column>
      <el-table-column prop="nationalStudentNo" label="国家学籍号" min-width="150" />
      <AdaptiveActionColumn label="操作" width="300"><template #default="s"><div class="row-actions">
        <el-button v-if="s.row.admissionStatus==='ADMITTED'" link @click="openEdit(s.row)">编辑</el-button>
        <el-button v-if="s.row.admissionStatus==='ADMITTED'" v-permission="['education:admission:review','education:admission:manage']" link type="success" @click="review(s.row,true)">复查通过</el-button>
        <el-button v-if="s.row.admissionStatus==='ADMITTED'" v-permission="['education:admission:review','education:admission:manage']" link type="danger" @click="review(s.row,false)">复查退回</el-button>
        <el-button v-if="s.row.admissionStatus==='VERIFIED'" v-permission="['education:admission:enroll','education:admission:manage']" link type="primary" @click="openEnroll(s.row)">报到建档</el-button>
        <el-button v-if="s.row.admissionStatus==='ENROLLED'" v-permission="['education:admission:register','education:admission:manage']" link type="primary" @click="openRegistration(s.row)">登记注册结果</el-button>
      </div></template></AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="changeSize" />

    <el-dialog v-model="editDialog" :title="form.id?'编辑录取数据':'录入录取数据'" width="720px">
      <el-alert title="这里保存的是省级招办核准录取数据的校内副本，不会直接生成正式学籍。" type="info" :closable="false" />
      <el-form label-width="110px" class="form-grid">
        <el-form-item label="来源批次"><el-input v-model="form.sourceBatchNo" placeholder="例如：2026-GK-01" /></el-form-item>
        <el-form-item label="数据来源"><el-select v-model="form.sourceType"><el-option label="省级招办导入" value="PROVINCIAL_IMPORT" /><el-option label="授权人工补录" value="MANUAL_VERIFIED" /></el-select></el-form-item>
        <el-form-item label="考生号"><el-input v-model="form.candidateNo" /></el-form-item>
        <el-form-item label="录取号"><el-input v-model="form.admissionNo" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.studentName" /></el-form-item>
        <el-form-item label="性别"><el-select v-model="form.gender"><el-option label="男" value="MALE" /><el-option label="女" value="FEMALE" /></el-select></el-form-item>
        <el-form-item label="入学年份"><el-input-number v-model="form.admissionYear" :min="2000" :max="2100" /></el-form-item>
        <el-form-item label="培养层次"><el-select v-model="form.educationLevel"><el-option v-for="item in educationLevels" :key="item.value" v-bind="item" /></el-select></el-form-item>
        <el-form-item label="录取专业"><el-select v-model="form.admittedMajorId" filterable clearable><el-option v-for="item in majors" :key="item.id" :label="item.majorName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="拟分行政班"><el-select v-model="form.plannedClassId" filterable clearable><el-option v-for="item in eligibleClasses" :key="item.id" :label="item.className" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editDialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="enrollDialog" title="新生报到建档" width="520px">
      <el-alert :title="`${active?.studentName}资格复查已通过，完成后系统将生成不可随意修改的校内学号。`" type="warning" :closable="false" />
      <el-form label-width="110px"><el-form-item label="行政班"><el-select v-model="enrollClassId" filterable><el-option v-for="item in enrollClasses" :key="item.id" :label="item.className" :value="item.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="enrollDialog=false">取消</el-button><el-button type="primary" @click="enroll">确认报到并建档</el-button></template>
    </el-dialog>

    <el-dialog v-model="registrationDialog" title="登记学籍电子注册结果" width="560px">
      <el-alert title="请根据教育部学籍学历管理平台的实际处理结果填写，系统不会自行生成国家学籍号。" type="info" :closable="false" />
      <el-form label-width="120px">
        <el-form-item label="注册状态"><el-select v-model="registration.status"><el-option label="待主管部门处理" value="PENDING" /><el-option label="注册成功" value="REGISTERED" /><el-option label="注册退回" value="REJECTED" /></el-select></el-form-item>
        <el-form-item v-if="registration.status==='REGISTERED'" label="国家学籍号"><el-input v-model="registration.nationalStudentNo" /></el-form-item>
        <el-form-item v-if="registration.status==='REJECTED'" label="退回原因"><el-input v-model="registration.errorMessage" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="registrationDialog=false">取消</el-button><el-button type="primary" @click="submitRegistration">保存结果</el-button></template>
    </el-dialog>

    <el-dialog v-model="batchDialog" :title="batchTitle" width="860px">
      <el-alert type="info" :closable="false" title="批量操作先生成候选结果，不会立即修改正式数据；确认候选无误后才执行。" />
      <el-form inline class="batch-form">
        <el-form-item label="入学年份"><el-input-number v-model="batchForm.admissionYear" :min="2000" :max="2100" /></el-form-item>
        <el-form-item label="本批上限"><el-input-number v-model="batchForm.batchLimit" :min="1" :max="500" /></el-form-item>
        <el-form-item v-if="batchType==='CLASS_ASSIGNMENT'" label="目标班额"><el-input-number v-model="batchForm.targetClassSize" :min="1" :max="200" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="batchLoading" @click="previewBatch">生成候选结果</el-button></el-form-item>
      </el-form>
      <template v-if="batchPreview">
        <el-descriptions :column="4" border class="batch-summary">
          <el-descriptions-item label="候选总数">{{ batchPreview.candidateCount }}</el-descriptions-item>
          <el-descriptions-item label="可处理">{{ batchPreview.assigned ?? batchPreview.eligible ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ batchPreview.skipped ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="无可用班级">{{ batchPreview.noAvailableClass ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="batchRows" border max-height="360">
          <el-table-column prop="candidateNo" label="考生号" min-width="150" />
          <el-table-column prop="studentName" label="姓名" min-width="110" />
          <el-table-column v-if="batchType==='CLASS_ASSIGNMENT'" prop="className" label="拟分行政班" min-width="180" />
          <el-table-column v-else label="拟分行政班" min-width="180"><template #default="s">{{ className(s.row.plannedClassId) }}</template></el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="batchDialog=false">取消</el-button>
        <el-button type="primary" :disabled="!batchPreview || batchRows.length===0" :loading="batchLoading" @click="executeBatch">确认并执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchAssignAdmissionClasses, batchEnrollAdmissions, batchReviewAdmissions, createAdmissionCandidate, downloadAdmissionImportTemplate, enrollAdmissionCandidate, importAdmissionCandidates, listAdministrativeClasses, listAdmissionCandidates, listEducationMajors, reviewAdmissionCandidate, updateAdmissionCandidate, updateAdmissionRegistration } from '../../../api/admin'

const rows=ref([]),page=ref(1),size=ref(10),total=ref(0),majors=ref([]),classes=ref([]),editDialog=ref(false),enrollDialog=ref(false),registrationDialog=ref(false),active=ref(null),enrollClassId=ref('')
const batchDialog=ref(false),batchLoading=ref(false),batchType=ref(''),batchPreview=ref(null)
const filters=reactive({status:'',keyword:''}),form=reactive({}),registration=reactive({status:'PENDING',nationalStudentNo:'',errorMessage:''})
const batchForm=reactive({admissionYear:new Date().getFullYear(),batchLimit:100,targetClassSize:50})
const educationLevels=[{label:'本科',value:'UNDERGRADUATE'},{label:'职业本科',value:'VOCATIONAL_UNDERGRADUATE'},{label:'高职/专科',value:'HIGHER_EDUCATION'}]
const admissionStatuses=[{label:'待资格复查',value:'ADMITTED'},{label:'复查通过',value:'VERIFIED'},{label:'复查退回',value:'REJECTED'},{label:'已报到建档',value:'ENROLLED'}]
const registrationStatuses={NOT_SUBMITTED:'未提交',PENDING:'待主管部门处理',REGISTERED:'注册成功',REJECTED:'注册退回'}
const eligibleClasses=computed(()=>classes.value.filter(item=>(!form.admittedMajorId||item.majorId===form.admittedMajorId)&&(!form.admissionYear||item.gradeYear===form.admissionYear)))
const enrollClasses=computed(()=>classes.value.filter(item=>item.gradeYear===active.value?.admissionYear&&(!active.value?.admittedMajorId||item.majorId===active.value.admittedMajorId)))
const batchTitle=computed(()=>({CLASS_ASSIGNMENT:'批量拟分行政班',REVIEW:'批量资格复查',ENROLL:'批量报到建档并开通账号'}[batchType.value]||'批量处理'))
const batchRows=computed(()=>batchPreview.value?.assignments||batchPreview.value?.candidates||[])
const load=async()=>{const r=await listAdmissionCandidates({page:page.value-1,size:size.value,...filters});rows.value=r.data?.content||[];total.value=r.data?.totalElements||0}
const search=()=>{page.value=1;load()};const changeSize=()=>{page.value=1;load()}
const openEdit=(row={})=>{Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,{sourceType:'PROVINCIAL_IMPORT',admissionYear:new Date().getFullYear(),educationLevel:'HIGHER_EDUCATION',...row});editDialog.value=true}
const save=async()=>{if(!form.sourceBatchNo||!form.candidateNo||!form.studentName) return ElMessage.warning('请完整填写来源批次、考生号和姓名');await(form.id?updateAdmissionCandidate(form.id,form):createAdmissionCandidate(form));editDialog.value=false;ElMessage.success('录取数据已保存');await load()}
const review=async(row,passed)=>{let remark='';if(!passed){const r=await ElMessageBox.prompt('请输入资格复查退回原因','复查退回',{inputType:'textarea',inputValidator:v=>!!v?.trim()||'退回原因不能为空'});remark=r.value}else{await ElMessageBox.confirm(`确认 ${row.studentName} 的录取资格复查通过？`,'资格复查',{type:'warning'})}await reviewAdmissionCandidate(row.id,{passed,remark});ElMessage.success(passed?'资格复查已通过':'录取记录已退回');await load()}
const openEnroll=row=>{active.value=row;enrollClassId.value=row.plannedClassId||'';enrollDialog.value=true}
const enroll=async()=>{if(!enrollClassId.value)return ElMessage.warning('请选择行政班');await enrollAdmissionCandidate(active.value.id,{administrativeClassId:enrollClassId.value});enrollDialog.value=false;ElMessage.success('报到建档完成，校内学号已生成');await load()}
const openRegistration=row=>{active.value=row;Object.assign(registration,{status:row.registrationStatus==='NOT_SUBMITTED'?'PENDING':row.registrationStatus,nationalStudentNo:row.nationalStudentNo||'',errorMessage:row.registrationError||''});registrationDialog.value=true}
const submitRegistration=async()=>{await updateAdmissionRegistration(active.value.id,{...registration});registrationDialog.value=false;ElMessage.success('学籍电子注册结果已登记');await load()}
const downloadTemplate=async()=>{const blob=await downloadAdmissionImportTemplate();const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='Chronos-正式录取名册模板.xlsx';a.click();URL.revokeObjectURL(url)}
const uploadAdmissions=async file=>{const preview=await importAdmissionCandidates(file,true);if(preview.data?.errors?.length){await ElMessageBox.alert(preview.data.errors.slice(0,20).join('\n'),'导入校验失败',{type:'error'});return}await ElMessageBox.confirm(`校验通过，共 ${preview.data?.successCount||0} 条正式录取数据，确认导入？`,'导入确认',{type:'warning'});await importAdmissionCandidates(file,false);ElMessage.success('正式录取名册导入成功');await load()}
const batchApi=()=>batchType.value==='CLASS_ASSIGNMENT'?batchAssignAdmissionClasses:batchType.value==='REVIEW'?batchReviewAdmissions:batchEnrollAdmissions
const openBatch=type=>{batchType.value=type;batchPreview.value=null;batchDialog.value=true}
const previewBatch=async()=>{batchLoading.value=true;try{const r=await batchApi()({...batchForm,dryRun:true});batchPreview.value=r.data||{};if(!batchRows.value.length)ElMessage.warning('当前条件下没有可处理的候选数据')}finally{batchLoading.value=false}}
const executeBatch=async()=>{await ElMessageBox.confirm(`确认执行“${batchTitle.value}”，共处理 ${batchRows.value.length} 条候选数据？`,'批量执行确认',{type:'warning'});batchLoading.value=true;try{const r=await batchApi()({...batchForm,dryRun:false});const count=r.data?.assigned??r.data?.succeeded??0;ElMessage.success(`批量处理完成，共成功 ${count} 条`);batchPreview.value=null;batchDialog.value=false;await load()}finally{batchLoading.value=false}}
const majorName=id=>majors.value.find(x=>x.id===id)?.majorName||'-';const className=id=>classes.value.find(x=>x.id===id)?.className||'-';const levelName=v=>educationLevels.find(x=>x.value===v)?.label||v;const statusName=v=>admissionStatuses.find(x=>x.value===v)?.label||v;const registrationName=v=>registrationStatuses[v]||v;const statusType=v=>({ADMITTED:'warning',VERIFIED:'success',REJECTED:'danger',ENROLLED:'primary'}[v]||'info')
onMounted(async()=>{const[m,c]=await Promise.all([listEducationMajors(),listAdministrativeClasses()]);majors.value=m.data||[];classes.value=c.data||[];await load()})
</script>

<style scoped>
.page{padding:24px}.page header{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px;gap:16px}.page h2{margin:0 0 6px}.page p{margin:0;color:#84909a}.header-actions,.toolbar{display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap}.toolbar{margin-bottom:10px}.toolbar .el-select{width:180px}.toolbar .el-input{width:280px}.el-pagination{justify-content:flex-end;margin-top:10px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px;margin-top:18px}.row-actions{display:flex;flex-wrap:nowrap;white-space:nowrap}.row-actions .el-button{flex:none}.batch-form{margin-top:18px}.batch-summary{margin-bottom:14px}@media(max-width:760px){.form-grid{grid-template-columns:1fr}}
</style>
