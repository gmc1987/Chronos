<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useOrganizationContext } from '../composables/useOrganizationContext'

const organizationId = defineModel(String)
const props = defineProps({
  autoSelectSingle: { type: Boolean, default: true },
  schoolOrCampusOnly: Boolean,
  schoolOnly: Boolean,
  contextKey: { type: String, default: '' },
  placeholder: { type: String, default: '请选择所属机构' },
})
const route = useRoute()
const contextKey = computed(() => props.contextKey || String(route.name || route.path || 'default'))
const {
  options,
  loading,
  hasNoAccess,
  singleOrganization,
  selectedOrganization,
  selectionDisabled,
  reloadOrganizations,
} = useOrganizationContext({
  organizationId,
  contextKey,
  autoSelectSingle: props.autoSelectSingle,
  filter: item => props.schoolOnly ? item.organizationType === 'SCHOOL'
    : !props.schoolOrCampusOnly || ['SCHOOL', 'CAMPUS'].includes(item.organizationType),
})

defineExpose({ options, reloadOrganizations })
</script>

<template>
  <div class="organization-select">
    <el-alert
      v-if="hasNoAccess"
      type="warning"
      :closable="false"
      title="当前账号没有可访问的机构"
    />
    <el-input
      v-else-if="singleOrganization"
      :model-value="selectedOrganization?.organizationName || ''"
      disabled
      aria-label="当前机构"
    />
    <el-select
      v-else
      v-model="organizationId"
      :loading="loading"
      :disabled="selectionDisabled"
      filterable
      :placeholder="placeholder"
      style="width: 100%"
    >
      <el-option v-for="item in options" :key="item.id" :label="item.organizationName" :value="item.id" />
    </el-select>
  </div>
</template>

<style scoped>
.organization-select { width: 100%; }
</style>
