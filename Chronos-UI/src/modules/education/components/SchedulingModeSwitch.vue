<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDeliveryContext, hasAdminPermission } from '../../../store/auth'

const props = defineProps({
  semesterCode: { type: String, default: '' },
  runId: { type: String, default: '' },
  organizationId: { type: String, default: '' },
})
const SCHEDULING_PATH = '/admin/education/scheduling'
const AI_PATH = `${SCHEDULING_PATH}/ai`
const route = useRoute()
const router = useRouter()
const isAiMode = computed(() => route.path === AI_PATH)
const delivery = getDeliveryContext()
const canSwitch = computed(() => (delivery.mode !== 'customer' || delivery.routes?.includes(AI_PATH)) && (isAiMode.value || [
  'education:ai:agent:use',
  'education:scheduling:manage',
  'education:scheduling:ai:use',
].every(code => hasAdminPermission(code))))

const switchMode = (enabled) => {
  const semesterCode = props.semesterCode || route.query.semesterCode
  const runId = props.runId || route.query.aiRunId
  return router.push({
    path: enabled ? AI_PATH : SCHEDULING_PATH,
    query: {
      ...(props.organizationId ? { organizationId: props.organizationId } : {}),
      ...(typeof semesterCode === 'string' && semesterCode ? { semesterCode } : {}),
      ...(typeof runId === 'string' && runId ? { aiRunId: runId } : {}),
    },
  })
}
</script>

<template>
  <div v-if="canSwitch" class="scheduling-mode-switch">
    <span :class="{ current: !isAiMode }">普通排课</span>
    <el-switch
      :model-value="isAiMode"
      aria-label="切换 AI 智能排课"
      @change="switchMode"
    />
    <span :class="{ current: isAiMode }">AI 智能排课</span>
  </div>
</template>

<style scoped>
.scheduling-mode-switch { display: inline-flex; align-items: center; gap: 10px; white-space: nowrap; }
.current { color: var(--el-color-primary); font-weight: 600; }
</style>
