<template>
  <section class="homework-analysis">
    <header><h1>作业统计与分析</h1><p>按教学班查看作业提交率、评分进度和已发布成绩。</p></header>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />
    <el-form inline class="filters" @submit.prevent="load">
      <el-form-item label="教学班"><el-select v-model="offeringId" filterable placeholder="选择教学班" style="width:420px" @change="resetAndLoad"><el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="offeringLabel(item)" /></el-select></el-form-item>
      <el-button type="primary" @click="resetAndLoad">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="title" label="作业" min-width="230" />
      <el-table-column prop="dueAt" label="截止时间" width="180" />
      <el-table-column label="状态" width="100"><template #default="{row}">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="应交人数" width="100"><template #default="{row}">{{ row.audienceCount ?? '—' }}</template></el-table-column>
      <el-table-column prop="submittedCount" label="已交" width="80" />
      <el-table-column prop="gradedCount" label="已批改" width="90" />
      <el-table-column prop="publishedGradeCount" label="已发布成绩" width="110" />
      <el-table-column label="提交率" width="100"><template #default="{row}">{{ row.submissionRate == null ? '—' : `${row.submissionRate}%` }}</template></el-table-column>
      <el-table-column label="已发布成绩均分" width="140"><template #default="{row}">{{ row.averageScore ?? '—' }}</template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="当前教学班暂无作业" />
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" @change="load" />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { teachingCenterOfferings, homeworkStatistics } from '../api/teachingCenter'

const offerings=ref([]),offeringId=ref(''),rows=ref([]),loading=ref(false),loadError=ref(''),page=ref(1),size=ref(20),total=ref(0)
const unwrap=response=>response?.data?.content||response?.data||[]
const offeringLabel=item=>[item.semesterCode,item.courseName,item.teachingClassName].filter(Boolean).join(' · ')
const statusLabel=value=>({DRAFT:'草稿',PUBLISHED:'已发布',CLOSED:'已关闭',ARCHIVED:'已归档'})[value]||value
const load=async()=>{if(!offeringId.value){rows.value=[];total.value=0;return}loading.value=true;loadError.value='';try{const response=await homeworkStatistics({offeringId:offeringId.value,page:page.value-1,size:size.value});rows.value=unwrap(response);total.value=response?.data?.totalElements||0}catch(error){loadError.value=error.message||'统计加载失败'}finally{loading.value=false}}
const resetAndLoad=()=>{page.value=1;load()}
onMounted(async()=>{try{offerings.value=unwrap(await teachingCenterOfferings());offeringId.value=offerings.value[0]?.id||'';await load()}catch(error){loadError.value=error.message||'教学班加载失败'}})
</script>

<style scoped>
.homework-analysis{padding:24px}.homework-analysis header{margin-bottom:18px}.homework-analysis h1{margin:0 0 6px}.homework-analysis p{margin:0;color:#667085}.filters{margin-bottom:10px}.el-pagination{margin-top:18px;justify-content:flex-end}.el-alert{margin-bottom:16px}
</style>
