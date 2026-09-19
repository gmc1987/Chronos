<script setup>
import { onMounted, ref } from 'vue'
import { portalGradeDetail, portalGrades } from '../../../api/portal'

const grades = ref([])
const selected = ref(null)
const loading = ref(false)

async function loadGrades() {
  loading.value = true
  try {
    const response = await portalGrades()
    grades.value = response?.data || []
  } finally {
    loading.value = false
  }
}

async function openGrade(grade) {
  const response = await portalGradeDetail(grade.id)
  selected.value = response?.data || grade
}

onMounted(loadGrades)
</script>

<template>
  <section class="portal-grades">
    <header class="page-header">
      <div>
        <h1>我的成绩</h1>
        <p>这里只展示已发布且属于当前登录学生本人的课程成绩。</p>
      </div>
      <el-button :loading="loading" @click="loadGrades">刷新</el-button>
    </header>
    <el-empty v-if="!loading && !grades.length" description="暂无已发布成绩" />
    <el-table v-else v-loading="loading" :data="grades" stripe @row-click="openGrade">
      <el-table-column prop="courseName" label="课程" min-width="180" />
      <el-table-column prop="offeringName" label="教学班" min-width="180" />
      <el-table-column prop="totalScore" label="总评成绩" width="120" />
      <el-table-column prop="gradeLevel" label="等级" width="100" />
      <el-table-column prop="publishedAt" label="发布时间" width="180" />
    </el-table>
    <el-card v-if="selected" class="detail">
      <template #header>{{ selected.courseName || '成绩详情' }}</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总评成绩">{{ selected.totalScore }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ selected.gradeLevel }}</el-descriptions-item>
        <el-descriptions-item label="是否通过">{{ selected.passed ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="发布版本">{{ selected.versionNo }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>

<style scoped>
.portal-grades { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; }
.page-header p { margin: 0; color: var(--el-text-color-secondary); }
.detail { margin-top: 20px; }
</style>
