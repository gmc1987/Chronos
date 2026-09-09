<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  analyzeAcademicSchedule,
  confirmSchedulingAgentProposal,
  createSchedulingAgentProposal,
  listAcademicTerms,
  listSchedulingAgentProposals,
  rejectSchedulingAgentProposal,
} from '../../../api/admin'

const terms = ref([])
const semesterCode = ref('')
const requestText = ref('')
const proposals = ref([])
const analysis = ref(null)
const proposing = ref(false)
const analyzing = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const load = async () => {
  const response = await listAcademicTerms()
  terms.value = response.data || []
  if (!semesterCode.value) {
    const current = terms.value.find(item => item.currentTerm) || terms.value[0]
    semesterCode.value = current?.termCode || ''
  }
  await loadProposals()
}

const loadProposals = async () => {
  if (!semesterCode.value) return
  const response = await listSchedulingAgentProposals(semesterCode.value, { page: page.value - 1, size: pageSize.value })
  proposals.value = response.data?.content || response.data || []
  total.value = response.data?.totalElements ?? proposals.value.length
  analysis.value = null
}
const changePageSize = () => { page.value = 1; loadProposals() }

const propose = async () => {
  if (!requestText.value.trim()) {
    ElMessage.warning('请输入排课约束')
    return
  }
  proposing.value = true
  try {
    await createSchedulingAgentProposal({
      semesterCode: semesterCode.value,
      requestText: requestText.value.trim(),
    })
    requestText.value = ''
    ElMessage.success('已生成待确认草稿，尚未影响正式排课')
    await loadProposals()
  } finally {
    proposing.value = false
  }
}

const confirm = async row => {
  await ElMessageBox.confirm(
    `确认把“${row.teacherName} 周${row.dayOfWeek}第${row.periodNo}节”写入正式排课约束？`,
    '人工确认',
    { type: 'warning' },
  )
  await confirmSchedulingAgentProposal(row.id)
  ElMessage.success('建议已确认并写入正式约束')
  await loadProposals()
}

const reject = async row => {
  await rejectSchedulingAgentProposal(row.id)
  ElMessage.success('建议已驳回')
  await loadProposals()
}

const analyze = async () => {
  analyzing.value = true
  try {
    const response = await analyzeAcademicSchedule(semesterCode.value)
    analysis.value = response.data
  } finally {
    analyzing.value = false
  }
}

const statusType = status => ({ DRAFT: 'warning', CONFIRMED: 'success', REJECTED: 'info' })[status] || ''
const changeSemester = () => { page.value = 1; loadProposals() }

onMounted(load)
</script>

<template>
  <div class="agent-page">
    <header>
      <div>
        <h2>教育智能体</h2>
        <p>排课 Agent 生成待确认草稿，教务 Agent 只读分析课表和教师负荷。</p>
      </div>
      <el-select v-model="semesterCode" placeholder="选择学期" @change="changeSemester">
        <el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.termCode" />
      </el-select>
    </header>

    <el-tabs>
      <el-tab-pane label="Scheduling Agent">
        <el-alert
          title="Agent 只能生成草稿。确认前不会写入教师禁排或偏好配置。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="command-box">
          <el-input
            v-model="requestText"
            type="textarea"
            :rows="3"
            placeholder="例如：张老师星期三第3节不能排课；李老师周五第1节优先排课"
          />
          <el-button type="primary" :loading="proposing" @click="propose">生成约束草稿</el-button>
        </div>
        <el-table :data="proposals" border>
          <el-table-column prop="teacherName" label="教师" width="120" />
          <el-table-column prop="dayOfWeek" label="星期" width="80" />
          <el-table-column prop="periodNo" label="节次" width="80" />
          <el-table-column prop="constraintType" label="类型" width="110" />
          <el-table-column prop="requestText" label="原始指令" min-width="260" />
          <el-table-column label="状态" width="100">
            <template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="人工决策" width="150">
            <template #default="scope">
              <template v-if="scope.row.status === 'DRAFT'">
                <el-button link type="success" @click="confirm(scope.row)">确认</el-button>
                <el-button link type="danger" @click="reject(scope.row)">驳回</el-button>
              </template>
              <span v-else>{{ scope.row.confirmedBy || '-' }}</span>
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
          @current-change="loadProposals"
        />
      </el-tab-pane>

      <el-tab-pane label="AI 教务 Agent">
        <el-alert
          title="本分析仅读取教学任务、课表、教师上限和禁排约束，不会修改业务数据。"
          type="info"
          show-icon
          :closable="false"
        />
        <div class="analysis-toolbar">
          <el-button type="primary" :loading="analyzing" @click="analyze">运行教务分析</el-button>
        </div>
        <template v-if="analysis">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="学期">{{ analysis.semesterCode }}</el-descriptions-item>
            <el-descriptions-item label="教学任务">{{ analysis.offeringCount }}</el-descriptions-item>
            <el-descriptions-item label="课表项">{{ analysis.scheduleEntryCount }}</el-descriptions-item>
          </el-descriptions>
          <h3>教师负荷</h3>
          <el-table :data="analysis.teacherLoads" border>
            <el-table-column prop="teacherName" label="教师" />
            <el-table-column prop="weeklyLessons" label="已分配周课时" />
            <el-table-column prop="maxWeeklyLessons" label="周课时上限" />
            <el-table-column label="超负荷">
              <template #default="scope"><el-tag :type="scope.row.overloaded ? 'danger' : 'success'">{{ scope.row.overloaded ? '是' : '否' }}</el-tag></template>
            </el-table-column>
          </el-table>
          <h3>禁排冲突</h3>
          <el-table :data="analysis.constraintViolations" border empty-text="未发现禁排冲突">
            <el-table-column prop="teacherName" label="教师" />
            <el-table-column prop="dayOfWeek" label="星期" />
            <el-table-column prop="periodNo" label="节次" />
            <el-table-column prop="reason" label="问题" />
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.agent-page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
header p { margin: 0; color: #84909a; }
.command-box { display: flex; gap: 14px; align-items: flex-start; margin: 16px 0; }
.command-box .el-textarea { max-width: 720px; }
.analysis-toolbar { margin: 16px 0; }
h3 { margin-top: 22px; }
</style>
