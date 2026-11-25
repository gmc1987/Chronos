<template>
  <el-button @click="visible = true">督导教师档案</el-button>
  <el-dialog v-model="visible" title="督导教师档案" width="min(560px, 95vw)">
    <el-form label-width="110px">
      <el-form-item label="学校"><OrganizationSelect v-model="schoolId" school-only /></el-form-item>
      <el-form-item label="操作">
        <el-radio-group v-model="operation">
          <el-radio-button value="register">登记教师</el-radio-button>
          <el-radio-button value="bind">绑定账号</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="operation === 'register'">
        <el-form-item label="教师编号"><el-input v-model="teacher.code" maxlength="32" /></el-form-item>
        <el-form-item label="教师姓名"><el-input v-model="teacher.name" maxlength="100" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="完整教师编号"><el-input v-model="link.teacherNo" /></el-form-item>
        <el-form-item label="本校登录账号"><el-input v-model="link.username" /></el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="!schoolId" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { bindSupervisionTeacher, setupSupervisionTeacher } from '../../../api/admin'

const visible = ref(false)
const saving = ref(false)
const schoolId = ref('')
const operation = ref('register')
const teacher = reactive({ code: '', name: '' })
const link = reactive({ teacherNo: '', username: '' })

async function save() {
  saving.value = true
  try {
    if (operation.value === 'register') {
      const { data } = await setupSupervisionTeacher(schoolId.value, teacher)
      link.teacherNo = data.code
      operation.value = 'bind'
      Object.assign(teacher, { code: '', name: '' })
      ElMessage.success('教师已登记，可以绑定已有学校账号')
    } else {
      await bindSupervisionTeacher(schoolId.value, link)
      Object.assign(link, { teacherNo: '', username: '' })
      operation.value = 'register'
      ElMessage.success('教师账号已绑定')
    }
  } finally {
    saving.value = false
  }
}
</script>
