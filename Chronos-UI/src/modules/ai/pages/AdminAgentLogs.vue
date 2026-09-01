<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">执行监控</div>
        <div class="subtitle">Agent/模型任务日志</div>
      </div>
      <div class="actions">
        <el-input v-model="taskId" placeholder="任务ID" class="search-input" @keyup.enter="load" />
        <el-input v-model="logType" placeholder="日志类型" style="width: 140px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="taskId" label="任务ID" width="220" />
      <el-table-column prop="logType" label="类型" width="120" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" @click="openDetail(scope.row)">查看</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDetail" title="日志详情" width="640px" class="dark-dialog">
      <div class="detail-block">
        <div class="detail-line"><strong>任务ID：</strong>{{ detail?.taskId }}</div>
        <div class="detail-line"><strong>类型：</strong>{{ detail?.logType }}</div>
        <div class="detail-line"><strong>时间：</strong>{{ detail?.createTime }}</div>
        <div class="detail-content">{{ detail?.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { listTaskLogs, deleteTaskLog } from '../api'

const items = ref([])
const taskId = ref('')
const logType = ref('')

const showDetail = ref(false)
const detail = ref(null)

const load = async () => {
  const res = await listTaskLogs({ taskId: taskId.value, logType: logType.value })
  items.value = res?.data || []
}

const openDetail = (row) => {
  detail.value = row
  showDetail.value = true
}

const remove = async (row) => {
  await deleteTaskLog(row.id)
  load()
}

load()
</script>
