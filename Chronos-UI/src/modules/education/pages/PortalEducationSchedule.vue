<template>
  <div class="portal-schedule">
    <el-page-header @back="$router.push('/portal')">
      <template #content>
        <div>
          <strong>我的课表</strong>
          <small>{{ termName || '当前学期' }}</small>
        </div>
      </template>
    </el-page-header>

    <el-alert
      title="仅展示教务处最近发布的课表版本；未发布的调整草稿不会出现在这里。"
      type="info"
      :closable="false" />
    <el-skeleton v-if="loading" :rows="7" animated />
    <el-empty v-else-if="!schedule.length" description="当前账号没有已发布的课表" />
    <el-table v-else :data="schedule" stripe>
      <el-table-column label="上课时间" width="190">
        <template #default="scope">
          星期{{ dayName(scope.row.dayOfWeek) }} 第 {{ scope.row.periodNo }} 节
          <span v-if="scope.row.durationPeriods > 1">（连堂 {{ scope.row.durationPeriods }} 节）</span>
        </template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" min-width="170" />
      <el-table-column prop="teachingClassName" label="教学班" min-width="210" />
      <el-table-column prop="classroomName" label="教室" min-width="150" />
      <el-table-column label="状态" width="100">
        <template #default><el-tag type="success">正常</el-tag></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { portalBootstrap } from '../../../api/portal'

const loading = ref(true)
const contribution = ref({ data: {} })
const schedule = computed(() => contribution.value?.data?.mySchedule || [])
const termName = computed(() => contribution.value?.data?.termName || '')
const dayName = day => ['一', '二', '三', '四', '五', '六', '日'][Number(day) - 1] || '-'

onMounted(async () => {
  try {
    const response = await portalBootstrap()
    contribution.value = response.data?.contributions?.DATA || { data: {} }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.portal-schedule { display: grid; gap: 18px; padding: 24px; }
.portal-schedule :deep(.el-page-header__content > div) { display: grid; gap: 3px; }
.portal-schedule :deep(.el-page-header__content strong) { color: #263f49; font-size: 20px; }
.portal-schedule :deep(.el-page-header__content small) { color: #819097; font-size: 12px; }
.portal-schedule span { color: #829197; font-size: 12px; }
</style>
