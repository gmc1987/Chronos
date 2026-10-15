<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  calendarBindingStatus,
  cancelRecurringMeetingSeries,
  changeRecurringMeeting,
  createRecurringMeeting,
  listMeetingRooms,
  listRecurringMeetings,
  previewRecurringMeeting,
  publishMeeting,
  recurringMeetingInstances,
  saveCalendarBinding,
} from '../../../api/admin'

const loading = ref(false)
const preview = ref(null)
const calendar = ref({ configured: false, provider: 'NONE', message: '未接入' })
const rooms = ref([])
const series = ref([])
const instances = ref([])
const selectedSeriesId = ref('')
const editing = ref(null)
const editVisible = ref(false)
const editMode = ref('THIS_ONLY')
const editForm = reactive({ title: '', agenda: '', startTime: '', endTime: '', roomId: '', recordVersion: null })
const meeting = reactive({ title: '', agenda: '', meetingType: 'ONSITE', roomId: '', joinUrl: '' })
const form = reactive({
  startTime: '',
  endTime: '',
  frequency: 'WEEKLY',
  interval: 1,
  byDay: 'MO',
  dayOfMonth: null,
  until: '',
  count: 12,
})

async function loadCalendar() {
  try {
    calendar.value = (await calendarBindingStatus())?.data || calendar.value
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '日历接入状态读取失败')
  }
}

async function loadSeries() {
  try {
    const [seriesResult, roomResult] = await Promise.all([listRecurringMeetings(), listMeetingRooms()])
    series.value = seriesResult?.data || []
    rooms.value = (roomResult?.data || []).filter(item => item.enabled)
    if (selectedSeriesId.value) await loadInstances(selectedSeriesId.value)
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '周期会议加载失败')
  }
}

async function loadInstances(id) {
  selectedSeriesId.value = id
  try { instances.value = (await recurringMeetingInstances(id))?.data || [] }
  catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '周期实例加载失败') }
}

function recurrencePayload() {
  return {
    ...form,
    until: form.until || null,
    count: form.count ? Number(form.count) : null,
    interval: Number(form.interval),
  }
}

function meetingPayload(startTime = form.startTime, endTime = form.endTime) {
  return {
    title: meeting.title.trim(), agenda: meeting.agenda,
    meetingType: meeting.meetingType, startTime, endTime,
    roomId: meeting.meetingType === 'ONLINE' ? null : meeting.roomId,
    joinUrl: meeting.meetingType === 'ONSITE' ? null : meeting.joinUrl || null,
    participantUsernames: [],
  }
}

async function createSeries() {
  if (!meeting.title.trim() || !form.startTime || !form.endTime) return ElMessage.warning('请填写会议主题和首次时间')
  if (meeting.meetingType !== 'ONLINE' && !meeting.roomId) return ElMessage.warning('请选择会议室')
  if (meeting.meetingType !== 'ONSITE' && !meeting.joinUrl) return ElMessage.warning('请填写线上加入链接')
  loading.value = true
  try {
    const response = await createRecurringMeeting({ meeting: meetingPayload(), recurrence: recurrencePayload() })
    ElMessage.success('周期会议已创建，请逐次发布需要生效的会议')
    selectedSeriesId.value = response?.data?.meeting?.id || ''
    await loadSeries()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '周期会议创建失败') }
  finally { loading.value = false }
}

function openEdit(row) {
  editing.value = row
  editMode.value = 'THIS_ONLY'
  Object.assign(editForm, {
    title: row.meeting.title, agenda: row.meeting.agenda || '',
    startTime: row.meeting.startTime, endTime: row.meeting.endTime,
    roomId: row.meeting.roomId || '', recordVersion: row.meeting.recordVersion,
  })
  editVisible.value = true
}

async function saveEdit() {
  const source = editing.value?.meeting
  if (!source || !editForm.title.trim() || !editForm.startTime || !editForm.endTime) return ElMessage.warning('请填写有效的会议内容')
  loading.value = true
  try {
    await changeRecurringMeeting(source.id, {
      mode: editMode.value,
      meeting: {
        ...meetingPayload(editForm.startTime, editForm.endTime),
        title: editForm.title.trim(), agenda: editForm.agenda,
        meetingType: source.meetingType, roomId: editForm.roomId || null,
        joinUrl: source.joinUrl, recordVersion: editForm.recordVersion,
        participantUsernames: (editing.value.participants || []).map(item => item.username),
      },
    })
    editVisible.value = false
    ElMessage.success('周期例外已保存')
    await loadInstances(selectedSeriesId.value)
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '周期例外保存失败') }
  finally { loading.value = false }
}

async function publishInstance(row) {
  try {
    await publishMeeting(row.meeting.id)
    ElMessage.success('周期实例已发布')
    await loadInstances(selectedSeriesId.value)
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '实例发布失败') }
}

async function cancelSeries() {
  if (!selectedSeriesId.value) return
  try {
    await cancelRecurringMeetingSeries(selectedSeriesId.value)
    ElMessage.success('未来周期实例已取消')
    await loadSeries()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '周期取消失败') }
}

async function showPreview() {
  if (!form.startTime || !form.endTime || (!form.until && !form.count)) {
    return ElMessage.warning('请填写起止时间及结束日期或次数')
  }
  loading.value = true
  try {
    preview.value = (await previewRecurringMeeting(recurrencePayload()))?.data
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '周期实例预览失败')
  } finally {
    loading.value = false
  }
}

async function disableCalendar() {
  try {
    calendar.value = await saveCalendarBinding({
      provider: 'NONE',
      externalCalendarId: 'local',
      enabled: false,
    }).then(response => response?.data || calendar.value)
    ElMessage.success('已切换为本地会议，外部日历未接入')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '日历设置失败')
  }
}

onMounted(() => { loadCalendar(); loadSeries() })
</script>

<template>
  <div class="page">
    <header>
      <div>
        <h2>周期会议与日历</h2>
        <p>周期实例写入现有会议中心；未配置 provider 时仍可正常使用本地会议。</p>
      </div>
      <el-button @click="disableCalendar">使用本地日历</el-button>
    </header>

    <el-alert
      :title="calendar.configured ? `外部日历：${calendar.provider}（已配置）` : '外部日历未接入，本地会议不受影响'"
      :type="calendar.configured ? 'success' : 'info'"
      :closable="false"
    />
    <el-form label-width="110px" class="form">
      <el-form-item label="会议主题"><el-input v-model="meeting.title" maxlength="200" /></el-form-item>
      <el-form-item label="议程"><el-input v-model="meeting.agenda" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="会议形式"><el-radio-group v-model="meeting.meetingType"><el-radio value="ONSITE">线下</el-radio><el-radio value="ONLINE">线上</el-radio><el-radio value="HYBRID">混合</el-radio></el-radio-group></el-form-item>
      <el-form-item v-if="meeting.meetingType !== 'ONLINE'" label="会议室"><el-select v-model="meeting.roomId" placeholder="选择会议室"><el-option v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" /></el-select></el-form-item>
      <el-form-item v-if="meeting.meetingType !== 'ONSITE'" label="加入链接"><el-input v-model="meeting.joinUrl" /></el-form-item>
    </el-form>
    <el-form label-width="110px" class="form">
      <el-form-item label="首次开始">
        <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
      </el-form-item>
      <el-form-item label="首次结束">
        <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
      </el-form-item>
      <el-form-item label="规则">
        <el-select v-model="form.frequency" style="width: 150px">
          <el-option label="每周" value="WEEKLY" />
          <el-option label="每月" value="MONTHLY" />
        </el-select>
        <el-input-number v-model="form.interval" :min="1" :max="52" />
      </el-form-item>
      <el-form-item v-if="form.frequency === 'WEEKLY'" label="星期">
        <el-input v-model="form.byDay" placeholder="MO,WE,FR" />
      </el-form-item>
      <el-form-item v-if="form.frequency === 'MONTHLY'" label="每月日期">
        <el-input-number v-model="form.dayOfMonth" :min="1" :max="31" />
      </el-form-item>
      <el-form-item label="结束日期">
        <el-date-picker v-model="form.until" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable />
      </el-form-item>
      <el-form-item label="或实例次数">
        <el-input-number v-model="form.count" :min="1" :max="400" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="showPreview">预览实例</el-button>
      <el-button :loading="loading" @click="createSeries">创建周期会议</el-button>
    </el-form>
    <el-table v-if="preview" :data="preview.occurrences" border class="preview">
      <el-table-column prop="ordinal" label="#" width="70" />
      <el-table-column prop="startTime" label="开始时间" />
      <el-table-column prop="endTime" label="结束时间" />
      <el-table-column prop="occurrenceKey" label="occurrence_key" />
    </el-table>
    <h3>我的周期会议</h3>
    <el-table :data="series" border>
      <el-table-column prop="meeting.title" label="会议主题" min-width="220" />
      <el-table-column prop="meeting.startTime" label="首次时间" width="190" />
      <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="loadInstances(row.meeting.id)">查看实例</el-button></template></el-table-column>
    </el-table>
    <div v-if="selectedSeriesId" class="instances-header"><h3>周期实例</h3><el-button type="danger" plain @click="cancelSeries">取消未来实例</el-button></div>
    <el-table v-if="selectedSeriesId" :data="instances" border>
      <el-table-column prop="meeting.startTime" label="开始时间" width="190" />
      <el-table-column prop="meeting.endTime" label="结束时间" width="190" />
      <el-table-column prop="meeting.status" label="状态" width="130" />
      <el-table-column prop="meeting.exceptionType" label="例外" width="110" />
      <el-table-column label="操作" min-width="210"><template #default="{ row }">
        <el-button v-if="row.meeting.status === 'DRAFT'" link type="primary" @click="publishInstance(row)">发布</el-button>
        <el-button v-if="!['CANCELLED', 'COMPLETED'].includes(row.meeting.status)" link type="primary" @click="openEdit(row)">调整</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="editVisible" title="调整周期实例" width="600px">
      <el-form label-width="110px">
        <el-form-item label="调整范围"><el-radio-group v-model="editMode"><el-radio value="THIS_ONLY">仅本次</el-radio><el-radio value="THIS_AND_FUTURE">本次及以后</el-radio></el-radio-group></el-form-item>
        <el-form-item label="会议主题"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="议程"><el-input v-model="editForm.agenda" type="textarea" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="editForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="editForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item v-if="editing?.meeting?.meetingType !== 'ONLINE'" label="会议室"><el-select v-model="editForm.roomId"><el-option v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="loading" @click="saveEdit">保存调整</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.form { max-width: 720px; margin-top: 18px; }
.instances-header { display: flex; align-items: center; justify-content: space-between; }
.form .el-input-number, .form .el-select { margin-left: 10px; }
.preview { margin-top: 20px; }
</style>
