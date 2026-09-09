<template>
  <div class="notification-page">
    <div class="title">
      <div>
        <h2>流程通知</h2>
        <p>查看任务催办、即将到期、逾期和升级通知</p>
      </div>
      <div>
        <el-tag v-if="unreadCount" type="danger" class="unread">{{ unreadCount }} 条未读</el-tag>
        <el-button v-if="unreadCount" @click="readAll">全部已读</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="notifications" v-loading="loading" row-key="id">
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.readAt ? 'info' : 'danger'">{{ row.readAt ? '已读' : '未读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="210" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="createTime" label="时间" width="190" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="!row.readAt" link type="primary" @click="markRead(row)">标记已读</el-button>
          <el-button v-if="row.instanceId" link @click="$router.push(`/portal/workflow-instances/${row.instanceId}/forms`)">查看流程</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listWorkflowNotifications,
  readAllWorkflowNotifications,
  readWorkflowNotification,
  workflowNotificationUnreadCount
} from '../../../api/admin'

const loading = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const load = async () => {
  loading.value = true
  try {
    const [listResponse, countResponse] = await Promise.all([
      listWorkflowNotifications({ page: page.value - 1, size: pageSize.value }),
      workflowNotificationUnreadCount()
    ])
    notifications.value = listResponse?.data?.content || listResponse?.data || []
    total.value = listResponse?.data?.totalElements ?? notifications.value.length
    unreadCount.value = countResponse?.data || 0
  } finally {
    loading.value = false
  }
  const changePageSize = () => { page.value = 1; load() }
}

const readAll = async () => {
  await readAllWorkflowNotifications()
  ElMessage.success('全部通知已标记为已读')
  await load()
}

const markRead = async row => {
  await readWorkflowNotification(row.id)
  ElMessage.success('已标记为已读')
  await load()
}

onMounted(load)
</script>

<style scoped>
.notification-page { background: #fff; border-radius: 12px; padding: 24px; }
.title { display: flex; justify-content: space-between; align-items: center; }
.title h2 { margin: 0; }
.title p { color: #8492a6; margin: 8px 0 20px; }
.unread { margin-right: 12px; }
</style>
