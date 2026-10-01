<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listIntegrationConnectors } from '../../../api/admin'

const connectors = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    connectors.value = (await listIntegrationConnectors())?.data || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '集成连接器读取失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header>
      <div>
        <h2>集成中心</h2>
        <p>管理外部连接器和同步元数据；凭据只由服务端托管，不在页面回显。</p>
      </div>
    </header>
    <el-alert title="周期会议未配置外部日历 provider 时，会保留本地会议并明确返回“未接入”。" type="info" :closable="false" />
    <el-table v-loading="loading" :data="connectors" border class="table">
      <el-table-column prop="name" label="连接器" min-width="220" />
      <el-table-column prop="providerCode" label="Provider" width="180" />
      <el-table-column prop="status" label="状态" width="140" />
      <el-table-column prop="healthStatus" label="健康状态" width="140" />
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.table { margin-top: 18px; }
</style>
