<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orgOptions } from '../api/admin'

const organizationId = defineModel(String)
const props = defineProps({ autoSelectSingle: Boolean, schoolOrCampusOnly: Boolean })
const options = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const response = await orgOptions()
    const rows = response.data || []
    options.value = props.schoolOrCampusOnly
      ? rows.filter(item => ['SCHOOL', 'CAMPUS'].includes(item.organizationType))
      : rows
    if (props.autoSelectSingle && !organizationId.value && options.value.length === 1) {
      organizationId.value = options.value[0].id
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error.message || '加载机构失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-select v-model="organizationId" :loading="loading" filterable placeholder="请选择所属机构" style="width: 100%">
    <el-option v-for="item in options" :key="item.id" :label="item.organizationName" :value="item.id" />
  </el-select>
</template>
