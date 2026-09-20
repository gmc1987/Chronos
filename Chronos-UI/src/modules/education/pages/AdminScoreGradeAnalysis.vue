<script setup>
import AdminScoreAnalysisUnavailable from './AdminScoreAnalysisUnavailable.vue'
import { getScoreGradeAnalysisCapability } from '../../../api/admin'
</script>

<template>
  <AdminScoreAnalysisUnavailable
    title="年级分析"
    description="按数据范围查看年级成绩统计；当前只展示真实后端能力状态。"
    permission="education:score:grade-analysis:view"
    :load-capability="getScoreGradeAnalysisCapability"
  />
</template>
