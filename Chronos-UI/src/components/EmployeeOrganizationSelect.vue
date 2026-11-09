<script setup>
import { computed, watch } from 'vue'

const organizationId = defineModel(String)
const props = defineProps({
  organizations: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择员工所属机构' },
})

const normalized = computed(() => props.organizations.map(item => ({
  ...item,
  label: item.organizationName || item.orgName || item.name || item.id,
})))
const single = computed(() => normalized.value.length === 1)

watch(normalized, values => {
  if (!values.some(item => item.id === organizationId.value)) organizationId.value = ''
  if (values.length === 1) organizationId.value = values[0].id
}, { immediate: true })
</script>

<template>
  <el-alert
    v-if="!normalized.length"
    type="warning"
    :closable="false"
    title="当前账号没有可访问的机构"
  />
  <el-input v-else-if="single" :model-value="normalized[0].label" disabled />
  <el-select v-else v-model="organizationId" filterable :placeholder="placeholder" style="width:100%">
    <el-option v-for="item in normalized" :key="item.id" :label="item.label" :value="item.id" />
  </el-select>
</template>
