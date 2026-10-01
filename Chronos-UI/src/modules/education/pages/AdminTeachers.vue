<template>
  <AcademicCrudPage
    title="教师档案"
    description="维护教师专业方向、所属单位和最大周课时。可用时间在排课约束中维护。"
    entity-label="教师档案"
    :columns="columns"
    :fields="fields"
    :defaults="defaults"
    :loader="listEducationTeachers"
    :creator="createEducationTeacher"
    :updater="updateEducationTeacher"
    :deleter="deleteEducationTeacher"
    :row-actions="rowActions"
  />
  <el-dialog v-model="changeDialog" title="登记教师任职异动" width="620px">
    <el-alert :title="`教师：${selectedTeacher?.teacherName || '-'}`" type="info" :closable="false" />
    <el-form label-width="110px" class="change-form">
      <el-form-item label="异动类型"
        ><el-select v-model="changeForm.changeType"
          ><el-option label="单位调动" value="TRANSFER" /><el-option label="停职" value="SUSPEND" /><el-option
            label="复职"
            value="RESUME" /><el-option label="离职" value="TERMINATE" /></el-select
      ></el-form-item>
      <el-form-item v-if="changeForm.changeType === 'TRANSFER'" label="目标单位">
        <el-select v-model="changeForm.targetDepartmentId" filterable>
          <el-option
            v-for="item in organizationOptions"
            :key="item.id"
            :label="item.organizationName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期"
        ><el-date-picker v-model="changeForm.effectiveDate" value-format="YYYY-MM-DD"
      /></el-form-item>
      <el-form-item label="异动原因"
        ><el-input v-model="changeForm.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="changeDialog = false">取消</el-button
      ><el-button type="primary" @click="submitChange">确认登记</el-button></template
    >
  </el-dialog>
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
      <el-table-column label="操作" width="90"
        ><template #default="s"
          ><el-button v-if="s.row.status === 'SCHEDULED'" link type="danger" @click="cancelChange(s.row)"
            >取消</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </el-drawer>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  bindEducationTeacherAccount,
  cancelTeacherEmploymentChange,
  createEducationTeacher,
  deleteEducationTeacher,
  listEducationTeachers,
  listTeacherEmploymentChanges,
  orgList,
  registerTeacherEmploymentChange,
  resetUserPassword,
  unbindEducationTeacherAccount,
  updateEducationTeacher,
  userByEmployee
} from '../../../api/admin'
const columns = [
  { prop: 'teacherNo', label: '教师编号', width: 130 },
  { prop: 'teacherName', label: '姓名', width: 100 },
  { prop: 'specialty', label: '专业方向', minWidth: 150 },
  { prop: 'accountUsername', label: '绑定账号', width: 150 },
  { prop: 'accountStatus', label: '账号状态', width: 110 },
  { prop: 'employmentStatus', label: '任职状态', width: 110 },
  { prop: 'maxWeeklyLessons', label: '周课时上限', width: 120 },
  { prop: 'maxDailyLessons', label: '日课时上限', width: 120 },
  { prop: 'maxConsecutiveLessons', label: '连续课时上限', width: 130 }
]
const fields = [
  { prop: 'employeeId', label: 'IAM 员工 ID' },
  { prop: 'teacherNo', label: '教师编号' },
  { prop: 'teacherName', label: '教师姓名' },
  { prop: 'departmentId', label: '所属单位 ID', immutableOnEdit: true },
  { prop: 'specialty', label: '专业方向' },
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
]
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
const changeDialog = ref(false),
  historyDrawer = ref(false),
  selectedTeacher = ref(null),
  changeHistory = ref([]),
  organizationOptions = ref([])
const changeForm = reactive({
  changeType: '',
  targetDepartmentId: '',
  effectiveDate: '',
  reason: ''
})
const openChange = (row) => {
  selectedTeacher.value = row
  Object.assign(changeForm, {
    changeType: '',
    targetDepartmentId: '',
    effectiveDate: new Date().toISOString().slice(0, 10),
    reason: ''
  })
  changeDialog.value = true
}
const openHistory = async (row) => {
  selectedTeacher.value = row
  const response = await listTeacherEmploymentChanges(row.id, {
    page: 0,
    size: 100
  })
  changeHistory.value = response.data?.content || []
  historyDrawer.value = true
}
const submitChange = async () => {
  if (!changeForm.changeType || !changeForm.reason.trim()) return ElMessage.warning('请选择异动类型并填写原因')
  if (changeForm.changeType === 'TRANSFER' && !changeForm.targetDepartmentId) return ElMessage.warning('请输入目标单位')
  await registerTeacherEmploymentChange(selectedTeacher.value.id, {
    ...changeForm
  })
  changeDialog.value = false
  ElMessage.success('教师任职异动已登记')
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
  { label: '任职异动', run: openChange, successMessage: false, reload: false },
  { label: '异动记录', run: openHistory, successMessage: false, reload: false },
  { label: '重新绑定', run: (row) => bindEducationTeacherAccount(row.id) },
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
<style scoped>
.change-form {
  margin-top: 20px;
}
</style>
