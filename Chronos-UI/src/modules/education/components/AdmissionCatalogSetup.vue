<template>
  <el-dropdown trigger="click" :disabled="!schoolId" @command="open">
    <el-button>{{ teaching ? '教学基础资料' : '招生基础资料' }}</el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="entry in entries" :key="entry.action" :command="entry">新增{{ entry.label }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <el-dialog v-model="show" :title="selected ? '新增' + selected.label : ''" width="520px">
    <el-form v-if="selected" label-width="105px" @submit.prevent="save">
      <el-form-item label="编码"><el-input v-model.trim="form.code" maxlength="64" /></el-form-item>
      <el-form-item label="名称"><el-input v-model.trim="form.name" maxlength="128" /></el-form-item>
      <el-form-item v-if="selected.action === 'grade'" label="入学年份"><el-input-number v-model="form.year" :min="2000" :max="2100" /></el-form-item>
      <el-form-item v-if="selected.action === 'major'" label="学制"><el-input-number v-model="form.years" :min="1" :max="10" /></el-form-item>
      <el-form-item v-if="selected.action === 'administrative-class'" label="年级">
        <el-select v-model="form.gradeId" filterable>
          <el-option v-for="grade in grades" :key="grade.id" :value="grade.id" :label="grade.gradeName" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="needsMajor" label="专业">
        <el-select v-model="form.majorId" filterable>
          <el-option v-for="major in localMajors" :key="major.id" :value="major.id" :label="major.majorName" />
        </el-select>
      </el-form-item>
      <template v-if="selected.action === 'course'">
        <el-form-item v-if="teaching" label="学科">
          <el-select v-model="form.subjectId" filterable>
            <el-option v-for="subject in subjects" :key="subject.id" :value="subject.id" :label="subject.subjectName" />
          </el-select>
        </el-form-item>
        <el-form-item label="理论学时"><el-input-number v-model="form.theoryHours" :min="0" :max="10000" /></el-form-item>
        <el-form-item label="实践学时"><el-input-number v-model="form.practiceHours" :min="0" :max="10000" /></el-form-item>
        <el-form-item label="课程学分"><el-input-number v-model="form.credits" :min="0" :max="9999.99" :precision="2" /></el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="show = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!valid" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listEducationGrades } from '../../../api/admin'
import { http } from '../../../api/http'
import { hasAdminPermission } from '../../../store/auth'

const props = defineProps({
  schoolId: String,
  majors: { type: Array, default: () => [] },
  subjects: { type: Array, default: () => [] },
  teaching: Boolean,
})
const emit = defineEmits(['saved'])
const entries = [
  { action: 'grade', label: '年级', permission: 'education:grade:create', fields: ['year'] },
  { action: 'major', label: '专业', permission: 'education:class:create', fields: ['years'] },
  { action: 'administrative-class', label: '行政班', permission: 'education:class:create', fields: ['gradeId', 'majorId'] },
  { action: 'course', label: '课程', permission: 'education:course:create', fields: ['majorId', 'subjectId', 'theoryHours', 'practiceHours', 'credits'] },
].filter(item => hasAdminPermission(item.permission))
const show = ref(false)
const saving = ref(false)
const selected = ref(null)
const form = ref({})
const grades = ref([])
const localMajors = computed(() => props.majors.filter(major => major.organizationId === props.schoolId && major.enabled))
const needsMajor = computed(() => ['administrative-class', 'course'].includes(selected.value?.action))
const valid = computed(() => props.schoolId && form.value.code && form.value.name
  && (!needsMajor.value || form.value.majorId)
  && (selected.value?.action !== 'administrative-class' || form.value.gradeId)
  && (!props.teaching || selected.value?.action !== 'course' || form.value.subjectId))

watch(() => props.schoolId, () => { show.value = false })

async function open(entry) {
  const school = props.schoolId
  selected.value = entry
  form.value = { code: '', name: '', year: new Date().getFullYear(), years: 4, theoryHours: 32, practiceHours: 0, credits: 2 }
  grades.value = []
  show.value = true
  if (entry.action === 'administrative-class') {
    const result = await listEducationGrades({ organizationId: school })
    if (props.schoolId === school) grades.value = (result.data || []).filter(grade => grade.organizationId === school && grade.enabled)
  }
}

async function save() {
  if (!valid.value || saving.value) return
  saving.value = true
  const school = props.schoolId
  const entry = selected.value
  try {
    const payload = Object.fromEntries(['code', 'name', ...entry.fields].map(key => [key, form.value[key]]))
    await http.post('/admin/education/academic-lifecycle/student-setup/' + entry.action
      + '?' + new URLSearchParams({ schoolId: school }), payload)
    if (props.schoolId !== school) return
    show.value = false
    ElMessage.success(entry.label + '已保存')
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
