<template>
  <AdmissionCatalogSetup teaching :school-id="schoolId" :majors="majors" :subjects="subjects" @saved="reload" />
  <el-button :disabled="!schoolId" @click="open('subject')">新增学科</el-button>
  <el-button :disabled="!schoolId" @click="open('teacher')">登记任课教师</el-button>
  <el-button :disabled="!schoolId" @click="open('class-roster')">导入在读名单</el-button>
  <el-button :disabled="!schoolId" @click="open('account-links')">绑定课表账号</el-button>
  <el-dialog v-model="visible" :title="labels[action]" width="580px">
    <el-form label-width="100px" @submit.prevent="save">
      <template v-if="action === 'class-roster'">
        <el-form-item label="行政班">
          <el-select v-model="classId" filterable>
            <el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="在读名单">
          <el-input v-model="text" type="textarea" :rows="7" placeholder="从表格粘贴学号、姓名、性别（可空）。每次最多 500 行，省略表头。" />
        </el-form-item>
      </template>
      <template v-else-if="action === 'account-links'">
        <el-form-item label="身份">
          <el-select v-model="profileType">
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="profileType === 'TEACHER' ? '教师工号' : '学号'">
          <el-input v-model.trim="code" maxlength="64" />
        </el-form-item>
        <el-form-item label="登录账号"><el-input v-model.trim="name" maxlength="100" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="编码"><el-input v-model.trim="code" maxlength="32" /></el-form-item>
        <el-form-item label="名称"><el-input v-model.trim="name" maxlength="100" /></el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdministrativeClasses, listEducationMajors, listEducationSubjects } from '../../../api/admin'
import { http } from '../../../api/http'
import AdmissionCatalogSetup from './AdmissionCatalogSetup.vue'

const props = defineProps({ schoolId: String })
const emit = defineEmits(['saved'])
const labels = { subject: '新增学科', teacher: '登记任课教师', 'class-roster': '导入在读名单', 'account-links': '绑定课表账号' }
const majors = ref([])
const subjects = ref([])
const classes = ref([])
const visible = ref(false)
const saving = ref(false)
const action = ref('subject')
const code = ref('')
const name = ref('')
const classId = ref('')
const text = ref('')
const profileType = ref('TEACHER')

async function reload() {
  const school = props.schoolId
  majors.value = []
  subjects.value = []
  classes.value = []
  if (!school) return
  const [majorList, subjectList, classList] = await Promise.all([
    listEducationMajors({ organizationId: school }),
    listEducationSubjects({ organizationId: school }),
    listAdministrativeClasses({ campusId: school }),
  ])
  if (school !== props.schoolId) return
  majors.value = majorList.data || []
  subjects.value = (subjectList.data || []).filter(item => item.enabled)
  classes.value = (classList.data || []).filter(item => item.status === 'ACTIVE' && item.gradeId && item.majorId)
}

watch(() => props.schoolId, () => {
  visible.value = false
  reload()
}, { immediate: true })

function open(value) {
  action.value = value
  code.value = name.value = classId.value = text.value = ''
  profileType.value = 'TEACHER'
  visible.value = true
}

function rosterRows() {
  return text.value.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
    const [studentNo, studentName, gender, ...extra] = line.split('\t').map(value => value.trim())
    if (!studentNo || !studentName || extra.length) throw new Error('请检查第 ' + (index + 1) + ' 行的学号、姓名和性别')
    return { studentNo, studentName, gender }
  })
}

async function save() {
  if (saving.value || !props.schoolId) return
  const school = props.schoolId
  const mode = action.value
  let payload
  try {
    if (mode === 'class-roster') {
      const rows = rosterRows()
      if (!classId.value || !rows.length || rows.length > 500) throw new Error('请选择行政班并粘贴 1 至 500 名学生')
      payload = { classId: classId.value, rows }
    } else if (mode === 'account-links') {
      if (!code.value || !name.value) throw new Error('请填写工号或学号以及已有的学校账号')
      payload = { profileType: profileType.value, code: code.value, username: name.value }
    } else {
      if (!code.value || !name.value) throw new Error('请填写编码和名称')
      payload = { code: code.value, name: name.value }
    }
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  saving.value = true
  try {
    const { data } = await http.post('/admin/education/term-course-plans/setup/' + mode
      + '?' + new URLSearchParams({ schoolId: school }), payload)
    if (school !== props.schoolId) return
    visible.value = false
    ElMessage.success(mode === 'class-roster' ? '已新增 ' + data.added + ' 名学生，复用 ' + data.existing + ' 名' : '已保存')
    if (mode === 'subject') await reload()
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
