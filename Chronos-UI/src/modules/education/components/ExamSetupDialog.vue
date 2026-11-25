<template>
  <el-button :disabled="!schoolId" @click="open">考务基础数据</el-button>
  <el-dialog v-model="visible" title="考务基础数据" width="min(640px, 95vw)">
    <el-form label-width="105px" @submit.prevent>
      <el-form-item label="维护内容">
        <el-select v-model="kind">
          <el-option v-for="(name, key) in choices" :key="key" :label="name" :value="key" />
        </el-select>
      </el-form-item>
      <template v-if="kind === 'terms'">
        <el-form-item label="学年"><el-input v-model="term.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学期序号"><el-input-number v-model="term.termNo" :min="1" :max="3" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="term.name" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="term.startDate" placeholder="开始日期" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="term.endDate" placeholder="结束日期" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="教学周数"><el-input-number v-model="term.weeks" :min="1" :max="53" /></el-form-item>
      </template>
      <template v-else-if="kind === 'roster'">
        <p>粘贴电子表格中的数据行，列顺序为班级编码、班级名称、入学年份、学号、学生姓名、性别。性别可留空，每批最多 500 人。</p>
        <el-input v-model="roster" aria-label="考生名册" type="textarea" :rows="8" />
      </template>
      <template v-else-if="kind === 'account-links'">
        <el-form-item label="人员类型"><el-select v-model="account.type">
          <el-option label="考生" value="STUDENT" /><el-option label="监考人员" value="TEACHER" />
        </el-select></el-form-item>
        <el-form-item label="学号或监考编号"><el-input v-model="account.code" /></el-form-item>
        <el-form-item label="本校登录账号"><el-input v-model="account.username" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="编码"><el-input v-model="resource.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="resource.name" maxlength="100" /></el-form-item>
        <el-form-item v-if="kind === 'rooms'" label="考场容量"><el-input-number v-model="resource.capacity" :min="1" /></el-form-item>
      </template>
    </el-form>
    <template #footer><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveExamSetup } from '../api/examSetup'

const props = defineProps({ schoolId: String })
const emit = defineEmits(['saved'])
const choices = { terms: '新建学期', roster: '导入考生', subjects: '新增考试科目', rooms: '新增考场', teachers: '新增监考人员', 'account-links': '绑定本校账号' }
const kind = ref('terms')
const visible = ref(false)
const saving = ref(false)
const term = reactive({ academicYear: '', termNo: 1, name: '', startDate: '', endDate: '', weeks: 20 })
const resource = reactive({ code: '', name: '', capacity: 40 })
const roster = ref('')
const account = reactive({ type: 'STUDENT', code: '', username: '' })
const open = () => { visible.value = true }
watch(() => props.schoolId, () => { visible.value = false })
watch(kind, () => Object.assign(resource, { code: '', name: '', capacity: 40 }))

async function save() {
  const schoolId = props.schoolId
  const operation = kind.value
  saving.value = true
  try {
    let payload = operation === 'terms' ? term : operation === 'account-links' ? account : resource
    if (operation === 'roster') {
      payload = roster.value.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
        const columns = line.split('\t')
        if (columns.length < 5 || columns.length > 6) throw new Error(`第 ${index + 1} 行需要 5 至 6 列`)
        const [classCode, className, enrollmentYear, studentNo, studentName, gender] = columns
        return { classCode, className, enrollmentYear: Number(enrollmentYear), studentNo, studentName, gender }
      })
    }
    const { data } = await saveExamSetup(schoolId, operation, payload)
    if (schoolId !== props.schoolId) return
    if (operation === 'roster') {
      roster.value = ''
      ElMessage.success(`新增 ${data.added} 人，复用 ${data.existing} 人`)
    } else {
      ElMessage.success('已保存')
    }
    visible.value = false
    emit('saved', operation, data)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}
</script>
