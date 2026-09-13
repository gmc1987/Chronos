<template><TeachingDomainPage title="作业入口" description="按用户范围进入备课任务，独立维护作业记录" entity-label="作业" resource-type="PREPARATION" :columns="columns" :fields="fields" :defaults="{ topic: '', activities: '', members: '', materials: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'topic', label: '作业主题', minWidth: 180 }, { prop: 'activities', label: '作业活动', minWidth: 180 }, { prop: 'members', label: '用户范围', minWidth: 150 }, { prop: 'materials', label: '提交材料', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'topic', label: '作业主题' }, { prop: 'activities', label: '作业活动', type: 'textarea' }, { prop: 'members', label: '用户范围' }, { prop: 'materials', label: '提交材料', type: 'textarea' }]
</script>
