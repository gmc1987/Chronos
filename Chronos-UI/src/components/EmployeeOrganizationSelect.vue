<script setup>
defineProps({ organizations: { type: Array, required: true } })
const organizationId = defineModel(String)
</script>

<template>
  <el-select v-if="organizations.length > 1" v-model="organizationId" filterable
    placeholder="选择员工所属机构" aria-label="员工所属机构">
    <el-option v-for="item in organizations" :key="item.id" :label="item.organizationName" :value="item.id" />
  </el-select>
</template>
