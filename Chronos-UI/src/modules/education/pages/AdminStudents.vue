<template>
  <AcademicCrudPage
    title="学生档案"
    description="维护学生学籍、年级、专业和行政班"
    entity-label="学生"
    permission-prefix="education:student"
    :columns="columns"
    :fields="fields"
    :defaults="defaults"
    :lookups="lookups"
    :loader="listEducationStudents"
    :creator="createEducationStudent"
    :updater="updateEducationStudent"
    :row-actions="rowActions"
  >
    <template #actions>
      <el-button
        v-permission="['education:student:export', 'education:student:manage']"
        @click="exportStudents"
      >
        导出 Excel
      </el-button>
    </template>
  </AcademicCrudPage>

  <el-dialog v-model="changeDialog" title="发起学籍异动" width="620px">
    <el-alert
      :title="`学生：${selectedStudent?.studentName || '-'}`"
      type="info"
      :closable="false"
    />
    <el-form label-width="110px" class="change-form">
      <el-form-item label="异动类型">
        <el-select v-model="changeForm.changeType" @change="handleChangeType">
          <el-option
            v-for="option in changeTypeOptions"
            :key="option.dictValue"
            :label="option.dictName"
            :value="option.dictValue"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="requiresTargetClass" label="目标行政班">
        <el-select v-model="changeForm.targetClassId" filterable>
          <el-option
            v-for="item in classOptions"
            :key="item.id"
            :label="item.className"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期">
        <el-date-picker v-model="changeForm.effectiveDate" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="异动原因">
        <el-input v-model="changeForm.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="changeDialog = false">取消</el-button>
      <el-button type="primary" @click="submitChange">提交申请</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="historyDrawer" title="学籍异动记录" size="760px">
    <el-table :data="changeHistory" border>
      <el-table-column prop="changeType" label="异动类型" width="120">
        <template #default="scope">{{ changeTypeLabel(scope.row.changeType) }}</template>
      </el-table-column>
      <el-table-column prop="effectiveDate" label="生效日期" width="110" />
      <el-table-column prop="reason" label="原因" min-width="180" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="requestedBy" label="申请人" width="110" />
      <el-table-column label="操作" width="130">
        <template #default="scope">
          <template v-if="scope.row.status === 'PENDING'">
            <el-button
              v-permission="['education:student:manage']"
              link
              type="success"
              @click="approveChange(scope.row)"
            >
              批准
            </el-button>
            <el-button
              v-permission="['education:student:manage']"
              link
              type="danger"
              @click="rejectChange(scope.row)"
            >
              驳回
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  approveStudentStatusChange,
  createEducationStudent,
  dictionaryOptions,
  exportEducationStudents,
  listAdministrativeClasses,
  listEducationGrades,
  listEducationMajors,
  listEducationStudents,
  listStudentStatusChanges,
  rejectStudentStatusChange,
  requestStudentStatusChange,
  updateEducationStudent,
} from '../../../api/admin'

const changeDialog = ref(false)
const historyDrawer = ref(false)
const selectedStudent = ref(null)
const classOptions = ref([])
const changeTypeOptions = ref([])
const changeHistory = ref([])
const changeForm = reactive({ changeType: '', targetClassId: '', effectiveDate: '', reason: '' })
const requiresTargetClass = computed(() => ['TRANSFER_CLASS', 'RETAIN_GRADE'].includes(changeForm.changeType))

const resetChangeForm = () => Object.assign(changeForm, {
  changeType: '', targetClassId: '', effectiveDate: new Date().toISOString().slice(0, 10), reason: '',
})
const handleChangeType = () => { if (!requiresTargetClass.value) changeForm.targetClassId = '' }
const openChange = row => { selectedStudent.value = row; resetChangeForm(); changeDialog.value = true }
const openHistory = async row => {
  selectedStudent.value = row
  const response = await listStudentStatusChanges(row.id, { page: 0, size: 100 })
  changeHistory.value = response.data?.content || []
  historyDrawer.value = true
}
const submitChange = async () => {
  if (!changeForm.changeType || !changeForm.reason.trim()) {
    ElMessage.warning('请选择异动类型并填写原因')
    return
  }
  if (requiresTargetClass.value && !changeForm.targetClassId) {
    ElMessage.warning('请选择目标行政班')
    return
  }
  await requestStudentStatusChange(selectedStudent.value.id, { ...changeForm })
  changeDialog.value = false
  ElMessage.success('学籍异动申请已提交')
}
const approveChange = async row => {
  const { value = '' } = await ElMessageBox.prompt('请输入审批意见（可选）', '批准学籍异动', { inputType: 'textarea' })
  const response = await approveStudentStatusChange(row.id, value)
  const message = response.data?.status === 'APPROVED_PENDING'
    ? '学籍异动已批准，将在生效日期自动执行'
    : '学籍异动已批准并生效'
  ElMessage.success(message)
  await openHistory(selectedStudent.value)
}
const rejectChange = async row => {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回学籍异动', { inputType: 'textarea', inputValidator: text => !!text?.trim() || '驳回原因不能为空' })
  await rejectStudentStatusChange(row.id, value)
  ElMessage.success('学籍异动已驳回')
  await openHistory(selectedStudent.value)
}
const changeTypeLabel = value => changeTypeOptions.value.find(item => item.dictValue === value)?.dictName || value
const rowActions = [
  { label: '学籍异动', run: openChange, successMessage: false, reload: false },
  { label: '异动记录', run: openHistory, successMessage: false, reload: false },
]

const exportStudents = async () => {
  const blob = await exportEducationStudents()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '学生档案.xlsx'
  anchor.click()
  URL.revokeObjectURL(url)
}

const columns = [
  { prop: 'studentNo', label: '学号' },
  { prop: 'studentName', label: '姓名' },
  { prop: 'gradeId', label: '年级', lookup: 'grades' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes' },
  { prop: 'enrollmentStatus', label: '学籍状态', dictCode: 'EDU_STUDENT_STATUS' },
]
const fields = [
  { prop: 'studentNo', label: '学号' },
  { prop: 'studentName', label: '姓名' },
  { prop: 'gender', label: '性别', dictCode: 'COMMON_GENDER' },
  { prop: 'gradeYear', label: '入学年份', type: 'number' },
  { prop: 'gradeId', label: '所属年级', lookup: 'grades', labelProp: 'gradeName', immutableOnEdit: true },
  { prop: 'majorId', label: '所属专业', lookup: 'majors', labelProp: 'majorName', immutableOnEdit: true },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes', labelProp: 'className', immutableOnEdit: true },
  { prop: 'enrollmentStatus', label: '学籍状态', dictCode: 'EDU_STUDENT_STATUS', immutableOnEdit: true },
  { prop: 'phone', label: '联系电话' },
]
const defaults = { enrollmentStatus: 'ACTIVE' }
const lookups = [
  { key: 'grades', loader: listEducationGrades, labelProp: 'gradeName' },
  { key: 'majors', loader: listEducationMajors, labelProp: 'majorName' },
  { key: 'classes', loader: listAdministrativeClasses, labelProp: 'className' },
]
onMounted(async () => {
  const [classes, types] = await Promise.all([
    listAdministrativeClasses(),
    dictionaryOptions('EDU_STUDENT_CHANGE_TYPE'),
  ])
  classOptions.value = classes.data || []
  changeTypeOptions.value = types.data || []
})
</script>

<style scoped>
.change-form { margin-top: 20px; }
</style>
