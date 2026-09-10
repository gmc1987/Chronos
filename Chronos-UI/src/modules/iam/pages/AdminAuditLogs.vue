<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">审计中心</div>
        <div class="subtitle">按业务域核查关键操作，审计记录只读且不可在此页面删除</div>
      </div>
    </div>

    <div class="query-bar">
      <el-select v-model="filters.domain" style="width: 150px" @change="search">
        <el-option label="全部业务" value="ALL" />
        <el-option label="智慧校园" value="EDUCATION" />
        <el-option label="流程中心" value="WORKFLOW" />
        <el-option label="知识中心" value="KNOWLEDGE" />
        <el-option label="通知公告" value="MESSAGE" />
        <el-option label="身份权限" value="IAM" />
      </el-select>
      <el-input v-model="filters.username" clearable placeholder="操作人" @keyup.enter="search" />
      <el-input v-model="filters.action" clearable placeholder="动作编码" @keyup.enter="search" />
      <el-input v-model="filters.keyword" clearable placeholder="详情关键词" @keyup.enter="search" />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button
        v-permission="['iam:audit:export']"
        :loading="exporting"
        @click="exportRows"
      >
        导出 CSV
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="createTime" label="发生时间" width="190" />
      <el-table-column prop="username" label="操作人" width="170" />
      <el-table-column prop="action" label="动作编码" min-width="230" />
      <el-table-column prop="detail" label="操作详情" min-width="360" show-overflow-tooltip />
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[20, 50, 100]"
        @current-change="load"
        @size-change="changePageSize"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { auditLogs, exportAuditLogs } from '../api'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const timeRange = ref([])
const filters = reactive({
  domain: 'EDUCATION',
  username: '',
  action: '',
  keyword: '',
})

const load = async () => {
  loading.value = true
  try {
    const response = await auditLogs({
      ...filters,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
      page: page.value - 1,
      size: size.value,
    })
    rows.value = response?.data?.content || []
    total.value = response?.data?.totalElements || 0
  } catch (error) {
    ElMessage.error(error?.message || '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  load()
}

const reset = () => {
  filters.domain = 'EDUCATION'
  filters.username = ''
  filters.action = ''
  filters.keyword = ''
  timeRange.value = []
  page.value = 1
  load()
}

const changePageSize = () => {
  page.value = 1
  load()
}

const exportRows = async () => {
  exporting.value = true
  try {
    const blob = await exportAuditLogs({
      ...filters,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'chronos-audit.csv'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 0)
    ElMessage.success('审计日志已导出')
  } catch (error) {
    ElMessage.error(error?.message || '审计日志导出失败')
  } finally {
    exporting.value = false
  }
}

load()
</script>

<style scoped>
.header {
  margin-bottom: 16px;
}

.query-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.query-bar .el-input {
  width: 180px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
