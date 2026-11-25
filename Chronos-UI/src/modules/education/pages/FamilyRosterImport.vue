<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { importFamilyRoster } from '../api/familySetup'

const emit = defineEmits(['saved'])
const open = ref(false)
const busy = ref(false)
const schoolId = ref('')
const source = ref('')

async function save() {
  if (!schoolId.value) return ElMessage.warning('请选择名册所属学校')
  const lines = source.value.split(/\r?\n/).filter(line => line.trim())
  if (lines.length < 1 || lines.length > 500) return ElMessage.warning('每次请导入 1 至 500 名学生')
  const rows = []
  for (const [index, line] of lines.entries()) {
    const [classCode, className, year, studentNo, studentName, gender, ...extra] = line.split('\t').map(item => item.trim())
    if (!classCode || !className || !/^\d{4}$/.test(year) || !studentNo || !studentName || extra.length) {
      return ElMessage.warning(`第 ${index + 1} 行有误，请核对列数和必填内容`)
    }
    rows.push({ classCode, className, enrollmentYear: Number(year), studentNo, studentName, gender: gender || null })
  }
  busy.value = true
  try {
    await importFamilyRoster(schoolId.value, rows)
    ElMessage.success(`已处理 ${rows.length} 条名册记录`)
    source.value = ''
    open.value = false
    emit('saved')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-button @click="open = true">导入班级学生名册</el-button>
  <el-dialog v-model="open" title="导入班级学生名册" width="640px">
    <el-form label-position="top">
      <el-form-item label="所属学校">
        <OrganizationSelect v-model="schoolId" school-only />
      </el-form-item>
      <el-form-item label="班级编码、班级名称、入学年份、学号、姓名、性别（可空）">
        <el-input v-model="source" type="textarea" :rows="8" placeholder="从表格复制数据行。各列用 Tab 分隔，无需列标题。" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="busy" @click="save">导入名册</el-button>
    </template>
  </el-dialog>
</template>
