<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { acceptSupervisionTask, checkInSupervisionTask, portalSupervisionTasks } from '../../../api/portal'
const tasks = ref([])
const loading = ref(false)
const unwrap = response => response?.data?.content || response?.data || []
async function load() { loading.value = true; try { tasks.value = unwrap(await portalSupervisionTasks()) } finally { loading.value = false } }
async function action(row) {
  if (row.status === 'PENDING') await acceptSupervisionTask(row.id)
  else if (row.status === 'ACCEPTED') await checkInSupervisionTask(row.id)
  ElMessage.success('任务状态已更新')
  await load()
}
onMounted(load)
</script>
<template>
  <section class="page"><header><div><h1>我的督导任务</h1><p>签到时间由服务端记录，课表上下文在评价提交时冻结。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header><el-table v-loading="loading" :data="tasks" border><el-table-column prop="teacherId" label="授课教师" /><el-table-column prop="scheduleEntryId" label="课表项" /><el-table-column prop="status" label="状态" /><el-table-column prop="checkedInAt" label="签到时间" /><el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === 'PENDING' || row.status === 'ACCEPTED'" link type="primary" @click="action(row)">{{ row.status === 'PENDING' ? '接受' : '签到' }}</el-button></template></el-table-column></el-table><el-empty v-if="!loading && !tasks.length" description="暂无已分配任务" /></section>
</template>
<style scoped>.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h1 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }</style>
