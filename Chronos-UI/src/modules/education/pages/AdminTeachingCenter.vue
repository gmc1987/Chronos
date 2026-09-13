<template>
  <section class="admin-teaching-page">
    <div class="admin-teaching-header">
      <div>
        <h1>{{ currentTypeLabel }}</h1>
        <p>教学中心后台管理</p>
      </div>
    </div>
    <el-alert type="info" :closable="false"
      description="教学中心已按业务领域拆分，请从左侧菜单进入教学计划、教案、备课、资源、题库、知识点、教研或错题页面。" />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const labels = {
  PLAN: '教学计划',
  LESSON_PLAN: '教案管理',
  PREPARATION: '备课管理',
  COURSEWARE: '课件管理',
  MATERIAL: '教学材料',
  HOMEWORK: '作业入口',
  QUESTION_BANK: '题库维护',
  KNOWLEDGE_POINT: '知识点维护',
  MISTAKE: '错题维护',
  RESEARCH: '教研管理',
}
const currentTypeLabel = computed(() => labels[route.meta.teachingType] || '教学中心')
</script>

<style scoped>
.admin-teaching-page {
  min-height: 100%;
}

.admin-teaching-header {
  padding: 20px 24px 0;
}

.admin-teaching-header h1 {
  margin: 0;
  font-size: 22px;
}

.admin-teaching-header p {
  margin: 6px 0 0;
  color: #7b8794;
}
</style>
