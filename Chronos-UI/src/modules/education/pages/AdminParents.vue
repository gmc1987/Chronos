<template>
  <div class="page">
    <header>
      <div>
        <h2>家长管理</h2>
        <p>维护家长档案、学生监护关系和门户账号绑定。</p>
      </div>
      <el-button type="primary" @click="editParent()">新增家长</el-button>
    </header>

    <el-table :data="parents" border>
      <el-table-column prop="parentNo" label="家长编号" />
      <el-table-column prop="parentName" label="姓名" />
      <el-table-column label="性别">
        <template #default="scope">{{ label(genders, scope.row.gender) }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" />
      <el-table-column prop="employment" label="工作单位或职业" />
      <AdaptiveActionColumn label="操作" width="220">
        <template #default="scope">
          <el-button link type="primary" @click="editParent(scope.row)">编辑</el-button>
          <el-button link @click="openRelations(scope.row)">关联学生</el-button>
          <el-button link type="danger" @click="removeParent(scope.row)">删除</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="loadParents"
    />

    <el-card class="binding-card">
      <template #header><div class="binding-header"><strong>门户账号绑定</strong><el-button type="primary" @click="openBinding">新增绑定</el-button></div></template>
      <el-table :data="bindings" border>
        <el-table-column prop="parentName" label="家长" />
        <el-table-column prop="username" label="登录账号" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="账号可用性" width="120"><template #default="{ row }"><el-tag :type="row.accountAvailable ? 'success' : 'danger'">{{ row.accountAvailable ? '可邀请' : '不可用' }}</el-tag></template></el-table-column>
        <el-table-column prop="verifiedAt" label="核验时间" />
        <AdaptiveActionColumn label="操作" width="100"><template #default="{ row }"><el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="invalidateBinding(row)">解绑</el-button></template></AdaptiveActionColumn>
      </el-table>
      <el-pagination v-model:current-page="bindingPage" v-model:page-size="bindingPageSize"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        :total="bindingTotal" @size-change="changeBindingPageSize" @current-change="loadBindings" />
    </el-card>
    <el-dialog v-model="bindingDialog" title="绑定家长门户账号" width="520px">
      <el-form label-width="100px">
        <el-form-item label="家长"><el-select v-model="bindingForm.parentId" filterable placeholder="选择家长" style="width: 100%"><el-option v-for="item in allParents" :key="item.id" :label="`${item.parentName}（${item.parentNo || item.phone || '-'}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="登录账号"><el-input v-model="bindingForm.username" placeholder="输入已创建的门户登录账号" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="bindingDialog = false">取消</el-button><el-button type="primary" @click="saveBinding">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="parentDialog" :title="parentForm.id ? '编辑家长' : '新增家长'" width="600px">
      <el-form label-width="120px">
        <el-form-item label="家长编号"><el-input v-model="parentForm.parentNo" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="parentForm.parentName" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="parentForm.gender">
            <el-option v-for="item in genders" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话"><el-input v-model="parentForm.phone" /></el-form-item>
        <el-form-item label="工作单位或职业"><el-input v-model="parentForm.employment" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="parentDialog = false">取消</el-button>
        <el-button type="primary" @click="saveParent">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDialog" :title="`${activeParent.parentName || ''} · 学生关系`" width="760px">
      <div class="relation-form">
        <el-select-v2 v-model="relationForm.studentId" :options="studentOptions" filterable placeholder="选择学生" />
        <el-select v-model="relationForm.relationship" placeholder="关系">
          <el-option v-for="item in relationships" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="relationForm.primaryGuardian">主监护人</el-checkbox>
        <el-checkbox v-model="relationForm.emergencyContact">紧急联系人</el-checkbox>
        <el-button type="primary" @click="saveRelation">添加关联</el-button>
      </div>
      <el-table :data="relations" border>
        <el-table-column label="学生">
          <template #default="scope">{{ studentName(scope.row.studentId) }}</template>
        </el-table-column>
        <el-table-column label="关系">
          <template #default="scope">{{ label(relationships, scope.row.relationship) }}</template>
        </el-table-column>
        <el-table-column label="主监护人" width="100">
          <template #default="scope">{{ scope.row.primaryGuardian ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="紧急联系人" width="110">
          <template #default="scope">{{ scope.row.emergencyContact ? '是' : '否' }}</template>
        </el-table-column>
        <AdaptiveActionColumn label="操作" width="80">
          <template #default="scope"><el-button link type="danger" @click="removeRelation(scope.row)">移除</el-button></template>
        </AdaptiveActionColumn>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createHomeSchoolBinding,
  createEducationParent,
  createStudentGuardian,
  deleteEducationParent,
  deleteStudentGuardian,
  dictionaryOptions,
  listEducationParents,
  pageHomeSchoolBindings,
  listEducationStudents,
  listStudentGuardians,
  updateEducationParent,
  invalidateHomeSchoolBinding,
} from '../../../api/admin'

const parents = ref([])
const students = ref([])
const studentOptions = computed(() => students.value.map(student => ({
  label: `${student.studentName}（${student.studentNo}）`,
  value: student.id,
})))
const genders = ref([])
const relationships = ref([])
const parentDialog = ref(false)
const relationDialog = ref(false)
const parentForm = ref({})
const relationForm = ref({})
const activeParent = ref({})
const relations = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const bindings = ref([])
const bindingPage = ref(1)
const bindingPageSize = ref(20)
const bindingTotal = ref(0)
const allParents = ref([])
const bindingDialog = ref(false)
const bindingForm = ref({ parentId: '', username: '' })

const options = response => (response?.data || []).map(item => ({
  label: item.dictName,
  value: item.dictValue,
}))
const label = (items, value) => items.find(item => item.value === value)?.label || value || '-'
const studentName = id => {
  const student = students.value.find(item => item.id === id)
  return student ? `${student.studentName}（${student.studentNo}）` : id
}

const loadParents = async () => {
  const response = await listEducationParents({ page: page.value - 1, size: pageSize.value })
  parents.value = response?.data?.content || response?.data || []
  total.value = response?.data?.totalElements ?? parents.value.length
}
const load = async () => {
  const [studentResponse, genderResponse, relationshipResponse] = await Promise.all([
    listEducationStudents(),
    dictionaryOptions('COMMON_GENDER'),
    dictionaryOptions('EDU_GUARDIAN_RELATIONSHIP'),
  ])
  students.value = studentResponse?.data || []
  genders.value = options(genderResponse)
  relationships.value = options(relationshipResponse)
  await Promise.all([loadParents(), loadBindings()])
}
const loadBindings = async () => {
  const response = await pageHomeSchoolBindings({ page: bindingPage.value - 1, size: bindingPageSize.value })
  bindings.value = response?.data?.content || response?.data || []
  bindingTotal.value = response?.data?.totalElements ?? bindings.value.length
}
const changeBindingPageSize = () => { bindingPage.value = 1; loadBindings() }
const openBinding = async () => {
  const response = await listEducationParents()
  allParents.value = response?.data?.content || response?.data || []
  bindingForm.value = { parentId: '', username: '' }
  bindingDialog.value = true
}
const saveBinding = async () => {
  if (!bindingForm.value.parentId || !bindingForm.value.username.trim()) return ElMessage.warning('请选择家长并填写登录账号')
  await createHomeSchoolBinding(bindingForm.value)
  bindingDialog.value = false
  ElMessage.success('门户账号绑定已保存')
  await load()
}
const invalidateBinding = async row => {
  await ElMessageBox.confirm(`确认解除“${row.parentName}”的账号绑定？`, '解绑确认', { type: 'warning' })
  await invalidateHomeSchoolBinding(row.id)
  await load()
}
const changePageSize = () => { page.value = 1; loadParents() }
const editParent = (row = {}) => {
  parentForm.value = { status: 'ACTIVE', ...row }
  parentDialog.value = true
}
const saveParent = async () => {
  if (parentForm.value.id) await updateEducationParent(parentForm.value.id, parentForm.value)
  else await createEducationParent(parentForm.value)
  parentDialog.value = false
  ElMessage.success('家长档案已保存')
  await load()
}
const removeParent = async row => {
  await ElMessageBox.confirm(`确认删除家长“${row.parentName}”？`, '删除确认', { type: 'warning' })
  await deleteEducationParent(row.id)
  if (!parents.value.length && page.value > 1) page.value -= 1
  await loadParents()
}
const openRelations = async parent => {
  activeParent.value = parent
  relationForm.value = {
    parentId: parent.id,
    relationship: relationships.value[0]?.value || '',
    primaryGuardian: false,
    emergencyContact: false,
  }
  const response = await listStudentGuardians({ parentId: parent.id })
  relations.value = response?.data || []
  relationDialog.value = true
}
const saveRelation = async () => {
  await createStudentGuardian(relationForm.value)
  ElMessage.success('学生监护关系已保存')
  await openRelations(activeParent.value)
}
const removeRelation = async row => {
  await deleteStudentGuardian(row.id)
  await openRelations(activeParent.value)
}

onMounted(load)
</script>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.relation-form { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.relation-form .el-select { min-width: 150px; }
.binding-card { margin-top: 20px; }
.binding-header { display: flex; align-items: center; justify-content: space-between; }
.binding-card :deep(.el-pagination) { justify-content: flex-end; margin-top: 10px; }
</style>
