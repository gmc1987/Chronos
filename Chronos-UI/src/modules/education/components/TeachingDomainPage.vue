<template>
  <section class="teaching-domain-page">
    <header class="page-head">
      <div><h1>{{ title }}</h1><p>{{ description }}</p></div>
      <el-button type="primary" @click="openCreate">新增{{ entityLabel }}</el-button>
    </header>
    <el-form inline @submit.prevent="load">
      <el-form-item label="教学班"><el-select v-model="filters.offeringId" clearable filterable placeholder="全部教学班" style="width:260px">
        <el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="`${item.semesterCode} · ${item.courseName} · ${item.teachingClassName}`" />
      </el-select></el-form-item>
      <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="名称或主题" style="width:200px" /></el-form-item>
      <el-button @click="load">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column v-for="column in columns" :key="column.prop" v-bind="column" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="archive(row)">归档</el-button></template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    <el-dialog v-model="dialog" :title="`${editing ? '编辑' : '新增'}${entityLabel}`" width="680px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="教学班" required><el-select v-model="form.offeringId" filterable style="width:100%"><el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="`${item.semesterCode} · ${item.courseName} · ${item.teachingClassName}`" /></el-select></el-form-item>
        <el-form-item v-for="field in fields" :key="field.prop" :label="field.label" :required="field.required !== false">
          <el-input v-if="field.type === 'textarea'" v-model="form[field.prop]" type="textarea" :rows="4" />
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :min="0" />
          <el-select v-else-if="field.options" v-model="form[field.prop]" style="width:100%"><el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-input v-else v-model="form[field.prop]" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveTeachingDomain, createTeachingDomain, teachingCenterOfferings, teachingDomainPage, updateTeachingDomain } from '../api/teachingCenter'

const props = defineProps({
  title: String, description: String, entityLabel: String, resourceType: String,
  columns: { type: Array, default: () => [] }, fields: { type: Array, default: () => [] },
  defaults: { type: Object, default: () => ({}) },
})
const offerings = ref([]); const rows = ref([]); const total = ref(0); const page = ref(1); const size = ref(20); const loading = ref(false)
const dialog = ref(false); const editing = ref(''); const form = reactive({}); const filters = reactive({ offeringId: '', keyword: '' })
const load = async () => {
  loading.value = true
  try {
    const response = await teachingDomainPage(props.resourceType, { ...filters, page: page.value - 1, size: size.value })
    rows.value = response.data?.content || []; total.value = response.data?.totalElements || 0
  } catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
const reset = value => { Object.keys(form).forEach(key => delete form[key]); Object.assign(form, value) }
const openCreate = () => { editing.value = ''; reset({ ...props.defaults, offeringId: filters.offeringId || offerings.value[0]?.id || '' }); dialog.value = true }
const openEdit = row => { editing.value = row.id; reset({ ...props.defaults, ...row }); dialog.value = true }
const save = async () => {
  try {
    if (editing.value) await updateTeachingDomain(props.resourceType, editing.value, { ...form })
    else await createTeachingDomain(props.resourceType, { ...form, status: 'DRAFT' })
    dialog.value = false; ElMessage.success('保存成功'); await load()
  } catch (error) { ElMessage.error(error.message) }
}
const archive = async row => { try { await archiveTeachingDomain(props.resourceType, row.id); ElMessage.success('已归档'); await load() } catch (error) { ElMessage.error(error.message) } }
onMounted(async () => { try { const response = await teachingCenterOfferings(); offerings.value = response.data || []; await load() } catch (error) { ElMessage.error(error.message) } })
</script>

<style scoped>
.teaching-domain-page { padding: 24px; }
.page-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.page-head h1 { margin:0 0 6px; } .page-head p { margin:0; color:#7b8794; }
.el-pagination { margin-top:20px; justify-content:flex-end; }
</style>
