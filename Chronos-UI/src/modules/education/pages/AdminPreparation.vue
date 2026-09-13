<template><TeachingDomainPage title="备课" description="围绕主题组织活动、成员与备课材料" entity-label="备课记录" resource-type="PREPARATION" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ topic: '', activities: '', members: '', materials: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'topic', label: '主题', minWidth: 180 }, { prop: 'activities', label: '活动', minWidth: 180 }, { prop: 'members', label: '成员', minWidth: 150 }, { prop: 'materials', label: '材料', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'topic', label: '主题' }, { prop: 'activities', label: '活动', type: 'textarea' }, { prop: 'members', label: '成员' }, { prop: 'materials', label: '材料', type: 'textarea' }]
const filterFields = [{ prop: 'topic', label: '主题' }, { prop: 'members', label: '成员' }]
</script>
