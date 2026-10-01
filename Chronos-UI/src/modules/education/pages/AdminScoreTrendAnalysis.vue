<script setup>
import { onMounted, ref } from 'vue'
import {
  exportGradeTrendAnalysis,
  getGradeAnalysisFilters,
  getGradeTrendAnalysis,
} from '../../../api/admin'

const loading = ref(false)
const filters = ref({ classes: [], grades: [], subjects: [] })
const query = ref({ classId: '', gradeId: '', courseCode: '' })
const rows = ref([])

async function load() {
  loading.value = true
  try {
    const response = await getGradeTrendAnalysis(query.value)
    rows.value = response?.data || []
  } finally {
    loading.value = false
  }
}

async function exportAnalysis() {
  const blob = await exportGradeTrendAnalysis(query.value)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '成绩趋势分析.xlsx'
  anchor.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  const response = await getGradeAnalysisFilters()
  filters.value = response?.data || filters.value
  await load()
})
</script>

<template>
  <section class="trend-page" v-loading="loading">
    <header>
      <div>
        <h2>趋势分析</h2>
        <p>按学期观察已发布成绩的平均分和及格率变化。</p>
      </div>
      <div class="header-actions">
        <el-button
          v-permission="['education:score:trend-analysis:export']"
          @click="exportAnalysis"
        >
          导出 Excel
        </el-button>
        <el-button type="primary" @click="load">刷新分析</el-button>
      </div>
    </header>
    <el-card shadow="never" class="filters">
      <el-select v-model="query.classId" clearable placeholder="全部班级" @change="load"><el-option v-for="item in filters.classes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="query.gradeId" clearable placeholder="全部年级" @change="load"><el-option v-for="item in filters.grades" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="query.courseCode" clearable placeholder="全部学科" @change="load"><el-option v-for="item in filters.subjects" :key="item.value" :label="item.label" :value="item.value" /></el-select>
    </el-card>
    <el-card shadow="never"><el-table :data="rows" empty-text="暂无跨学期已发布成绩"><el-table-column prop="semesterCode" label="学期" min-width="180" /><el-table-column prop="studentCount" label="人数" /><el-table-column prop="averageScore" label="平均分" /><el-table-column prop="passRate" label="及格率(%)" /></el-table></el-card>
  </section>
</template>

<style scoped>
.trend-page { display: grid; gap: 16px; }
header { display: flex; justify-content: space-between; }
.header-actions { display: flex; gap: 8px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: var(--el-text-color-secondary); }
.filters :deep(.el-card__body) { display: flex; gap: 12px; flex-wrap: wrap; }
.filters .el-select { width: 210px; }
</style>
