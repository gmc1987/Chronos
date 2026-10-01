<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  calendarBindingStatus,
  previewRecurringMeeting,
  saveCalendarBinding,
} from '../../../api/admin'

const loading = ref(false)
const preview = ref(null)
const calendar = ref({ configured: false, provider: 'NONE', message: '未接入' })
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

async function showPreview() {
  if (!form.startTime || !form.endTime || (!form.until && !form.count)) {
    return ElMessage.warning('请填写起止时间及结束日期或次数')
  }
  loading.value = true
  try {
    preview.value = (await previewRecurringMeeting({
      ...form,
      until: form.until || null,
      count: form.count ? Number(form.count) : null,
      interval: Number(form.interval),
    }))?.data
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

onMounted(loadCalendar)
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
    </el-form>
    <el-table v-if="preview" :data="preview.occurrences" border class="preview">
      <el-table-column prop="ordinal" label="#" width="70" />
      <el-table-column prop="startTime" label="开始时间" />
      <el-table-column prop="endTime" label="结束时间" />
      <el-table-column prop="occurrenceKey" label="occurrence_key" />
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.form { max-width: 720px; margin-top: 18px; }
.form .el-input-number, .form .el-select { margin-left: 10px; }
.preview { margin-top: 20px; }
</style>
