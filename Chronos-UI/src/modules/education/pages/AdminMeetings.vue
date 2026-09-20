<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelMeeting,
  createMeeting,
  decideMeetingRoom,
  deleteMeeting,
  listMeetingRooms,
  listMeetingParticipantOptions,
  pageMeetings,
  publishMeeting,
  updateMeeting,
  uploadManagedFile,
  downloadManagedFile,
  addMeetingMaterial,
  deleteMeetingMaterial,
  saveMeetingMinutes,
  publishMeetingMinutes,
  addMeetingActionItem,
} from '../../../api/admin'

const meetings = ref([])
const rooms = ref([])
const users = ref([])
const dialogVisible = ref(false)
const busy = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ keyword: '', status: '' })
const form = reactive({})
const executionVisible = ref(false)
const activeMeeting = ref(null)
const minutesForm = reactive({ content: '', decisionsText: '', recordVersion: null })
const actionForm = reactive({ title: '', description: '', assigneeUsername: '', dueAt: null })
const unwrap = response => response?.data?.content || response?.data || []
const typeLabels = { ONSITE: '线下会议', ONLINE: '线上会议', HYBRID: '混合会议' }
const statusLabels = {
  DRAFT: '草稿', PENDING_ROOM: '会议室待审批', REJECTED: '已驳回',
  PUBLISHED: '已发布', CANCELLED: '已取消', COMPLETED: '已完成',
}
const needsRoom = computed(() => ['ONSITE', 'HYBRID'].includes(form.meetingType))
const needsOnline = computed(() => ['ONLINE', 'HYBRID'].includes(form.meetingType))

function toPicker(value) {
  return value ? String(value).slice(0, 16) : ''
}

function resetForm(view) {
  Object.keys(form).forEach(key => delete form[key])
  const meeting = view?.meeting
  Object.assign(form, meeting ? {
    ...meeting,
    startTime: toPicker(meeting.startTime),
    endTime: toPicker(meeting.endTime),
    participantUsernames: view.participants.map(item => item.username),
  } : {
    meetingType: 'ONSITE',
    participantUsernames: [],
  })
}

function openDialog(view) {
  resetForm(view)
  dialogVisible.value = true
}

function openExecution(view) {
  activeMeeting.value = view
  Object.assign(minutesForm, {
    content: view.minutes?.content || '',
    decisionsText: view.minutes?.decisionsText || '',
    recordVersion: view.minutes?.recordVersion ?? null,
  })
  Object.assign(actionForm, { title: '', description: '', assigneeUsername: '', dueAt: null })
  executionVisible.value = true
}

async function uploadMaterial(file) {
  if (!activeMeeting.value) return false
  try {
    const uploaded = await uploadManagedFile(file)
    await addMeetingMaterial(activeMeeting.value.meeting.id, {
      title: file.name,
      fileId: uploaded?.data?.id,
    })
    ElMessage.success('会议材料已上传')
    await load()
    openExecution(meetings.value.find(item => item.meeting.id === activeMeeting.value.meeting.id))
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '材料上传失败')
  }
  return false
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

async function removeMaterial(item) {
  await execute(
    () => deleteMeetingMaterial(activeMeeting.value.meeting.id, item.id),
    '会议材料已删除',
  )
  openExecution(meetings.value.find(row => row.meeting.id === activeMeeting.value.meeting.id))
}

async function saveMinutes(publishAfter = false) {
  try {
    await saveMeetingMinutes(activeMeeting.value.meeting.id, { ...minutesForm })
    if (publishAfter) await publishMeetingMinutes(activeMeeting.value.meeting.id)
    ElMessage.success(publishAfter ? '会议纪要已发布' : '会议纪要草稿已保存')
    await load()
    openExecution(meetings.value.find(item => item.meeting.id === activeMeeting.value.meeting.id))
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '纪要保存失败')
  }
}

async function saveAction() {
  if (!actionForm.title || !actionForm.assigneeUsername) {
    return ElMessage.warning('请填写行动项和责任人')
  }
  try {
    await addMeetingActionItem(activeMeeting.value.meeting.id, { ...actionForm })
    ElMessage.success('行动项已添加')
    await load()
    openExecution(meetings.value.find(item => item.meeting.id === activeMeeting.value.meeting.id))
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '行动项保存失败')
  }
}

async function load() {
  busy.value = true
  try {
    const [meetingResult, roomResult, userResult] = await Promise.all([
      pageMeetings({
        keyword: filters.keyword,
        status: filters.status,
        page: page.value - 1,
        size: size.value,
      }),
      listMeetingRooms(),
      listMeetingParticipantOptions(),
    ])
    meetings.value = meetingResult?.data?.content || []
    total.value = meetingResult?.data?.totalElements || 0
    rooms.value = unwrap(roomResult).filter(item => item.enabled)
    users.value = unwrap(userResult)
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!form.title || !form.startTime || !form.endTime) {
    return ElMessage.warning('请填写会议主题和起止时间')
  }
  if (needsRoom.value && !form.roomId) return ElMessage.warning('请选择会议室')
  if (needsOnline.value && !form.joinUrl) return ElMessage.warning('请填写线上会议加入链接')
  busy.value = true
  try {
    const payload = {
      ...form,
      roomId: needsRoom.value ? form.roomId : null,
      joinUrl: needsOnline.value ? form.joinUrl : null,
      meetingProvider: needsOnline.value ? form.meetingProvider : null,
      externalMeetingId: needsOnline.value ? form.externalMeetingId : null,
      onlineAccessCode: needsOnline.value ? form.onlineAccessCode : null,
    }
    await (form.id ? updateMeeting(form.id, payload) : createMeeting(payload))
    dialogVisible.value = false
    ElMessage.success('会议已保存')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '保存失败')
  } finally {
    busy.value = false
  }
}

async function execute(action, success) {
  busy.value = true
  try {
    await action()
    ElMessage.success(success)
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

async function publish(view) {
  await execute(() => publishMeeting(view.meeting.id), '发布申请已提交')
}

async function decide(view, approve) {
  let comment = ''
  try {
    const result = await ElMessageBox.prompt(
      approve ? '可填写审批意见' : '请填写驳回原因',
      approve ? '批准会议室预约' : '驳回会议室预约',
      { inputValidator: value => approve || Boolean(value?.trim()) || '驳回时必须填写原因' },
    )
    comment = result.value
  } catch {
    return
  }
  await execute(
    () => decideMeetingRoom(view.meeting.id, { approve, comment }),
    approve ? '会议室预约已批准' : '会议室预约已驳回',
  )
}

async function cancel(view) {
  try {
    const result = await ElMessageBox.prompt('取消后将通知所有参会人，请填写原因。', '取消会议', {
      inputValidator: value => Boolean(value?.trim()) || '必须填写取消原因',
    })
    await execute(() => cancelMeeting(view.meeting.id, { reason: result.value }), '会议已取消')
  } catch {
    // 用户关闭对话框时无需提示错误。
  }
}

async function remove(view) {
  try {
    await ElMessageBox.confirm('确认删除该会议草稿？', '删除确认', { type: 'warning' })
    await execute(() => deleteMeeting(view.meeting.id), '会议草稿已删除')
  } catch {
    // 取消删除不是业务异常。
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header>
      <div><h2>会议管理</h2><p>编排线下、线上或混合会议，统一完成场地预约与参会邀请。</p></div>
      <el-button v-permission="['education:meeting:create', 'education:meeting:manage']" type="primary" @click="openDialog()">新建会议</el-button>
    </header>

    <el-alert
      title="人工审批的会议室只在批准后发送参会邀请；线上会议暂保存第三方加入链接。"
      type="info" :closable="false"
    />
    <el-form inline class="filters" @submit.prevent="page = 1; load()">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" clearable placeholder="会议主题或组织者" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 160px">
          <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="page = 1; load()">查询</el-button>
      <el-button @click="filters.keyword = ''; filters.status = ''; page = 1; load()">重置</el-button>
    </el-form>
    <el-table v-loading="busy" :data="meetings" border>
      <el-table-column label="会议" min-width="220">
        <template #default="scope">
          <strong>{{ scope.row.meeting.title }}</strong>
          <div class="muted">{{ scope.row.meeting.organizerUsername }}</div>
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="200">
        <template #default="scope">{{ scope.row.meeting.startTime }}<br>{{ scope.row.meeting.endTime }}</template>
      </el-table-column>
      <el-table-column label="形式" width="110"><template #default="scope">{{ typeLabels[scope.row.meeting.meetingType] }}</template></el-table-column>
      <el-table-column label="会议室" min-width="140"><template #default="scope">{{ scope.row.room?.roomName || '—' }}</template></el-table-column>
      <el-table-column label="参会人" width="90"><template #default="scope">{{ scope.row.participants.length }}</template></el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="scope"><el-tag>{{ statusLabels[scope.row.meeting.status] || scope.row.meeting.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="scope">
          <el-button v-if="['DRAFT', 'REJECTED', 'PUBLISHED'].includes(scope.row.meeting.status)" link type="primary" @click="openDialog(scope.row)">编辑</el-button>
          <el-button v-if="['PUBLISHED', 'COMPLETED'].includes(scope.row.meeting.status)" link type="primary" @click="openExecution(scope.row)">会议执行</el-button>
          <el-button v-if="['DRAFT', 'REJECTED'].includes(scope.row.meeting.status)" link type="success" @click="publish(scope.row)">发布</el-button>
          <template v-if="scope.row.meeting.status === 'PENDING_ROOM'">
            <el-button v-permission="['education:meeting:approve', 'education:meeting:room:manage']" link type="success" @click="decide(scope.row, true)">批准</el-button>
            <el-button v-permission="['education:meeting:approve', 'education:meeting:room:manage']" link type="warning" @click="decide(scope.row, false)">驳回</el-button>
          </template>
          <el-button v-if="['PENDING_ROOM', 'PUBLISHED'].includes(scope.row.meeting.status)" link type="danger" @click="cancel(scope.row)">取消</el-button>
          <el-button v-if="['DRAFT', 'REJECTED'].includes(scope.row.meeting.status)" link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @current-change="load"
      @size-change="page = 1; load()"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑会议' : '新建会议'" width="760px">
      <el-form label-width="110px">
        <el-form-item label="会议主题"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="议程"><el-input v-model="form.agenda" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="会议形式">
          <el-radio-group v-model="form.meetingType">
            <el-radio value="ONSITE">线下</el-radio><el-radio value="ONLINE">线上</el-radio><el-radio value="HYBRID">混合</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item v-if="needsRoom" label="会议室">
          <el-select v-model="form.roomId" filterable clearable>
            <el-option v-for="room in rooms" :key="room.id" :label="`${room.roomName}（${room.capacity}人）`" :value="room.id" />
          </el-select>
        </el-form-item>
        <template v-if="needsOnline">
          <el-form-item label="会议平台"><el-input v-model="form.meetingProvider" placeholder="腾讯会议、钉钉、Teams 等" /></el-form-item>
          <el-form-item label="第三方会议号"><el-input v-model="form.externalMeetingId" /></el-form-item>
          <el-form-item label="加入链接"><el-input v-model="form.joinUrl" placeholder="https://" /></el-form-item>
          <el-form-item label="入会密码"><el-input v-model="form.onlineAccessCode" /></el-form-item>
        </template>
        <el-form-item label="参会人">
          <el-select v-model="form.participantUsernames" multiple filterable collapse-tags :max-collapse-tags="4">
            <el-option v-for="item in users" :key="item.username" :label="`${item.displayName}（${item.username}）`" :value="item.username" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="executionVisible" title="会议执行与归档" width="900px">
      <el-tabs v-if="activeMeeting">
        <el-tab-pane label="会议材料">
          <el-upload :show-file-list="false" :before-upload="uploadMaterial">
            <el-button type="primary">上传材料</el-button>
          </el-upload>
          <el-table :data="activeMeeting.materials || []" border class="execution-table">
            <el-table-column prop="title" label="材料名称" min-width="260" />
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button link type="primary" @click="downloadMaterial(scope.row)">下载</el-button>
                <el-button link type="danger" @click="removeMaterial(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="会议纪要">
          <el-form label-width="90px">
            <el-form-item label="会议纪要"><el-input v-model="minutesForm.content" type="textarea" :rows="8" /></el-form-item>
            <el-form-item label="会议决议"><el-input v-model="minutesForm.decisionsText" type="textarea" :rows="5" /></el-form-item>
          </el-form>
          <div class="execution-actions">
            <el-button :disabled="activeMeeting.minutes?.status === 'PUBLISHED'" @click="saveMinutes(false)">保存草稿</el-button>
            <el-button type="primary" :disabled="activeMeeting.minutes?.status === 'PUBLISHED'" @click="saveMinutes(true)">保存并发布</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="行动项">
          <el-form :model="actionForm" inline>
            <el-form-item label="行动项"><el-input v-model="actionForm.title" /></el-form-item>
            <el-form-item label="责任人">
              <el-select v-model="actionForm.assigneeUsername" filterable style="width:180px">
                <el-option :label="activeMeeting.meeting.organizerUsername" :value="activeMeeting.meeting.organizerUsername" />
                <el-option v-for="item in activeMeeting.participants" :key="item.username" :label="item.username" :value="item.username" />
              </el-select>
            </el-form-item>
            <el-form-item label="截止时间"><el-date-picker v-model="actionForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-button type="primary" @click="saveAction">添加</el-button>
          </el-form>
          <el-table :data="activeMeeting.actionItems || []" border>
            <el-table-column prop="title" label="行动项" min-width="220" />
            <el-table-column prop="assigneeUsername" label="责任人" width="150" />
            <el-table-column prop="dueAt" label="截止时间" width="180" />
            <el-table-column prop="status" label="状态" width="120" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="签到记录">
          <el-table :data="activeMeeting.participants || []" border>
            <el-table-column prop="username" label="参会人" />
            <el-table-column prop="responseStatus" label="参会反馈" />
            <el-table-column prop="checkedInAt" label="签到时间" />
            <el-table-column prop="checkInMethod" label="签到方式" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p, .muted { margin: 0; color: #84909a; }
.el-alert { margin-bottom: 14px; }
.filters { margin-bottom: 6px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.execution-table { margin-top: 12px; }
.execution-actions { display: flex; justify-content: flex-end; gap: 10px; }
.el-select { width: 100%; }
</style>
