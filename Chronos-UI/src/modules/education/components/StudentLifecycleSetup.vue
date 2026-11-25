<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'
import { hasAdminPermission } from '../../../store/auth'

const props = defineProps({ schoolId: String, termCode: String, section: String, teaching: Boolean })
const emit = defineEmits(['saved'])
const path = computed(() => props.teaching ? '/admin/education/term-course-plans/setup/'
  : '/admin/education/academic-lifecycle/student-setup/')
const titles = computed(() => ({
  term: props.teaching ? '新建教学学期' : '新建注册学期',
  sections: '导入外部开课',
  'course-evidence': '导入课程通过证明',
}))
const mode = computed(() => ({ registrations: 'term', enrollments: 'sections', graduation: 'course-evidence' })[props.section])
const permission = computed(() => ({
  term: props.teaching ? 'education:term-course-plan:setup' : 'education:term-registration:manage',
  sections: 'education:course-enrollment:manage',
  'course-evidence': 'education:graduation-audit:manage',
})[mode.value])
const visible = ref(false)
const busy = ref(false)
const text = ref('')
const term = reactive({ academicYear: '', termNo: 1, name: '', startDate: '', endDate: '', weeks: 20 })
const format = computed(() => mode.value === 'sections'
  ? '教学班编码、教学班名称、课程编码、外部任课教师姓名（可空）'
  : '学号、课程编码、通过或未通过、校方证明编号或来源说明')

function rowsFromText() {
  const lines = text.value.split(/\r?\n/).filter(line => line.trim())
  if (!lines.length || lines.length > 500) throw new Error('每次请粘贴 1 至 500 行')
  return lines.map((line, index) => {
    const [first, second, third, fourth, ...extra] = line.split('\t').map(value => value.trim())
    if (!first || !second || !third || extra.length) throw new Error(`第 ${index + 1} 行列数或字段有误`)
    if (mode.value === 'sections') return { sectionCode: first, sectionName: second, courseCode: third, teacherName: fourth || null }
    if (!['通过', '未通过'].includes(third) || !fourth) throw new Error(`第 ${index + 1} 行须填写通过状态和证明来源`)
    return { studentNo: first, courseCode: second, passed: third === '通过', reference: fourth }
  })
}

async function save() {
  let body
  try {
    if (mode.value === 'term') {
      if (!term.academicYear || !term.name.trim() || !term.startDate || !term.endDate) {
        throw new Error('请填写学年、名称和起止日期')
      }
      body = term
    } else {
      body = { rows: rowsFromText() }
      if (mode.value === 'sections') body.termCode = props.termCode
    }
  } catch (error) { return ElMessage.warning(error.message) }
  busy.value = true
  try {
    await http.post(`${path.value}${mode.value}?schoolId=${encodeURIComponent(props.schoolId)}`, body)
    visible.value = false
    text.value = ''
    ElMessage.success(mode.value === 'term' ? '学期已保存' : '导入成功')
    emit('saved', mode.value)
  } finally { busy.value = false }
}
</script>

<template>
  <template v-if="mode && hasAdminPermission(permission)">
    <el-button :disabled="!schoolId || (mode === 'sections' && !termCode)" @click="visible = true">{{ titles[mode] }}</el-button>
    <el-dialog v-model="visible" :title="titles[mode]" width="550px">
      <el-form v-if="mode === 'term'" label-width="100px">
        <el-form-item label="学年"><el-input v-model="term.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学期序号"><el-input-number v-model="term.termNo" :min="1" :max="3" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="term.name" /></el-form-item>
        <el-form-item label="开始日期"><el-input v-model="term.startDate" type="date" /></el-form-item>
        <el-form-item label="结束日期"><el-input v-model="term.endDate" type="date" /></el-form-item>
        <el-form-item label="教学周数"><el-input-number v-model="term.weeks" :min="1" :max="53" /></el-form-item>
      </el-form>
      <el-form v-else label-position="top">
        <el-form-item :label="format">
          <el-input v-model="text" type="textarea" :rows="8" placeholder="从表格复制数据行，使用 Tab 分隔各列，无需标题行。" />
        </el-form-item>
        <p v-if="mode === 'course-evidence'">同一学生同一门课程的证明将更新。毕业核验优先采用已发布成绩。</p>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </template>
</template>
