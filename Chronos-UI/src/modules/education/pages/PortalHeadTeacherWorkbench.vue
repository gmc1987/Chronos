<template>
  <div class="head-teacher-workbench">
    <el-page-header @back="$router.push('/portal')">
      <template #content>
        <div class="page-title">
          <strong>班主任工作台</strong>
          <small>聚合本人所带班级、学生、监护关系及学籍动态</small>
        </div>
      </template>
    </el-page-header>

    <el-skeleton v-if="loadingClasses" :rows="6" animated />
    <el-empty v-else-if="!classes.length" description="当前账号未绑定班主任班级" />
    <template v-else>
      <div class="class-selector">
        <el-card
          v-for="item in classes"
          :key="item.id"
          shadow="hover"
          :class="['class-card', { active: item.id === selectedClassId }]"
          @click="selectClass(item.id)"
        >
          <strong>{{ item.className }}</strong>
          <small>{{ item.classCode }} · {{ item.gradeYear }}级</small>
          <div>
            <span>{{ item.activeStudentCount }}/{{ item.studentCount }} 在籍</span>
            <el-tag v-if="item.pendingChangeCount" size="small" type="warning">
              {{ item.pendingChangeCount }}项异动待关注
            </el-tag>
          </div>
        </el-card>
      </div>

      <el-skeleton v-if="loadingDetail" :rows="8" animated />
      <template v-else-if="detail.classInfo">
        <div class="metric-grid">
          <el-card v-for="metric in metrics" :key="metric.label" shadow="never">
            <small>{{ metric.label }}</small>
            <strong>{{ metric.value }}</strong>
          </el-card>
        </div>

        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <strong>班级学生</strong>
              <div>
                <el-input v-model="keyword" clearable placeholder="搜索学号或姓名" />
                <el-button @click="$router.push('/portal/education/schedule')">查看课表</el-button>
                <el-button @click="$router.push('/portal/workflows')">发起流程</el-button>
                <el-button @click="$router.push('/portal/publications')">通知公告</el-button>
              </div>
            </div>
          </template>
          <el-table :data="filteredStudents" border>
            <el-table-column prop="studentNo" label="学号" width="140" />
            <el-table-column prop="studentName" label="姓名" width="120" />
            <el-table-column prop="gender" label="性别" width="80" />
            <el-table-column label="学籍状态" width="110">
              <template #default="scope">
                <el-tag :type="scope.row.enrollmentStatus === 'ACTIVE' ? 'success' : 'warning'">
                  {{ statusName(scope.row.enrollmentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="监护关系" min-width="150">
              <template #default="scope">
                <span v-if="scope.row.guardianCount">
                  已登记 {{ scope.row.guardianCount }} 人
                  <el-tag v-if="scope.row.hasPrimaryGuardian" size="small">主监护人</el-tag>
                </span>
                <el-tag v-else type="danger">未登记</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <strong>待处理学籍异动</strong>
          </template>
          <el-empty v-if="!detail.pendingChanges?.length" description="当前班级没有待处理学籍异动" />
          <el-table v-else :data="detail.pendingChanges" border>
            <el-table-column prop="studentNo" label="学号" width="140" />
            <el-table-column prop="studentName" label="学生" width="120" />
            <el-table-column label="异动类型" width="120">
              <template #default="scope">{{ changeTypeName(scope.row.changeType) }}</template>
            </el-table-column>
            <el-table-column prop="effectiveDate" label="生效日期" width="120" />
            <el-table-column prop="reason" label="申请原因" show-overflow-tooltip />
            <el-table-column prop="requestedAt" label="申请时间" width="180" />
          </el-table>
        </el-card>
        <el-card shadow="never">
          <template #header
            ><div class="card-header">
              <strong>班级通知与回执</strong><el-button type="primary" @click="noticeDialog = true">新建通知</el-button>
            </div></template
          >
          <el-empty v-if="!notices.length" description="尚未发布班级通知" />
          <el-table v-else :data="notices" border>
            <el-table-column prop="notice.title" label="标题" min-width="180" /><el-table-column
              prop="notice.status"
              label="状态"
              width="100"
            />
            <el-table-column label="回执" width="130"
              ><template #default="s"
                >{{ s.row.acknowledgedCount }}/{{ s.row.recipientCount }}</template
              ></el-table-column
            >
            <el-table-column prop="notice.receiptDeadline" label="截止时间" width="180" />
            <el-table-column label="操作" width="90"
              ><template #default="s"
                ><el-button
                  v-if="s.row.notice.status === 'DRAFT'"
                  link
                  type="primary"
                  @click="publishNotice(s.row.notice)"
                  >发布</el-button
                ></template
              ></el-table-column
            >
          </el-table>
        </el-card>
      </template>
    </template>
    <el-dialog v-model="noticeDialog" title="新建班级通知" width="620px"
      ><el-form label-width="90px"
        ><el-form-item label="标题"><el-input v-model="noticeForm.title" /></el-form-item
        ><el-form-item label="正文"><el-input v-model="noticeForm.content" type="textarea" :rows="6" /></el-form-item
        ><el-form-item label="需要回执"><el-switch v-model="noticeForm.requireReceipt" /></el-form-item
        ><el-form-item label="回执截止"
          ><el-date-picker
            v-model="noticeForm.receiptDeadline"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="noticeDialog = false">取消</el-button
        ><el-button type="primary" @click="saveNotice">保存草稿</el-button></template
      ></el-dialog
    >
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  createPortalClassNotice,
  portalClassNotices,
  portalHeadTeacherClassDetail,
  portalHeadTeacherClasses,
  publishPortalClassNotice
} from '../../../api/portal'
import { ElMessage, ElMessageBox } from 'element-plus'

const classes = ref([])
const selectedClassId = ref('')
const detail = ref({})
const keyword = ref('')
const loadingClasses = ref(true)
const loadingDetail = ref(false)
const notices = ref([]),
  noticeDialog = ref(false)
const noticeForm = ref({ title: '', content: '', requireReceipt: true, receiptDeadline: '' })

const metrics = computed(() => [
  { label: '班级学生', value: detail.value.metrics?.studentCount || 0 },
  { label: '正常在籍', value: detail.value.metrics?.activeStudentCount || 0 },
  { label: '非正常学籍', value: detail.value.metrics?.nonActiveStudentCount || 0 },
  { label: '缺少监护人', value: detail.value.metrics?.guardianMissingCount || 0 },
  { label: '待关注异动', value: detail.value.metrics?.pendingChangeCount || 0 }
])
const filteredStudents = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return detail.value.students || []
  return (detail.value.students || []).filter(
    (item) => item.studentNo.toLowerCase().includes(value) || item.studentName.toLowerCase().includes(value)
  )
})

const statusName = (value) =>
  ({
    ACTIVE: '正常在籍',
    SUSPENDED: '休学',
    TRANSFERRED: '已转出',
    WITHDRAWN: '已退学',
    GRADUATED: '已毕业'
  })[value] || value
const changeTypeName = (value) =>
  ({
    SUSPEND: '休学',
    RESUME: '复学',
    TRANSFER_OUT: '转出',
    WITHDRAW: '退学',
    GRADUATE: '毕业',
    TRANSFER_CLASS: '转班',
    RETAIN_GRADE: '留级'
  })[value] || value

const selectClass = async (classId) => {
  selectedClassId.value = classId
  loadingDetail.value = true
  try {
    const response = await portalHeadTeacherClassDetail(classId)
    detail.value = response.data || {}
    notices.value = (await portalClassNotices(classId)).data || []
  } finally {
    loadingDetail.value = false
  }
}
const saveNotice = async () => {
  if (!noticeForm.value.title.trim() || !noticeForm.value.content.trim()) return ElMessage.warning('请填写标题和正文')
  await createPortalClassNotice(selectedClassId.value, noticeForm.value)
  noticeDialog.value = false
  notices.value = (await portalClassNotices(selectedClassId.value)).data || []
  ElMessage.success('通知草稿已保存')
}
const publishNotice = async (notice) => {
  await ElMessageBox.confirm('发布后将冻结正文和接收人范围，确认发布？', '提示', { type: 'warning' })
  await publishPortalClassNotice(notice.id)
  notices.value = (await portalClassNotices(selectedClassId.value)).data || []
  ElMessage.success('班级通知已发布')
}

onMounted(async () => {
  try {
    const response = await portalHeadTeacherClasses()
    classes.value = response.data || []
    if (classes.value.length) await selectClass(classes.value[0].id)
  } finally {
    loadingClasses.value = false
  }
})
</script>

<style scoped>
.head-teacher-workbench {
  display: grid;
  gap: 18px;
  padding: 24px;
}
.page-title {
  display: grid;
  gap: 3px;
}
.page-title strong {
  color: #263f49;
  font-size: 20px;
}
.page-title small {
  color: #819097;
  font-size: 12px;
}
.class-selector {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.class-card {
  cursor: pointer;
  border: 1px solid transparent;
}
.class-card.active {
  border-color: #409eff;
  background: #f2f8ff;
}
.class-card :deep(.el-card__body) {
  display: grid;
  gap: 8px;
}
.class-card small {
  color: #819097;
}
.class-card div {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(130px, 1fr));
  gap: 12px;
}
.metric-grid :deep(.el-card__body) {
  display: grid;
  gap: 6px;
}
.metric-grid small {
  color: #819097;
}
.metric-grid strong {
  color: #263f49;
  font-size: 24px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.card-header > div {
  display: flex;
  gap: 8px;
}
.card-header .el-input {
  width: 220px;
}
@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .card-header > div {
    flex-wrap: wrap;
  }
}
</style>
