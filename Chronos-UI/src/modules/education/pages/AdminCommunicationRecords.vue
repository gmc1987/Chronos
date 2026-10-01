<script setup>
import { onMounted, ref } from 'vue'
import { listCommunicationRecords } from '../../../api/admin'

const records = ref([])
const loading = ref(false)
const unwrap = response => response?.data?.content || response?.data || []
async function load() {
  loading.value = true
  try { records.value = unwrap(await listCommunicationRecords()) } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>家校沟通记录</h2><p>记录和查询授权班级内的家校沟通，便于后续跟进。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="studentName" label="学生" width="140" />
      <el-table-column prop="parentName" label="家长" width="140" />
      <el-table-column prop="teacherName" label="沟通老师" width="140" />
      <el-table-column prop="channel" label="沟通方式" width="120" />
      <el-table-column prop="summary" label="沟通摘要" min-width="340" show-overflow-tooltip />
      <el-table-column prop="communicatedAt" label="沟通时间" width="180" />
    </el-table>
    <el-empty v-if="!loading && !records.length" description="暂无沟通记录" />
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }
</style>
