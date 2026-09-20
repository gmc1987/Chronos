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
          <el-tag :type="capability.supported ? 'success' : 'warning'">{{ capability.supported ? 'SUPPORTED' : 'UNAVAILABLE' }}</el-tag>
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
      <el-table v-if="capability?.supported" :data="capability.rows || []" border stripe class="analysis-table">
        <el-table-column prop="key" label="维度" min-width="160" />
        <el-table-column prop="label" label="名称" min-width="160" />
        <el-table-column prop="period" label="期间" min-width="130" />
        <el-table-column prop="studentCount" label="学生数" width="100" />
        <el-table-column prop="gradedCount" label="成绩数" width="100" />
        <el-table-column prop="averageScore" label="平均分" width="110" />
        <el-table-column prop="passRate" label="及格率(%)" width="120" />
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.analysis-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; }
.page-header p { margin: 0; color: var(--el-text-color-secondary); }
.capability-card { max-width: 920px; }
.analysis-table { margin-top: 20px; }
.dependency { margin: 0 8px 8px 0; }
</style>
