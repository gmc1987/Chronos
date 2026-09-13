<template><TeachingDomainPage title="教研" description="组织教研小组、活动并沉淀成果" entity-label="教研项目" resource-type="RESEARCH" :columns="columns" :fields="fields" :defaults="{ group: '', activity: '', result: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'name', label: '教研项目', minWidth: 180 }, { prop: 'group', label: '小组', minWidth: 150 }, { prop: 'activity', label: '活动', minWidth: 200 }, { prop: 'result', label: '成果', minWidth: 220 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'name', label: '项目名称' }, { prop: 'group', label: '教研小组' }, { prop: 'activity', label: '活动', type: 'textarea' }, { prop: 'result', label: '成果', type: 'textarea' }]
</script>
