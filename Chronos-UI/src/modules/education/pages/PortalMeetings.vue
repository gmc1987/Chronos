<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalMeetings, respondToMeeting, checkInMeeting, updateMeetingActionStatus } from '../../../api/portal'
import { downloadManagedFile } from '../../../api/admin'

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

async function checkIn(view) {
  try {
    await checkInMeeting(view.meeting.id)
    ElMessage.success('签到成功')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '签到失败')
  }
}

async function updateAction(view, item, status) {
  try {
    await updateMeetingActionStatus(view.meeting.id, item.id, {
      status,
      recordVersion: item.recordVersion,
    })
    ElMessage.success('行动项状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '状态更新失败')
  }
}

async function downloadMaterial(item) {
  try {
    const blob = await downloadManagedFile(item.fileId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.title
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '材料下载失败')
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
          <el-button :disabled="!!myParticipant(view)?.checkedInAt" @click="checkIn(view)">{{ myParticipant(view)?.checkedInAt ? '已签到' : '签到' }}</el-button>
        </template>
      </div>
      <el-collapse v-if="view.materials?.length || view.minutes || view.actionItems?.length" class="archive">
        <el-collapse-item v-if="view.materials?.length" title="会议材料">
          <el-button v-for="item in view.materials" :key="item.id" link type="primary" @click="downloadMaterial(item)">{{ item.title }}</el-button>
        </el-collapse-item>
        <el-collapse-item v-if="view.minutes" title="会议纪要与决议">
          <p class="minutes">{{ view.minutes.content }}</p>
          <p v-if="view.minutes.decisionsText" class="minutes"><strong>会议决议：</strong>{{ view.minutes.decisionsText }}</p>
        </el-collapse-item>
        <el-collapse-item v-if="view.actionItems?.length" title="行动项">
          <el-table :data="view.actionItems" border>
            <el-table-column prop="title" label="行动项" />
            <el-table-column prop="assigneeUsername" label="责任人" width="140" />
            <el-table-column prop="dueAt" label="截止时间" width="180" />
            <el-table-column label="状态" width="190">
              <template #default="scope">
                <el-select v-if="scope.row.assigneeUsername === view.currentUsername || view.meeting.organizerUsername === view.currentUsername" :model-value="scope.row.status" @change="value => updateAction(view, scope.row, value)">
                  <el-option label="待处理" value="OPEN" /><el-option label="处理中" value="IN_PROGRESS" /><el-option label="已完成" value="DONE" />
                </el-select>
                <span v-else>{{ scope.row.status }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
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
.archive { margin-top: 14px; }
.minutes { white-space: pre-wrap; color: #4f5b66; }
a { text-decoration: none; }
</style>
