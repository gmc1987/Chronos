<script setup>
import AdminScoreAnalysisUnavailable from './AdminScoreAnalysisUnavailable.vue'
import { getScoreKnowledgeAnalysisCapability } from '../../../api/admin'
</script>

<template>
  <AdminScoreAnalysisUnavailable
    title="知识点分析"
    description="按已关联知识点的成绩数据查看掌握情况；当前只展示真实后端能力状态。"
    permission="education:score:knowledge-analysis:view"
    :load-capability="getScoreKnowledgeAnalysisCapability"
  />
</template>
