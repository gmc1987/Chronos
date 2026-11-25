<template>
  <el-button @click="visible = true">{{ title }}</el-button>
  <el-dialog v-model="visible" :title="title" width="min(750px, 95vw)">
    <el-form label-width="110px" @submit.prevent>
      <el-form-item label="学校"><OrganizationSelect v-model="schoolId" school-only /></el-form-item>
      <el-form-item label="操作">
        <el-radio-group v-model="mode">
          <el-radio-button value="roster">导入教学班</el-radio-button>
          <el-radio-button value="teachers">登记教师</el-radio-button>
          <el-radio-button value="student-links">绑定学生账号</el-radio-button>
          <el-radio-button v-if="homework" value="teacher-links">绑定教师账号</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="mode === 'teachers'">
        <el-form-item label="教师编号"><el-input v-model="teacher.code" maxlength="32" /></el-form-item>
        <el-form-item label="教师姓名"><el-input v-model="teacher.name" maxlength="100" /></el-form-item>
      </template>
      <template v-else-if="mode === 'student-links'">
        <el-form-item label="学号"><el-input v-model="student.studentNo" /></el-form-item>
        <el-form-item label="本校登录账号"><el-input v-model="student.username" /></el-form-item>
      </template>
      <template v-else-if="mode === 'teacher-links'">
        <el-form-item label="完整教师编号"><el-input v-model="teacherLink.teacherNo" /></el-form-item>
        <el-form-item label="本校登录账号"><el-input v-model="teacherLink.username" /></el-form-item>
      </template>
      <template v-else>
      <el-form-item label="学期">
        <el-select v-model="form.semesterCode" :disabled="!schoolId || newTerm" placeholder="选择本校学期">
          <el-option v-for="item in options.terms" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="newTerm">新建学期</el-checkbox>
      </el-form-item>
      <template v-if="newTerm">
        <el-form-item label="学年"><el-input v-model="term.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学期序号"><el-input-number v-model="term.termNo" :min="1" :max="3" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="term.name" /></el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="教学周数"><el-input-number v-model="term.weeks" :min="1" :max="53" /></el-form-item>
      </template>
      <el-form-item label="课程编码"><el-input v-model="form.courseCode" maxlength="64" /></el-form-item>
      <el-form-item label="课程名称"><el-input v-model="form.courseName" maxlength="128" /></el-form-item>
      <el-form-item label="教学班编码"><el-input v-model="form.classCode" maxlength="64" /></el-form-item>
      <el-form-item label="教学班名称"><el-input v-model="form.className" maxlength="128" /></el-form-item>
      <el-form-item label="任课教师">
        <el-select v-model="form.teacherId" placeholder="选择本校任课教师" filterable>
          <el-option v-for="item in options.teachers" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="学生名册">
        <div>
          <p>从表格复制数据行。列顺序为行政班编码、行政班名称、入学年份、学号、学生姓名、性别。性别可留空，每批最多 500 人。</p>
          <el-input v-model="rows" :aria-label="homework ? '作业学生名册' : '成绩学生名册'" type="textarea" :rows="5" />
        </div>
      </el-form-item>
      </template>
    </el-form>
    <template #footer><el-button type="primary" :loading="saving" :disabled="!schoolId" @click="save">{{ mode === 'roster' ? '确认导入' : '保存' }}</el-button></template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { http } from '../../../api/http'

const props = defineProps({ homework: Boolean })
const title = props.homework ? '建立作业专用教学班' : '建立成绩专用教学班'
const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const schoolId = ref('')
const rows = ref('')
const mode = ref('roster')
const teacher = reactive({ code: '', name: '' })
const student = reactive({ studentNo: '', username: '' })
const teacherLink = reactive({ teacherNo: '', username: '' })
const newTerm = ref(false)
const dates = ref([])
const term = reactive({ academicYear: '', termNo: 1, name: '', weeks: 20 })
const form = reactive({ semesterCode: '', courseCode: '', courseName: '', classCode: '', className: '', teacherId: '' })
const options = ref({ terms: [], teachers: [] })
const setupPath = props.homework ? '/education/teaching-center/homework-support/roster-setup' : '/admin/education/grades/roster-setup'
const endpoint = (id, path = '') => setupPath + path + '?' + new URLSearchParams({ schoolId: id })

watch(schoolId, async id => {
  options.value = { terms: [], teachers: [] }
  form.semesterCode = ''
  form.teacherId = ''
  if (id) {
    const { data } = await http.get(endpoint(id))
    if (id !== schoolId.value) return
    options.value = data
    if (data.terms.length === 1) form.semesterCode = data.terms[0].value
  }
})

async function save() {
  saving.value = true
  try {
    if (mode.value !== 'roster') {
      const current = mode.value
      const selection = { teachers: teacher, 'student-links': student, 'teacher-links': teacherLink }
      const { data } = await http.post(endpoint(schoolId.value, '/' + current), selection[current])
      if (current === 'teachers') {
        options.value.teachers = [...options.value.teachers, data]
        form.teacherId = data.value
        Object.assign(teacher, { code: '', name: '' })
      } else if (current === 'student-links') {
        Object.assign(student, { studentNo: '', username: '' })
      } else {
        Object.assign(teacherLink, { teacherNo: '', username: '' })
      }
      mode.value = 'roster'
      ElMessage.success(current === 'teachers' ? '本校教师已登记' : '账号已绑定')
      return
    }
    const students = rows.value.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
      const columns = line.split('\t')
      if (columns.length < 5 || columns.length > 6) throw new Error(`第 ${index + 1} 行需要 5 至 6 列`)
      const [classCode, className, enrollmentYear, studentNo, studentName, gender] = columns
      return { classCode, className, enrollmentYear: Number(enrollmentYear), studentNo, studentName, gender }
    })
    const { data } = await http.post(endpoint(schoolId.value), {
      ...form, students,
      newTerm: newTerm.value ? { ...term, startDate: dates.value?.[0], endDate: dates.value?.[1] } : null,
    })
    visible.value = false
    rows.value = ''
    newTerm.value = false
    ElMessage.success(`教学班已创建，共 ${data.count} 名学生`)
    emit('saved', data)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}
</script>
