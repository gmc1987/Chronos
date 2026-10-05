<script setup>
import { onMounted, ref } from 'vue'
import { portalCommunicationRecords, portalFamilyChildren } from '../../../api/portal'

const records = ref([])
const loading = ref(false)
const children = ref([])
const unwrap = response => response?.data?.content || response?.data || []
const studentName = id => children.value.find(item => item.id === id)?.studentName || id || '-'
const channelName = channel => ({ PHONE: '电话', IN_PERSON: '面谈', ONLINE: '线上', OTHER: '其他' }[channel] || channel || '-')
async function load() {
  loading.value = true
  try {
    const [recordResponse, childResponse] = await Promise.all([portalCommunicationRecords(), portalFamilyChildren()])
    records.value = unwrap(recordResponse)
    children.value = unwrap(childResponse)
  } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>家校沟通记录</h1><p>查看学校与家长之间的历史沟通，保证信息连续可追溯。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="学生" width="130"><template #default="{ row }">{{ studentName(row.studentId) }}</template></el-table-column>
      <el-table-column prop="teacherUsername" label="记录人" width="140" />
      <el-table-column label="沟通方式" width="120"><template #default="{ row }">{{ channelName(row.channel) }}</template></el-table-column>
      <el-table-column prop="subject" label="主题" min-width="160" />
      <el-table-column prop="content" label="沟通内容" min-width="320" show-overflow-tooltip />
      <el-table-column prop="occurredAt" label="沟通时间" width="180" />
    </el-table>
    <el-empty v-if="!loading && !records.length" description="暂无沟通记录" />
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h1 { margin: 0 0 8px; } p { margin: 0; color: #84909a; }
</style>
