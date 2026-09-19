<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSupervisionPlan, listSupervisionPlans, publishSupervisionPlan, startSupervisionPlan } from '../../../api/admin'

const plans = ref([])
const loading = ref(false)
const dialog = ref(false)
const draft = ref({ name: '', startDate: '', endDate: '' })
const unwrap = response => response?.data?.content || response?.data || []
async function load() {
  loading.value = true
  try { plans.value = unwrap(await listSupervisionPlans()) } finally { loading.value = false }
}
async function save() {
  await createSupervisionPlan(draft.value)
  dialog.value = false
  draft.value = { name: '', startDate: '', endDate: '' }
  ElMessage.success('督导计划已保存')
  await load()
}
async function transition(row) {
  if (row.status === 'DRAFT') await publishSupervisionPlan(row.id)
  else if (row.status === 'PUBLISHED') await startSupervisionPlan(row.id)
  ElMessage.success('计划状态已更新')
  await load()
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>督导中心</h1><p>管理督导计划和任务，不直接调整课表。</p></div><div><el-button @click="load">刷新</el-button><el-button type="primary" @click="dialog = true">新建计划</el-button></div></header>
    <el-table v-loading="loading" :data="plans" border><el-table-column prop="name" label="计划名称" min-width="220" /><el-table-column prop="startDate" label="开始日期" /><el-table-column prop="endDate" label="结束日期" /><el-table-column prop="status" label="状态" /><el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === 'DRAFT' || row.status === 'PUBLISHED'" link type="primary" @click="transition(row)">{{ row.status === 'DRAFT' ? '发布' : '开始' }}</el-button></template></el-table-column></el-table>
    <el-empty v-if="!loading && !plans.length" description="暂无督导计划" />
    <el-dialog v-model="dialog" title="新建督导计划" width="520px"><el-form label-width="90px"><el-form-item label="名称"><el-input v-model="draft.name" maxlength="200" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="draft.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="draft.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </section>
</template>

<style scoped>.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h1 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }</style>
