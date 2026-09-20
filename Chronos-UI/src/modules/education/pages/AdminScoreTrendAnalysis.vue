<script setup>
import AdminScoreAnalysisUnavailable from './AdminScoreAnalysisUnavailable.vue'
import { getScoreTrendAnalysisCapability } from '../../../api/admin'
</script>

<template>
  <AdminScoreAnalysisUnavailable
    title="趋势分析"
    description="按已发布成绩快照查看时间趋势；当前只展示真实后端能力状态。"
    permission="education:score:trend-analysis:view"
    :load-capability="getScoreTrendAnalysisCapability"
  />
</template>
