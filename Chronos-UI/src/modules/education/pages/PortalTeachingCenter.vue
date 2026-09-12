<template>
  <section class="teaching-center">
    <div class="page-head">
      <div><h1>教学中心</h1><p>教学资源与教研工作台（作业发布和评分请前往作业中心）</p></div>
      <el-button type="primary" @click="openCreate">新建资源</el-button>
    </div>
    <el-form inline @submit.prevent="load">
      <el-form-item label="类型"><el-select v-model="filters.type" style="width:150px">
        <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
      </el-select></el-form-item>
      <el-form-item label="教学班"><el-input v-model="filters.offeringId" placeholder="Offering ID" /></el-form-item>
      <el-button @click="load">查询</el-button><el-button @click="exportCsv">导出 CSV</el-button>
      <el-upload :show-file-list="false" accept=".csv" :before-upload="importCsv"><el-button>导入 CSV</el-button></el-upload>
    </el-form>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="名称" min-width="220"><template #default="{ row }">{{ row.title || row.name }}</template></el-table-column>
      <el-table-column prop="resourceType" label="类型" width="130" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="versionNo" label="版本" width="80" />
      <el-table-column label="操作" width="230">
        <template #default="{ row }"><el-button link @click="edit(row)">编辑</el-button>
          <el-button link type="primary" @click="openChildren(row)">子对象</el-button>
          <el-button link type="success" :disabled="row.status !== 'DRAFT'" @click="submitReview(row)">提交审核</el-button>
          <el-button link type="danger" @click="archive(row)">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
      layout="total, sizes, prev, pager, next" @change="load" />
    <el-dialog v-model="dialog" title="教学中心资源" width="650px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="资源类型" required><el-select v-model="form.resourceType">
          <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
        </el-select></el-form-item>
        <el-form-item label="教学班" required><el-input v-model="form.offeringId" /></el-form-item>
        <el-form-item label="课表项"><el-input v-model="form.scheduleEntryId" placeholder="可选，只读关联" /></el-form-item>
        <el-form-item v-for="field in (mainFields[filters.type] || ['title'])" :key="field" :label="field" required>
          <el-input v-if="!['content','stem'].includes(field)" v-model="form[field]" />
          <el-input v-else v-model="form[field]" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="childDialog" title="关联子对象" width="760px">
      <el-form inline><el-form-item label="类型"><el-select v-model="childType" style="width:220px"><el-option v-for="item in childTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-button @click="loadChildren">查询</el-button><el-button type="primary" @click="newChild">新增</el-button></el-form>
      <el-table :data="children" size="small"><el-table-column prop="id" label="ID" min-width="180" /><el-table-column label="内容" min-width="280"><template #default="{row}">{{ childSummary(row) }}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><el-button link @click="editChild(row)">编辑</el-button><el-button link type="danger" @click="removeChild(row)">删除</el-button></template></el-table-column></el-table>
    </el-dialog>
    <el-dialog v-model="childFormDialog" title="子对象" width="620px">
      <el-form ref="childFormRef" :model="childForm" :rules="childRules" label-width="120px">
        <el-form-item v-for="field in childFields" :key="field.key" :label="field.label" :prop="field.key">
          <el-input v-if="field.type === 'text'" v-model="childForm[field.key]" />
          <el-input v-else-if="field.type === 'textarea'" v-model="childForm[field.key]" type="textarea" :rows="4" />
          <el-input-number v-else-if="field.type === 'number'" v-model="childForm[field.key]" :min="0" />
          <el-select v-else v-model="childForm[field.key]" style="width:100%"><el-option v-for="o in (field.options || [])" :key="o" :label="o" :value="o" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="childFormDialog=false">取消</el-button><el-button type="primary" @click="saveChild">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createTeachingDomain, teachingDomainPage, archiveTeachingDomain, updateTeachingDomain, exportTeachingDomainCsv, importTeachingDomainCsv, teachingChildPage, createTeachingChild, updateTeachingChild, deleteTeachingChild, submitTeachingReview } from '../api/teachingCenter'
const types = [
  { value: 'PLAN', label: '教学计划' }, { value: 'LESSON_PLAN', label: '教案' },
  { value: 'PREPARATION', label: '备课' }, { value: 'COURSEWARE', label: '课件' },
  { value: 'MATERIAL', label: '教学材料' }, { value: 'QUESTION_BANK', label: '题库' },
  { value: 'KNOWLEDGE_POINT', label: '知识点' }, { value: 'MISTAKE', label: '错题' },
  { value: 'RESEARCH', label: '教研' },
]
const filters = reactive({ type: 'PLAN', offeringId: '' })
const rows = ref([]); const total = ref(0); const page = ref(1); const size = ref(20)
const loading = ref(false); const dialog = ref(false); const editing = ref(null)
const childDialog = ref(false); const childFormDialog = ref(false); const childType = ref('PLAN_ITEM'); const childParent = ref(''); const childEditing = ref(null); const children = ref([])
const childForm = reactive({}); const childRules = {}; const childFormRef = ref()
const childTypes = [{ value: 'PLAN_ITEM', label: '计划项' }, { value: 'PLAN_VERSION', label: '计划版本' }, { value: 'LESSON_VERSION', label: '教案版本' }, { value: 'LESSON_REVIEW', label: '教案审核' }, { value: 'PREPARATION_MEMBER', label: '备课成员' }, { value: 'PREPARATION_MATERIAL', label: '备课资料' }, { value: 'PREPARATION_COMMENT', label: '备课评论' }, { value: 'QUESTION_OPTION', label: '题目选项' }, { value: 'QUESTION_KNOWLEDGE_POINT', label: '题目知识点' }, { value: 'RESEARCH_GROUP_MEMBER', label: '教研成员' }, { value: 'RESEARCH_ACTIVITY_MEMBER', label: '活动成员' }, { value: 'RESEARCH_MATERIAL', label: '活动资料' }, { value: 'RESEARCH_RESULT', label: '教研成果' }]
const form = reactive({ resourceType: 'PLAN', offeringId: '', scheduleEntryId: '', name: '', title: '', subject: '', grade: '', preparationType: '', shareScope: 'PRIVATE', studentId: '', stem: '', questionType: '', difficulty: '', fileId: '', content: '' })
const mainFields = { PLAN: ['name','subject','grade'], LESSON_PLAN:['title'], PREPARATION:['title','preparationType'], COURSEWARE:['title','shareScope','fileId'], MATERIAL:['title','shareScope','fileId'], QUESTION_BANK:['name','subject'], QUESTION:['stem','questionType','difficulty','content'], KNOWLEDGE_POINT:['name','subject'], MISTAKE:['name','studentId'], RESEARCH:['name','subject'] }
const load = async () => {
  loading.value = true
  try { const res = await teachingDomainPage(filters.type, { offeringId: filters.offeringId || undefined, page: page.value - 1, size: size.value }); rows.value = res.data?.content || []; total.value = res.data?.totalElements || 0 }
  catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
const openCreate = () => { editing.value = null; Object.assign(form, { resourceType: filters.type, offeringId: filters.offeringId, scheduleEntryId: '', name: '', title: '', subject: '', grade: '', preparationType: '', shareScope: 'PRIVATE', studentId: '', stem: '', questionType: '', difficulty: '', fileId: '', content: '' }); dialog.value = true }
const edit = (row) => {
  editing.value = row.id
  Object.assign(form, row, { resourceType: filters.type, title: row.title || row.name || '' })
  dialog.value = true
}
const payload = () => {
  const value = { ...form }
  value.status = value.status || 'DRAFT'
  if (value.resourceType === 'PLAN') { value.name = value.name || value.title; delete value.title }
  delete value.resourceType
  return value
}
const save = async () => { try { editing.value ? await updateTeachingDomain(filters.type, editing.value, payload()) : await createTeachingDomain(filters.type, payload()); dialog.value = false; ElMessage.success('已保存'); await load() } catch (error) { ElMessage.error(error.message) } }
const submitReview = async (row) => { try { await submitTeachingReview(filters.type, row.id, { offeringId: row.offeringId, resourceType: filters.type }); ElMessage.success('已提交审核'); await load() } catch (error) { ElMessage.error(error.message) } }
const archive = async (row) => { try { await archiveTeachingDomain(filters.type, row.id); ElMessage.success('已归档'); await load() } catch (error) { ElMessage.error(error.message) } }
const exportCsv = async () => {
  try {
    const response = await exportTeachingDomainCsv(filters.type, { offeringId: filters.offeringId || undefined })
    const blob = new Blob([response.data || response], { type: 'text/csv;charset=utf-8' })
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `${filters.type}.csv`; link.click(); URL.revokeObjectURL(link.href)
  } catch (error) { ElMessage.error(error.message) }
}
const importCsv = async (file) => {
  try { await importTeachingDomainCsv(filters.type, await file.text()); ElMessage.success('导入完成'); await load() }
  catch (error) { ElMessage.error(error.message) }
  return false
}
const openChildren = (row) => { childParent.value = row.id; childDialog.value = true; loadChildren() }
const loadChildren = async () => { try { const res = await teachingChildPage(childType.value, childParent.value, { page: 0, size: 100 }); children.value = res.data?.content || [] } catch (error) { ElMessage.error(error.message) } }
const childSchema = {
 PLAN_ITEM:[['chapterName','章节','text'],['lessonHours','课时','number'],['objectives','目标','textarea'],['keyPoints','重点','textarea'],['difficultPoints','难点','textarea'],['sortOrder','排序','number']],
 PLAN_VERSION:[['versionNo','版本号','number'],['snapshotJson','快照','textarea'],['status','状态','select']],
 LESSON_VERSION:[['versionNo','版本号','number'],['content','内容','textarea'],['fileId','文件','text'],['status','状态','select']],
 LESSON_REVIEW:[['decision','决定','select'],['comment','意见','textarea']], PREPARATION_MEMBER:[['teacherId','教师','text'],['role','角色','text']],
 PREPARATION_MATERIAL:[['title','标题','text'],['fileId','文件','text'],['metadataJson','元数据','textarea']], PREPARATION_COMMENT:[['content','评论','textarea']],
 QUESTION_OPTION:[['optionKey','选项','text'],['optionText','文本','textarea'],['sortOrder','排序','number']], QUESTION_KNOWLEDGE_POINT:[['knowledgePointId','知识点','text']],
 RESEARCH_GROUP_MEMBER:[['teacherId','教师','text'],['role','角色','text']], RESEARCH_ACTIVITY_MEMBER:[['teacherId','教师','text'],['role','角色','text']],
 RESEARCH_MATERIAL:[['title','标题','text'],['fileId','文件','text']], RESEARCH_RESULT:[['title','标题','text'],['content','内容','textarea'],['fileId','文件','text'],['status','状态','select']]
 }
const childFields = computed(() => (childSchema[childType.value] || []).map(([key,label,type]) => ({key,label,type,options:type==='select'?['DRAFT','SUBMITTED','PUBLISHED','APPROVED','REJECTED']:[]})))
const newChild = () => { childEditing.value = null; Object.keys(childForm).forEach(k => delete childForm[k]); childFields.value.forEach(f => { childForm[f.key] = f.type==='number'?0:'' }); childFormDialog.value = true }
const editChild = (row) => { childEditing.value = row.id; Object.keys(childForm).forEach(k => delete childForm[k]); childFields.value.forEach(f => { childForm[f.key] = row[f.key] ?? '' }); childFormDialog.value = true }
const childSummary = (row) => Object.entries(row).filter(([k]) => !['id', 'createTime', 'lastUpdateTime'].includes(k)).slice(0, 3).map(([k, v]) => `${k}: ${v}`).join('；')
const saveChild = async () => { try { const value = { ...childForm }; if (childEditing.value) await updateTeachingChild(childType.value, childEditing.value, value); else { const parentFields = { PLAN_ITEM: 'planId', PLAN_VERSION: 'planId', LESSON_VERSION: 'lessonPlanId', LESSON_REVIEW: 'lessonPlanVersionId', PREPARATION_MEMBER: 'preparationId', PREPARATION_MATERIAL: 'preparationId', PREPARATION_COMMENT: 'preparationId', QUESTION_OPTION: 'questionId', QUESTION_KNOWLEDGE_POINT: 'questionId', RESEARCH_GROUP_MEMBER: 'groupId', RESEARCH_ACTIVITY_MEMBER: 'activityId', RESEARCH_MATERIAL: 'activityId', RESEARCH_RESULT: 'activityId' }; value[parentFields[childType.value]] = childParent.value; await createTeachingChild(childType.value, value) } childFormDialog.value = false; await loadChildren(); ElMessage.success('已保存') } catch (error) { ElMessage.error(error.message) } }
const removeChild = async (row) => { try { await deleteTeachingChild(childType.value, row.id); await loadChildren() } catch (error) { ElMessage.error(error.message) } }
onMounted(load)
</script>

<style scoped>
.teaching-center { padding: 24px; }
.page-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.page-head h1 { margin:0 0 6px; } .page-head p { margin:0; color:#7b8794; }
.el-pagination { margin-top:20px; justify-content:flex-end; }
</style>
