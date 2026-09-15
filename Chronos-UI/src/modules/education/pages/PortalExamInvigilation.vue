<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  portalAcknowledgeInvigilation,
  portalCheckInInvigilation,
  portalMyInvigilations,
  portalMyInvigilationChanges,
  portalRequestInvigilationChange,
} from '../../../api/portal'

const duties = ref([])
const changes = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const changeForm = reactive({ assignmentId: '', reason: '' })

async function load() {
  try {
    const [dutyResult, changeResult] = await Promise.all([
      portalMyInvigilations(),
      portalMyInvigilationChanges(),
    ])
    duties.value = dutyResult.data || []
    changes.value = changeResult.data || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '监考任务加载失败')
  }
}

function requestChange(duty) {
  changeForm.assignmentId = duty.assignmentId
  changeForm.reason = ''
  dialogVisible.value = true
}

async function acknowledge(duty) {
  try {
    await portalAcknowledgeInvigilation(duty.assignmentId)
    ElMessage.success('已确认收到监考任务')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '确认失败')
  }
}

async function checkIn(duty) {
  try {
    await portalCheckInInvigilation(duty.assignmentId)
    ElMessage.success('监考报到成功')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '报到失败')
  }
}

async function submit() {
  if (!changeForm.reason.trim()) {
    ElMessage.warning('请填写不能参加监考的原因')
    return
  }
  submitting.value = true
  try {
    await portalRequestInvigilationChange(changeForm.assignmentId, {
      reason: changeForm.reason.trim(),
      proposedTeacherId: null,
    })
    dialogVisible.value = false
    ElMessage.success('调换申请已提交，等待考务管理员处理')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '调换申请提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="my-invigilation">
    <el-page-header @back="$router.push('/portal')">
      <template #content><strong>我的监考</strong></template>
    </el-page-header>
    <el-alert
      title="监考安排以已发布考试为准；请在考前 90 分钟至开考后 15 分钟报到，无法到场请提交调换申请。"
      type="info"
      :closable="false" />
    <el-card shadow="never">
      <div class="section-header"><h3>监考任务</h3><el-button @click="load">刷新</el-button></div>
      <el-empty v-if="!duties.length" description="目前没有监考任务" />
      <el-table v-else :data="duties" border>
        <el-table-column prop="planName" label="考试计划" min-width="150" />
        <el-table-column prop="subjectName" label="科目" min-width="120" />
        <el-table-column prop="examDate" label="日期" width="130" />
        <el-table-column prop="startTime" label="开始" width="100" />
        <el-table-column prop="endTime" label="结束" width="100" />
        <el-table-column prop="classroomName" label="考场" min-width="130" />
        <el-table-column label="岗位" width="110">
          <template #default="scope">{{ scope.row.assignmentStatus === 'STANDBY' ? '机动待命' : scope.row.dutyRole === 'CHIEF' ? '主监考' : '副监考' }}</template>
        </el-table-column>
        <el-table-column label="确认状态" width="120">
          <template #default="scope">{{ scope.row.acknowledgedAt ? '已确认' : '待确认' }}</template>
        </el-table-column>
        <el-table-column label="报到状态" width="120">
          <template #default="scope">{{ scope.row.checkedInAt ? '已报到' : '未报到' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button v-if="!scope.row.acknowledgedAt" link @click="acknowledge(scope.row)">确认收到</el-button>
            <el-button v-if="!scope.row.checkedInAt" link type="primary" @click="checkIn(scope.row)">报到</el-button>
            <el-button v-if="scope.row.assignmentStatus === 'ASSIGNED'" link @click="requestChange(scope.row)">申请调换</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card shadow="never">
      <h3>我的调换申请</h3>
      <el-table :data="changes" border>
        <el-table-column prop="createTime" label="申请时间" min-width="180" />
        <el-table-column prop="reason" label="原因" min-width="220" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="decidedAt" label="处理时间" min-width="180" />
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" title="申请调换监考" width="520px">
      <el-input
        v-model="changeForm.reason"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="请说明不能参加监考的原因" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.my-invigilation { padding: 20px; display: grid; gap: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-header h3 { margin: 0 0 12px; }
</style>
