<template>
  <el-button @click="visible = true">教学资源基础数据</el-button>
  <el-dialog v-model="visible" title="教学资源基础数据" width="min(650px, 95vw)">
    <el-form label-width="105px" @submit.prevent>
      <el-form-item label="学校"><OrganizationSelect v-model="schoolId" school-only /></el-form-item>
      <el-form-item label="操作">
        <el-radio-group v-model="mode">
          <el-radio-button value="offering">建立教学班</el-radio-button>
          <el-radio-button value="teachers">登记教师</el-radio-button>
          <el-radio-button value="accounts">绑定教师账号</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="mode === 'teachers'">
        <el-form-item label="教师编号"><el-input v-model="teacher.code" maxlength="32" /></el-form-item>
        <el-form-item label="教师姓名"><el-input v-model="teacher.name" maxlength="100" /></el-form-item>
      </template>
      <template v-else-if="mode === 'accounts'">
        <el-form-item label="完整教师编号"><el-input v-model="account.teacherNo" placeholder="填写含机构编码的教师编号" /></el-form-item>
        <el-form-item label="本校登录账号"><el-input v-model="account.username" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="学期">
          <el-select v-model="form.semesterCode" :disabled="newTerm" placeholder="选择本校学期">
            <el-option v-for="item in options.terms" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
          <el-checkbox v-model="newTerm">新建学期</el-checkbox>
        </el-form-item>
        <template v-if="newTerm">
          <el-form-item label="学年"><el-input v-model="term.academicYear" placeholder="例如 2026-2027" /></el-form-item>
          <el-form-item label="学期序号"><el-input-number v-model="term.termNo" :min="1" :max="3" /></el-form-item>
          <el-form-item label="学期名称"><el-input v-model="term.name" /></el-form-item>
          <el-form-item label="起止日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
          <el-form-item label="教学周数"><el-input-number v-model="term.weeks" :min="1" :max="53" /></el-form-item>
        </template>
        <el-form-item label="课程编码"><el-input v-model="form.courseCode" maxlength="32" /></el-form-item>
        <el-form-item label="课程名称"><el-input v-model="form.courseName" maxlength="128" /></el-form-item>
        <el-form-item label="总学时"><el-input-number v-model="form.hours" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="教学班编码"><el-input v-model="form.classCode" maxlength="64" /></el-form-item>
        <el-form-item label="教学班名称"><el-input v-model="form.className" maxlength="128" /></el-form-item>
        <el-form-item label="任课教师">
          <el-select v-model="form.teacherId" placeholder="选择本校教师" filterable>
            <el-option v-for="item in options.teachers" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>
    <template #footer><el-button type="primary" :disabled="!schoolId" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const schoolId = ref('')
const mode = ref('offering')
const newTerm = ref(false)
const dates = ref([])
const term = reactive({ academicYear: '', termNo: 1, name: '', weeks: 20 })
const form = reactive({ semesterCode: '', courseCode: '', courseName: '', hours: 32, classCode: '', className: '', teacherId: '' })
const teacher = reactive({ code: '', name: '' })
const account = reactive({ teacherNo: '', username: '' })
const emptyOptions = () => ({ terms: [], teachers: [] })
const options = ref(emptyOptions())
const endpoint = (id, path = '') => '/education/teaching-center/support/setup' + path + '?' + new URLSearchParams({ schoolId: id })

watch(schoolId, async id => {
  options.value = emptyOptions()
  form.semesterCode = ''
  form.teacherId = ''
  if (!id) return
  const { data } = await http.get(endpoint(id))
  if (id !== schoolId.value) return
  options.value = data
  if (data.terms.length === 1) form.semesterCode = data.terms[0].value
})

async function save() {
  const id = schoolId.value
  const operation = mode.value
  saving.value = true
  try {
    if (operation === 'offering') {
      const { data } = await http.post(endpoint(id), {
        ...form, newTerm: newTerm.value ? { ...term, startDate: dates.value?.[0], endDate: dates.value?.[1] } : null,
      })
      if (id !== schoolId.value) return
      visible.value = false
      newTerm.value = false
      emit('saved', data)
    } else {
      const { data } = await http.post(endpoint(id, '/' + operation), operation === 'teachers' ? teacher : account)
      if (id !== schoolId.value) return
      if (operation === 'teachers') {
        options.value.teachers = [...options.value.teachers, data]
        form.teacherId = data.value
        Object.assign(teacher, { code: '', name: '' })
      } else {
        Object.assign(account, { teacherNo: '', username: '' })
      }
      mode.value = 'offering'
    }
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
</script>
