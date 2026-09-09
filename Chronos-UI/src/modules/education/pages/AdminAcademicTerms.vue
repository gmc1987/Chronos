<template><AcademicCrudPage title="学年学期" description="维护校历范围、教学周和当前学期。" entity-label="学期" :columns="columns" :fields="fields" :defaults="defaults" :loader="listAcademicTerms" :creator="createAcademicTerm" :updater="updateAcademicTerm" /></template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { createAcademicTerm, listAcademicTerms, updateAcademicTerm } from '../../../api/admin'
const columns = [{ prop: 'termCode', label: '学期编码' }, { prop: 'termName', label: '学期名称' }, { prop: 'academicYear', label: '学年' }, { prop: 'startDate', label: '开始日期' }, { prop: 'endDate', label: '结束日期' }, { prop: 'weekCount', label: '教学周' }]
const fields = [{ prop: 'termCode', label: '学期编码' }, { prop: 'termName', label: '学期名称' }, { prop: 'academicYear', label: '学年' }, { prop: 'termNo', label: '学期序号', type: 'number', min: 1 }, { prop: 'startDate', label: '开始日期', type: 'date' }, { prop: 'endDate', label: '结束日期', type: 'date' }, { prop: 'weekCount', label: '教学周数', type: 'number', min: 1 }, { prop: 'currentTerm', label: '当前学期', type: 'boolean' }]
const defaults = { termNo: 1, weekCount: 20, currentTerm: false, status: 'ACTIVE' }
</script>
