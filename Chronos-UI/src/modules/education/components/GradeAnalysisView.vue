<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  exportClassGradeAnalysis,
  exportGradeLevelAnalysis,
  exportKnowledgeGradeAnalysis,
  exportSubjectGradeAnalysis,
  getClassGradeAnalysis,
  getGradeAnalysisFilters,
  getGradeLevelAnalysis,
  getKnowledgeGradeAnalysis,
  getSubjectGradeAnalysis,
} from '../../../api/admin'

const props = defineProps({
  dimension: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
})

const loading = ref(false)
const filters = ref({ semesters: [], classes: [], grades: [], subjects: [] })
const query = ref({ semesterCode: '', classId: '', gradeId: '', courseCode: '' })
const result = ref({ summary: {}, groups: [], distribution: [] })
const unavailableReason = ref('')

const showClass = computed(() => ['CLASS', 'SUBJECT', 'KNOWLEDGE'].includes(props.dimension))
const showGrade = computed(() => ['GRADE', 'SUBJECT', 'KNOWLEDGE'].includes(props.dimension))
const groupLabel = computed(() => ({ CLASS: '班级', GRADE: '年级', SUBJECT: '学科' }[props.dimension] || '维度'))
const averageLabel = computed(() => props.dimension === 'KNOWLEDGE' ? '掌握度(%)' : '平均分')
const passLabel = computed(() => props.dimension === 'KNOWLEDGE' ? '达标率(%)' : '及格率(%)')
const excellentLabel = computed(() => props.dimension === 'KNOWLEDGE' ? '优秀掌握率(%)' : '优秀率(%)')
const exportPermission = computed(() => ({
  CLASS: 'education:score:class-analysis:export',
  GRADE: 'education:score:grade-analysis:export',
  SUBJECT: 'education:score:subject-analysis:export',
  KNOWLEDGE: 'education:score:knowledge-analysis:export',
}[props.dimension]))

async function load() {
  loading.value = true
  try {
    if (props.dimension === 'KNOWLEDGE') {
      const response = await getKnowledgeGradeAnalysis(query.value)
      result.value = response?.data || { groups: [] }
      unavailableReason.value = result.value.available === false ? result.value.reason : ''
      return
    }
    const request = {
      CLASS: getClassGradeAnalysis,
      GRADE: getGradeLevelAnalysis,
      SUBJECT: getSubjectGradeAnalysis,
    }[props.dimension]
    const response = await request(query.value)
    result.value = response?.data || { summary: {}, groups: [], distribution: [] }
  } finally {
    loading.value = false
  }
}

async function exportAnalysis() {
  const request = {
    CLASS: exportClassGradeAnalysis,
    GRADE: exportGradeLevelAnalysis,
    SUBJECT: exportSubjectGradeAnalysis,
    KNOWLEDGE: exportKnowledgeGradeAnalysis,
  }[props.dimension]
  const blob = await request(query.value)
  saveBlob(blob, `${props.title}.xlsx`)
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
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
  <section class="analysis-page" v-loading="loading">
    <header>
      <div>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <div class="header-actions">
        <el-button
          v-permission="[exportPermission]"
          :disabled="Boolean(unavailableReason)"
          @click="exportAnalysis"
        >
          导出 Excel
        </el-button>
        <el-button type="primary" @click="load">刷新分析</el-button>
      </div>
    </header>

    <el-alert
      v-if="unavailableReason"
      :title="unavailableReason"
      type="warning"
      show-icon
      :closable="false"
    />

    <el-card shadow="never" class="filters">
      <el-select v-model="query.semesterCode" clearable placeholder="全部学期" @change="load">
        <el-option v-for="item in filters.semesters" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-if="showClass" v-model="query.classId" clearable placeholder="全部班级" @change="load">
        <el-option v-for="item in filters.classes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-if="showGrade" v-model="query.gradeId" clearable placeholder="全部年级" @change="load">
        <el-option v-for="item in filters.grades" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.courseCode" clearable placeholder="全部学科" @change="load">
        <el-option v-for="item in filters.subjects" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </el-card>

    <div v-if="dimension !== 'KNOWLEDGE'" class="summary-grid">
      <el-card shadow="never"><span>统计人数</span><strong>{{ result.summary?.studentCount || 0 }}</strong></el-card>
      <el-card shadow="never"><span>平均分</span><strong>{{ result.summary?.averageScore || 0 }}</strong></el-card>
      <el-card shadow="never"><span>及格率</span><strong>{{ result.summary?.passRate || 0 }}%</strong></el-card>
      <el-card shadow="never"><span>优秀率</span><strong>{{ result.summary?.excellentRate || 0 }}%</strong></el-card>
    </div>

    <el-card v-if="!unavailableReason" shadow="never">
      <el-table :data="result.groups || []" empty-text="暂无已发布成绩数据">
        <el-table-column prop="name" :label="groupLabel" min-width="180" />
        <el-table-column prop="studentCount" label="人数" width="100" />
        <el-table-column prop="averageScore" :label="averageLabel" width="140" />
        <el-table-column prop="passRate" :label="passLabel" width="140" />
        <el-table-column prop="excellentRate" :label="excellentLabel" width="150" />
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.analysis-page { display: grid; gap: 16px; }
header { display: flex; align-items: flex-start; justify-content: space-between; }
.header-actions { display: flex; gap: 8px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: var(--el-text-color-secondary); }
.filters :deep(.el-card__body) { display: flex; flex-wrap: wrap; gap: 12px; }
.filters .el-select { width: 210px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; }
.summary-grid span { display: block; color: var(--el-text-color-secondary); }
.summary-grid strong { display: block; margin-top: 8px; font-size: 24px; }
@media (max-width: 900px) { .summary-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
