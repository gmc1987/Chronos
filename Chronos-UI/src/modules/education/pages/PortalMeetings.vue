<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalMeetings, respondToMeeting } from '../../../api/portal'

const meetings = ref([])
const loading = ref(false)
const filter = ref('UPCOMING')
const statusLabels = {
  DRAFT: '草稿', PENDING_ROOM: '场地待审批', REJECTED: '已驳回',
  PUBLISHED: '已发布', CANCELLED: '已取消', COMPLETED: '已完成',
}
const responseLabels = { INVITED: '待确认', ACCEPTED: '已接受', DECLINED: '已谢绝', LEAVE: '已请假' }
const filtered = computed(() => {
  if (filter.value === 'ALL') return meetings.value
  const now = Date.now()
  return meetings.value.filter(item => {
    const upcoming = new Date(item.meeting.endTime).getTime() >= now
      && item.meeting.status !== 'CANCELLED'
    return filter.value === 'UPCOMING' ? upcoming : !upcoming
  })
})

async function load() {
  loading.value = true
  try {
    const response = await portalMeetings()
    meetings.value = response?.data || []
  } finally {
    loading.value = false
  }
}

function myParticipant(view) {
  return view.currentParticipant
}

async function respond(view, status) {
  let comment = ''
  if (status !== 'ACCEPTED') {
    try {
      const result = await ElMessageBox.prompt(
        status === 'LEAVE' ? '请填写请假原因' : '可填写谢绝原因',
        status === 'LEAVE' ? '会议请假' : '谢绝邀请',
        { inputValidator: value => status !== 'LEAVE' || Boolean(value?.trim()) || '请假原因不能为空' },
      )
      comment = result.value
    } catch {
      return
    }
  }
  try {
    await respondToMeeting(view.meeting.id, { status, comment })
    ElMessage.success('参会状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header>
      <div><h2>我的会议</h2><p>查看我组织或参加的会议，反馈参会状态并进入线上会议。</p></div>
      <el-radio-group v-model="filter">
        <el-radio-button value="UPCOMING">即将开始</el-radio-button>
        <el-radio-button value="HISTORY">历史/取消</el-radio-button>
        <el-radio-button value="ALL">全部</el-radio-button>
      </el-radio-group>
    </header>

    <el-empty v-if="!loading && !filtered.length" description="暂无会议" />
    <el-card v-for="view in filtered" :key="view.meeting.id" v-loading="loading" class="meeting-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div><strong>{{ view.meeting.title }}</strong><el-tag>{{ statusLabels[view.meeting.status] || view.meeting.status }}</el-tag></div>
          <span>{{ view.meeting.startTime }} — {{ view.meeting.endTime }}</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="组织者">{{ view.meeting.organizerUsername }}</el-descriptions-item>
        <el-descriptions-item label="会议形式">{{ view.meeting.meetingType }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ view.room ? `${view.room.roomName} ${view.room.location || ''}` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="我的反馈">{{ responseLabels[myParticipant(view)?.responseStatus] || '组织者' }}</el-descriptions-item>
        <el-descriptions-item label="议程" :span="2">{{ view.meeting.agenda || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="view.meeting.status === 'PUBLISHED'" class="actions">
        <a v-if="view.meeting.joinUrl" :href="view.meeting.joinUrl" target="_blank" rel="noopener noreferrer">
          <el-button type="primary">进入线上会议</el-button>
        </a>
        <template v-if="myParticipant(view)">
          <el-button type="success" @click="respond(view, 'ACCEPTED')">接受</el-button>
          <el-button @click="respond(view, 'DECLINED')">谢绝</el-button>
          <el-button type="warning" @click="respond(view, 'LEAVE')">请假</el-button>
        </template>
      </div>
      <el-alert v-if="view.meeting.status === 'CANCELLED'" :title="`取消原因：${view.meeting.cancelReason}`" type="warning" :closable="false" />
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header, .card-header, .card-header > div, .actions { display: flex; align-items: center; }
header, .card-header { justify-content: space-between; }
header { margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.meeting-card { margin-bottom: 14px; }
.card-header, .card-header > div, .actions { gap: 10px; }
.actions { margin-top: 14px; justify-content: flex-end; }
a { text-decoration: none; }
</style>
