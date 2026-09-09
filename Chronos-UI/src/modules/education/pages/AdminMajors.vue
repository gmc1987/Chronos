<template><AcademicCrudPage title="专业管理" description="维护中职专业目录、学制和归属教学单位。" entity-label="专业" :columns="columns" :fields="fields" :defaults="defaults" :loader="listEducationMajors" :creator="createEducationMajor" :updater="updateEducationMajor" /></template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { createEducationMajor, listEducationMajors, updateEducationMajor } from '../../../api/admin'
const columns = [{ prop: 'majorCode', label: '专业编码' }, { prop: 'majorName', label: '专业名称' }, { prop: 'schoolingYears', label: '学制年数' }]
const fields = [...columns.map(item => ({ ...item, type: item.prop === 'schoolingYears' ? 'number' : undefined, min: 1 }))]
const defaults = { schoolingYears: 3, enabled: true }
</script>
