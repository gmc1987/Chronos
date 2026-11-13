<template>
  <el-button type="primary" @click="$router.push('/admin/education/teacher-personnel')">前往教师人事工作台</el-button>
  <AcademicCrudPage searchable organization-scoped import-kind="TEACHER_PROFILES" permission-prefix="education:teacher:business"
    title="教师档案"
    description="维护教师专业方向、所属单位和最大周课时。可用时间在排课约束中维护。"
    entity-label="教师档案"
    :columns="columns"
    :fields="fields"
    :defaults="defaults"
    :loader="listEducationTeachers"
    :allow-create="false"
    :updater="updateEducationTeacher"
    :deleter="deleteEducationTeacher"
    :row-actions="rowActions"
  />
  <el-drawer v-model="historyDrawer" title="教师任职异动记录" size="760px">
    <el-table :data="changeHistory" border>
      <el-table-column prop="changeType" label="类型" width="100"
        ><template #default="s">{{ changeTypeName(s.row.changeType) }}</template></el-table-column
      >
      <el-table-column prop="fromStatus" label="原状态" width="100" /><el-table-column
        prop="toStatus"
        label="目标状态"
        width="100"
      />
      <el-table-column prop="effectiveDate" label="生效日期" width="115" /><el-table-column
        prop="reason"
        label="原因"
        min-width="180"
      />
      <el-table-column prop="status" label="记录状态" width="110" />
      <AdaptiveActionColumn label="操作" width="90"
        ><template #default="s"
          ><el-button v-if="s.row.status === 'SCHEDULED'" link type="danger" @click="cancelChange(s.row)"
            >取消</el-button
          ></template
        ></AdaptiveActionColumn
      >
    </el-table>
  </el-drawer>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  cancelTeacherEmploymentChange,
  deleteEducationTeacher,
  listEducationTeachers,
  listTeacherEmploymentChanges,
  orgList,
  resetUserPassword,
  unbindEducationTeacherAccount,
  updateEducationTeacher,
  userByEmployee
} from '../../../api/admin'
const columns = [
  { prop: 'teacherNo', label: '教师编号', width: 130 },
  { prop: 'teacherName', label: '姓名', width: 100 },
  { prop: 'specialty', label: '专业方向', dictCode: 'EDU_TEACHER_SPECIALTY', minWidth: 150 },
  { prop: 'accountUsername', label: '绑定账号', width: 150 },
  { prop: 'accountStatus', label: '账号状态', width: 110 },
  { prop: 'employmentStatus', label: '任职状态', width: 110 },
  { prop: 'maxWeeklyLessons', label: '周课时上限', width: 120 },
  { prop: 'maxDailyLessons', label: '日课时上限', width: 120 },
  { prop: 'maxConsecutiveLessons', label: '连续课时上限', width: 130 }
]
const fields = computed(() => [
  {
    prop: 'departmentId',
    label: '所属单位',
    options: organizationOptions.value.map(item => ({ value: item.id, label: item.organizationName })),
    readOnlyDisplay: id => organizationOptions.value.find(item => item.id === id)?.organizationName
      || (id ? '单位名称不可用' : '未设置'),
  },
  { prop: 'specialty', label: '专业方向', dictCode: 'EDU_TEACHER_SPECIALTY' },
  { prop: 'maxWeeklyLessons', label: '周课时上限', type: 'number', min: 1 },
  {
    prop: 'maxDailyLessons',
    label: '日课时上限',
    type: 'number',
    min: 1,
    max: 20
  },
  {
    prop: 'maxConsecutiveLessons',
    label: '连续课时上限',
    type: 'number',
    min: 1,
    max: 10
  }
])
const defaults = {
  maxWeeklyLessons: 20,
  maxDailyLessons: 6,
  maxConsecutiveLessons: 4,
  enabled: true
}
const resetTeacherPassword = async (row) => {
  const account = await userByEmployee(row.employeeId)
  if (!account?.data?.id) {
    throw new Error('该教师尚未绑定 IAM 登录账号')
  }
  const { value } = await ElMessageBox.prompt(
    `为 ${row.teacherName || row.teacherNo} 设置新的临时密码。重置后用户下次登录必须修改密码。`,
    '重置教师密码',
    {
      inputType: 'password',
      inputPlaceholder: '至少10位，包含大小写字母和数字',
      inputValidator: (value) =>
        /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{10,}$/.test(value || '') || '密码至少10位，并包含大小写字母和数字',
      inputErrorMessage: '密码格式不符合要求',
      confirmButtonText: '重置',
      cancelButtonText: '取消'
    }
  )
  await resetUserPassword(account.data.id, value)
  ElMessage.success('密码已重置，用户下次登录须修改密码')
}
const historyDrawer = ref(false)
const selectedTeacher = ref(null)
const changeHistory = ref([])
const organizationOptions = ref([])
const openHistory = async (row) => {
  selectedTeacher.value = row
  const response = await listTeacherEmploymentChanges(row.id, {
    page: 0,
    size: 100
  })
  changeHistory.value = response.data?.content || []
  historyDrawer.value = true
}
const cancelChange = async (row) => {
  await ElMessageBox.confirm('确认取消该待生效任职异动？', '提示', {
    type: 'warning'
  })
  await cancelTeacherEmploymentChange(row.id)
  ElMessage.success('任职异动已取消')
  await openHistory(selectedTeacher.value)
}
const changeTypeName = (value) =>
  ({
    TRANSFER: '单位调动',
    SUSPEND: '停职',
    RESUME: '复职',
    TERMINATE: '离职'
  })[value] || value
const rowActions = [
  { label: '异动记录', run: openHistory, successMessage: false, reload: false },
  {
    label: '解除绑定',
    type: 'warning',
    run: (row) => unbindEducationTeacherAccount(row.id)
  },
  { label: '重置密码', run: resetTeacherPassword }
]
onMounted(async () => {
  const response = await orgList({ page: 0, size: 500 })
  organizationOptions.value = response.data?.content || []
})
</script>
