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
          <el-button link type="success" @click="changeStatus(row, 'PUBLISHED')">发布</el-button>
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
        <el-form-item :label="filters.type === 'PLAN' ? '计划名称' : '标题'" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="childDialog" title="关联子对象" width="760px">
      <el-form inline><el-form-item label="类型"><el-select v-model="childType" style="width:220px"><el-option v-for="item in childTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-button @click="loadChildren">查询</el-button><el-button type="primary" @click="newChild">新增</el-button></el-form>
      <el-table :data="children" size="small"><el-table-column prop="id" label="ID" min-width="180" /><el-table-column label="内容" min-width="280"><template #default="{row}">{{ childSummary(row) }}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><el-button link @click="editChild(row)">编辑</el-button><el-button link type="danger" @click="removeChild(row)">删除</el-button></template></el-table-column></el-table>
    </el-dialog>
    <el-dialog v-model="childFormDialog" title="子对象" width="620px"><el-input v-model="childJson" type="textarea" :rows="12" placeholder="请输入 JSON 字段" /><template #footer><el-button @click="childFormDialog=false">取消</el-button><el-button type="primary" @click="saveChild">保存</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTeachingDomain, teachingDomainPage, archiveTeachingDomain, statusTeachingDomain, updateTeachingDomain, exportTeachingDomainCsv, importTeachingDomainCsv, teachingChildPage, createTeachingChild, updateTeachingChild, deleteTeachingChild } from '../api/teachingCenter'
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
const childDialog = ref(false); const childFormDialog = ref(false); const childType = ref('PLAN_ITEM'); const childParent = ref(''); const childEditing = ref(null); const childJson = ref('{}'); const children = ref([])
const childTypes = [{ value: 'PLAN_ITEM', label: '计划项' }, { value: 'PLAN_VERSION', label: '计划版本' }, { value: 'LESSON_VERSION', label: '教案版本' }, { value: 'LESSON_REVIEW', label: '教案审核' }, { value: 'PREPARATION_MEMBER', label: '备课成员' }, { value: 'PREPARATION_MATERIAL', label: '备课资料' }, { value: 'PREPARATION_COMMENT', label: '备课评论' }, { value: 'QUESTION_OPTION', label: '题目选项' }, { value: 'QUESTION_KNOWLEDGE_POINT', label: '题目知识点' }, { value: 'RESEARCH_GROUP_MEMBER', label: '教研成员' }, { value: 'RESEARCH_ACTIVITY_MEMBER', label: '活动成员' }, { value: 'RESEARCH_MATERIAL', label: '活动资料' }, { value: 'RESEARCH_RESULT', label: '教研成果' }]
const form = reactive({ resourceType: 'PLAN', offeringId: '', scheduleEntryId: '', title: '', fileId: '', content: '' })
const load = async () => {
  loading.value = true
  try { const res = await teachingDomainPage(filters.type, { offeringId: filters.offeringId || undefined, page: page.value - 1, size: size.value }); rows.value = res.data?.content || []; total.value = res.data?.totalElements || 0 }
  catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
const openCreate = () => { editing.value = null; Object.assign(form, { resourceType: filters.type, offeringId: filters.offeringId, scheduleEntryId: '', title: '', fileId: '', content: '' }); dialog.value = true }
const edit = (row) => {
  editing.value = row.id
  Object.assign(form, row, { resourceType: filters.type, title: row.title || row.name || '' })
  dialog.value = true
}
const payload = () => {
  const value = { ...form }
  value.status = value.status || 'DRAFT'
  if (value.resourceType === 'PLAN') { value.name = value.title; delete value.title } else value.title = value.title
  delete value.resourceType
  return value
}
const save = async () => { try { editing.value ? await updateTeachingDomain(filters.type, editing.value, payload()) : await createTeachingDomain(filters.type, payload()); dialog.value = false; ElMessage.success('已保存'); await load() } catch (error) { ElMessage.error(error.message) } }
const changeStatus = async (row, status) => { try { await statusTeachingDomain(filters.type, row.id, status); ElMessage.success('状态已更新'); await load() } catch (error) { ElMessage.error(error.message) } }
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
const newChild = () => { childEditing.value = null; childJson.value = JSON.stringify({}); childFormDialog.value = true }
const editChild = (row) => { childEditing.value = row.id; childJson.value = JSON.stringify(row, null, 2); childFormDialog.value = true }
const childSummary = (row) => Object.entries(row).filter(([k]) => !['id', 'createTime', 'lastUpdateTime'].includes(k)).slice(0, 3).map(([k, v]) => `${k}: ${v}`).join('；')
const saveChild = async () => { try { const value = JSON.parse(childJson.value); if (childEditing.value) await updateTeachingChild(childType.value, childEditing.value, value); else { const parentFields = { PLAN_ITEM: 'planId', PLAN_VERSION: 'planId', LESSON_VERSION: 'lessonPlanId', LESSON_REVIEW: 'lessonPlanVersionId', PREPARATION_MEMBER: 'preparationId', PREPARATION_MATERIAL: 'preparationId', PREPARATION_COMMENT: 'preparationId', QUESTION_OPTION: 'questionId', QUESTION_KNOWLEDGE_POINT: 'questionId', RESEARCH_GROUP_MEMBER: 'groupId', RESEARCH_ACTIVITY_MEMBER: 'activityId', RESEARCH_MATERIAL: 'activityId', RESEARCH_RESULT: 'activityId' }; value[parentFields[childType.value]] = childParent.value; await createTeachingChild(childType.value, value) } childFormDialog.value = false; await loadChildren(); ElMessage.success('已保存') } catch (error) { ElMessage.error(error.message || 'JSON 格式错误') } }
const removeChild = async (row) => { try { await deleteTeachingChild(childType.value, row.id); await loadChildren() } catch (error) { ElMessage.error(error.message) } }
onMounted(load)
</script>

<style scoped>
.teaching-center { padding: 24px; }
.page-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.page-head h1 { margin:0 0 6px; } .page-head p { margin:0; color:#7b8794; }
.el-pagination { margin-top:20px; justify-content:flex-end; }
</style>
