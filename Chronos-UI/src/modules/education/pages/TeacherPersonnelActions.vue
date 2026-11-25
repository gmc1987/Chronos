<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../../api/http'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { hasAdminPermission } from '../../../store/auth'

const props = defineProps({ organizationId: String, portal: Boolean })
const emit = defineEmits(['saved'])
const api = '/admin/education/teacher-personnel'
const managing = hasAdminPermission('education:teacher:business:manage')
const reviewing = hasAdminPermission('education:teacher:business:review')
const action = ref('')
const visible = ref(false)
const saving = ref(false)
const reviews = ref([])
const employees = ref([])
const form = reactive({})
const types = { ONBOARD: '入职', TERMINATE: '离职', TRANSFER: '调动', SUSPEND: '停职', RESUME: '复职' }
const titles = { onboard: '登记拟入职教师', transfer: '登记调入任职', start: '发起人事申请', review: '人事审核待办', resign: '申请离职' }
const wantsEmployee = computed(() => action.value === 'transfer' || action.value === 'start')
const needsTarget = computed(() => action.value === 'transfer' || (action.value === 'start' && form.type === 'TRANSFER'))
const fields = [['department', '部门', 'departments'], ['position', '岗位', 'positions'], ['level', '职级（可选）', 'levels']]
const structures = ref({})
const structureSchool = computed(() => action.value === 'onboard' ? props.organizationId
  : action.value === 'transfer' ? form.targetOrganizationId : null)

watch(structureSchool, async (school, previous, onCleanup) => {
  structures.value = {}
  if (!school) return
  let stale = false
  onCleanup(() => { stale = true })
  const data = (await http.get(`${api}/directory/structure?organizationId=${encodeURIComponent(school)}`)).data
  if (!stale) structures.value = data
})

function selectStructure(key, source, code) {
  const item = structures.value[source]?.find(entry => entry.code === code)
  if (item) form[key] = { ...item }
}
let searchSequence = 0

async function searchEmployees(keyword = '') {
  if (!props.organizationId) return
  const current = ++searchSequence
  const query = new URLSearchParams({ organizationId: props.organizationId, keyword })
  const response = await http.get(`${api}/directory/employees?${query}`)
  if (current === searchSequence) employees.value = response.data?.content || []
}

watch(() => props.organizationId, () => {
  searchSequence++
  employees.value = []
  form.employeeCode = ''
})

async function open(item) {
  action.value = item
  Object.assign(form, { name: '', employeeCode: '', type: 'ONBOARD', targetOrganizationId: '',
    effectiveDate: '', reason: '', department: { code: '', name: '' },
    position: { code: '', name: '' }, level: { code: '', name: '' } })
  visible.value = true
  if (wantsEmployee.value) await searchEmployees()
  if (item === 'review') await loadReviews()
}

async function loadReviews() {
  reviews.value = (await http.get(`${api}/reviews`)).data || []
}

async function save() {
  if (!form.effectiveDate) return ElMessage.warning('请选择生效日期')
  if (wantsEmployee.value && !form.employeeCode) return ElMessage.warning('请选择教职工')
  if (needsTarget.value && !form.targetOrganizationId) return ElMessage.warning('请选择调入学校')
  if (['resign', 'start'].includes(action.value) && !form.reason.trim()) return ElMessage.warning('请填写申请说明')
  if (action.value === 'onboard' && !form.name.trim()) return ElMessage.warning('请填写教师姓名')
  const directoryAction = ['onboard', 'transfer'].includes(action.value)
  if (directoryAction && [form.department, form.position].some(item => !item.code.trim() || !item.name.trim())) {
    return ElMessage.warning('请填写部门和岗位的编码与名称')
  }
  const assignment = {
    schoolId: action.value === 'transfer' ? form.targetOrganizationId : props.organizationId,
    department: form.department,
    position: form.position,
    level: form.level.code.trim() || form.level.name.trim() ? form.level : null,
    effectiveFrom: form.effectiveDate,
  }
  saving.value = true
  try {
    if (action.value === 'onboard') {
      const response = await http.post(`${api}/directory/onboard`, { name: form.name, assignment })
      ElMessage.success(`已登记 ${response.data.employeeName}，工号 ${response.data.employeeCode}`)
    } else if (action.value === 'transfer') {
      await http.post(`${api}/directory/transfer`, { sourceSchoolId: props.organizationId, employeeCode: form.employeeCode, assignment })
      ElMessage.success('调入任职已登记')
    } else if (action.value === 'start') {
      await http.post(`${api}/requests`, {
        organizationId: props.organizationId, targetOrganizationId: form.targetOrganizationId,
        type: form.type, employeeCode: form.employeeCode, effectiveDate: form.effectiveDate, reason: form.reason,
      })
      ElMessage.success('申请已提交')
    } else {
      await http.post('/portal/education/teacher-personnel/resignations', { effectiveDate: form.effectiveDate, reason: form.reason })
      ElMessage.success('离职申请已提交')
    }
    visible.value = false
    emit('saved')
  } finally { saving.value = false }
}

async function decide(task, approved) {
  let comment = ''
  if (approved) {
    await ElMessageBox.confirm(`批准 ${task.employeeName} 的${task.flowName}？`, '人事审核')
  } else {
    comment = (await ElMessageBox.prompt('请填写驳回原因', '人事审核', { inputPattern: /\S/, inputErrorMessage: '请输入原因' })).value
  }
  saving.value = true
  try {
    await http.post(`${api}/reviews/${task.id}`, { approved, comment })
    ElMessage.success(approved ? '已批准，系统将在到达生效日后处理' : '已驳回申请')
    await loadReviews()
    emit('saved')
  } finally { saving.value = false }
}
</script>

<template>
  <div class="actions">
    <el-button v-if="portal" type="primary" @click="open('resign')">申请离职</el-button>
    <template v-if="!portal && managing">
      <el-button :disabled="!organizationId" @click="open('onboard')">登记拟入职教师</el-button>
      <el-button :disabled="!organizationId" @click="open('transfer')">登记调入任职</el-button>
      <el-button type="primary" :disabled="!organizationId" @click="open('start')">发起人事申请</el-button>
    </template>
    <el-button v-if="!portal && reviewing" type="primary" @click="open('review')">人事审核待办</el-button>
    <el-dialog v-model="visible" :title="titles[action]" :width="action === 'review' ? '780px' : '560px'">
      <el-table v-if="action === 'review'" :data="reviews" border>
        <el-table-column prop="employeeName" label="教师" />
        <el-table-column prop="flowName" label="申请" />
        <el-table-column prop="effectiveDate" label="生效日期" />
        <el-table-column prop="reason" label="说明" show-overflow-tooltip />
        <el-table-column label="操作" width="145">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="saving" @click="decide(row, true)">批准</el-button>
            <el-button link type="danger" :disabled="saving" @click="decide(row, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-form v-else label-width="110px" @submit.prevent>
        <el-form-item v-if="action === 'onboard'" label="教师姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item v-if="action === 'start'" label="业务类型">
          <el-select v-model="form.type"><el-option v-for="(name, code) in types" :key="code" :label="name" :value="code" /></el-select>
        </el-form-item>
        <el-form-item v-if="wantsEmployee" label="教职工">
          <el-select v-model="form.employeeCode" remote filterable :remote-method="searchEmployees" placeholder="输入工号或姓名查询">
            <el-option v-for="item in employees" :key="item.employeeCode" :label="`${item.employeeName}（${item.employeeCode}）`" :value="item.employeeCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needsTarget" label="调入学校">
          <OrganizationSelect v-model="form.targetOrganizationId" school-only :auto-select-single="false" context-key="personnel-transfer-target" />
        </el-form-item>
        <template v-if="action === 'onboard' || action === 'transfer'">
          <el-form-item v-for="[key, label, source] in fields" :key="key" :label="label">
            <div class="directory-fields">
              <el-select v-if="structures[source]?.length" :model-value="form[key].code" filterable placeholder="选择已有记录"
                @change="code => selectStructure(key, source, code)">
                <el-option v-for="item in structures[source]" :key="item.code" :label="`${item.name}（${item.code}）`" :value="item.code" />
              </el-select>
              <el-input v-model="form[key].code" placeholder="编码" />
              <el-input v-model="form[key].name" placeholder="名称" />
            </div>
          </el-form-item>
        </template>
        <el-form-item label="生效日期"><el-input v-model="form.effectiveDate" type="date" /></el-form-item>
        <el-form-item v-if="action === 'start' || action === 'resign'" label="申请说明"><el-input v-model="form.reason" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="action !== 'review'" type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.actions, .directory-fields { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.directory-fields { width: 100%; }
.directory-fields > * { flex: 1; }
</style>
