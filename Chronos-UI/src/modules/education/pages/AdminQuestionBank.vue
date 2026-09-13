<template><TeachingDomainPage title="题库" description="维护题库属性、题目、选项与解析" entity-label="题库题目" resource-type="QUESTION_BANK" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ bankProperty: '', question: '', options: '', explanation: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'name', label: '题库名称', minWidth: 160 }, { prop: 'bankProperty', label: '题库属性', minWidth: 150 }, { prop: 'question', label: '题目', minWidth: 220 }, { prop: 'options', label: '选项', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'name', label: '题库名称' }, { prop: 'bankProperty', label: '题库属性' }, { prop: 'question', label: '题目', type: 'textarea' }, { prop: 'options', label: '选项', type: 'textarea' }, { prop: 'explanation', label: '解析', type: 'textarea' }]
const filterFields = [{ prop: 'bankProperty', label: '题库属性' }, { prop: 'question', label: '题目' }]
</script>
