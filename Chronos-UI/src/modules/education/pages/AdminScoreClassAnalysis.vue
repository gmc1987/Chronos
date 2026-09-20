<script setup>
import AdminScoreAnalysisUnavailable from './AdminScoreAnalysisUnavailable.vue'
import { getScoreClassAnalysisCapability } from '../../../api/admin'
</script>

<template>
  <AdminScoreAnalysisUnavailable
    title="班级分析"
    description="按数据范围查看班级成绩分布与及格情况；当前只展示真实后端能力状态。"
    permission="education:score:class-analysis:view"
    :load-capability="getScoreClassAnalysisCapability"
  />
</template>
