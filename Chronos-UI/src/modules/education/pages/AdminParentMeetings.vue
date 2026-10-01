<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createParentMeeting } from '../../../api/admin'

const busy = ref(false)
const form = reactive({
  title: '', agenda: '', scopeType: 'CLASS', scopeId: '', meetingType: 'ONLINE',
  startTime: '', endTime: '', joinUrl: '', roomId: '',
})

async function save() {
  if (!form.title || !form.scopeId || !form.startTime || !form.endTime) {
    ElMessage.warning('请填写主题、范围和起止时间')
    return
  }
  busy.value = true
  try {
    await createParentMeeting({ ...form, roomId: form.meetingType === 'ONLINE' ? null : form.roomId })
    ElMessage.success('家长会已创建，系统已按监护关系生成邀请')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '创建失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <header><div><h2>家长会管理</h2><p>按班级、年级或学生范围邀请有效监护人，复用会议签到、纪要和行动项。</p></div></header>
    <el-card>
      <el-form label-width="110px" @submit.prevent="save">
        <el-form-item label="会议主题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="会议范围">
          <el-select v-model="form.scopeType"><el-option label="班级" value="CLASS" /><el-option label="年级" value="GRADE" /><el-option label="学生" value="STUDENT" /></el-select>
          <el-input v-model="form.scopeId" placeholder="请输入范围 ID" style="width: 320px; margin-left: 12px" />
        </el-form-item>
        <el-form-item label="会议类型"><el-select v-model="form.meetingType"><el-option label="线上" value="ONLINE" /><el-option label="线下" value="ONSITE" /><el-option label="混合" value="HYBRID" /></el-select></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="线上链接" v-if="form.meetingType !== 'ONSITE'"><el-input v-model="form.joinUrl" /></el-form-item>
        <el-form-item label="议程"><el-input v-model="form.agenda" type="textarea" /></el-form-item>
        <el-button v-permission="['education:parent-meeting:create', 'education:parent-meeting:manage']" type="primary" :loading="busy" @click="save">创建家长会</el-button>
      </el-form>
    </el-card>
  </div>
</template>
