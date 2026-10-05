<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listParentMeetings, respondParentMeeting } from '../../../api/admin'

const meetings = ref([])
const loading = ref(false)
const respondingId = ref('')
const unwrap = response => response?.data?.content || response?.data || []
const responseLabels = { INVITED: '待回复', ACCEPTED: '参加', DECLINED: '不参加', LEAVE: '请假' }

async function load() {
  loading.value = true
  try {
    meetings.value = unwrap(await listParentMeetings())
  } finally {
    loading.value = false
  }
}

async function respond(row, status) {
  respondingId.value = row.meeting.id
  try {
    await respondParentMeeting(row.meeting.id, { status, comment: '' })
    ElMessage.success('回复已提交')
    await load()
  } finally {
    respondingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>家长会邀请</h1><p>查看学校发给您的邀请并回复参会意向。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-table v-loading="loading" :data="meetings" border>
      <el-table-column prop="meeting.title" label="主题" min-width="200" />
      <el-table-column prop="meeting.agenda" label="议程" min-width="220" show-overflow-tooltip />
      <el-table-column prop="meeting.startTime" label="开始时间" min-width="170" />
      <el-table-column prop="meeting.endTime" label="结束时间" min-width="170" />
      <el-table-column label="形式" width="100"><template #default="{ row }">{{ { ONLINE: '线上', ONSITE: '线下', HYBRID: '混合' }[row.meeting.meetingType] || row.meeting.meetingType }}</template></el-table-column>
      <el-table-column label="我的回复" width="100"><template #default="{ row }">{{ responseLabels[row.currentParticipant?.responseStatus] || '待回复' }}</template></el-table-column>
      <el-table-column label="操作" min-width="190"><template #default="{ row }">
        <div v-if="row.meeting.status === 'PUBLISHED'" class="actions">
          <el-button link type="primary" :loading="respondingId === row.meeting.id" @click="respond(row, 'ACCEPTED')">参加</el-button>
          <el-button link type="danger" :disabled="!!respondingId" @click="respond(row, 'DECLINED')">不参加</el-button>
          <el-button link :disabled="!!respondingId" @click="respond(row, 'LEAVE')">请假</el-button>
        </div>
      </template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !meetings.length" description="暂无家长会邀请" />
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h1 { margin: 0 0 8px; } p { margin: 0; color: #84909a; }
.actions { display: flex; align-items: center; white-space: nowrap; }
</style>
