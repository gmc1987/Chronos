<script setup>
import AdminScoreAnalysisUnavailable from './AdminScoreAnalysisUnavailable.vue'
import { getScoreSubjectAnalysisCapability } from '../../../api/admin'
</script>

<template>
  <AdminScoreAnalysisUnavailable
    title="学科分析"
    description="按数据范围查看学科成绩指标；当前只展示真实后端能力状态。"
    permission="education:score:subject-analysis:view"
    :load-capability="getScoreSubjectAnalysisCapability"
  />
</template>
