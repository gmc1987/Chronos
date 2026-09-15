<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalMyExams } from '../../../api/portal'

const exams = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    exams.value = (await portalMyExams()).data || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '考试安排加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="my-exams">
    <el-page-header @back="$router.push('/portal')">
      <template #content><strong>我的考试</strong></template>
    </el-page-header>
    <el-card shadow="never">
      <div class="section-header"><h3>已发布考试安排</h3><el-button @click="load">刷新</el-button></div>
      <el-empty v-if="!loading && !exams.length" description="目前没有已发布的考试安排" />
      <el-table v-else v-loading="loading" :data="exams" border>
        <el-table-column prop="planName" label="考试计划" min-width="150" />
        <el-table-column prop="subjectName" label="科目" min-width="120" />
        <el-table-column prop="examDate" label="日期" width="130" />
        <el-table-column prop="startTime" label="开始" width="100" />
        <el-table-column prop="endTime" label="结束" width="100" />
        <el-table-column prop="classroomName" label="考场" min-width="140" />
        <el-table-column prop="seatNo" label="座位号" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.my-exams { padding: 20px; display: grid; gap: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-header h3 { margin: 0 0 12px; }
</style>
