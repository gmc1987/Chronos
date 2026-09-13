<template><TeachingDomainPage title="错题" description="记录错题来源、原因、解析和纠正方案" entity-label="错题" resource-type="MISTAKE" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ source: '', reason: '', analysis: '', correction: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'name', label: '题目', minWidth: 180 }, { prop: 'source', label: '来源', minWidth: 140 }, { prop: 'reason', label: '原因', minWidth: 180 }, { prop: 'analysis', label: '解析', minWidth: 200 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'name', label: '题目' }, { prop: 'source', label: '来源' }, { prop: 'reason', label: '错误原因', type: 'textarea' }, { prop: 'analysis', label: '解析', type: 'textarea' }, { prop: 'correction', label: '纠正方案', type: 'textarea' }]
const filterFields = [{ prop: 'source', label: '来源' }, { prop: 'reason', label: '原因' }]
</script>
