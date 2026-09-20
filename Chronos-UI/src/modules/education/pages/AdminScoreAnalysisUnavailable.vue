<script setup>
import { onMounted, ref } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
  permission: { type: String, required: true },
  loadCapability: { type: Function, required: true },
})

const loading = ref(false)
const loadError = ref('')
const capability = ref(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await props.loadCapability()
    capability.value = response?.data || null
  } catch (error) {
    loadError.value = error?.response?.data?.msg || error?.message || '分析能力状态加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="analysis-page">
    <header class="page-header">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-card v-loading="loading" class="capability-card">
      <template #header><span>后端分析能力</span></template>
      <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
      <el-descriptions v-else-if="capability" :column="1" border>
        <el-descriptions-item label="状态">
          <el-tag type="warning">{{ capability.supported ? 'SUPPORTED' : 'UNAVAILABLE' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据契约">{{ capability.dimension }} analysis capability</el-descriptions-item>
        <el-descriptions-item label="权限">{{ permission }}</el-descriptions-item>
        <el-descriptions-item label="说明">{{ capability.reason }}</el-descriptions-item>
        <el-descriptions-item label="后续依赖">
          <el-tag v-for="dependency in capability.dependencies || []" :key="dependency" class="dependency">
            {{ dependency }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无分析能力状态" />
    </el-card>
  </section>
</template>

<style scoped>
.analysis-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; }
.page-header p { margin: 0; color: var(--el-text-color-secondary); }
.capability-card { max-width: 920px; }
.dependency { margin: 0 8px 8px 0; }
</style>
