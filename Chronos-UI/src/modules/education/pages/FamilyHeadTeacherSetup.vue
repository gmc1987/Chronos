<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { listAdministrativeClasses, saveFamilyHeadTeacher } from '../api/familySetup'

const open = ref(false)
const busy = ref(false)
const classes = ref([])
const form = reactive({ schoolId: '', classId: '', username: '', teacherCode: '', newTeacherName: '' })
watch(() => form.schoolId, async (schoolId, previous, onCleanup) => {
  form.classId = ''
  classes.value = []
  if (!schoolId) return
  let expired = false
  onCleanup(() => { expired = true })
  const response = await listAdministrativeClasses({ schoolId })
  if (!expired) classes.value = response?.data || []
})

async function save() {
  if (!form.classId || !form.username.trim() || !form.teacherCode.trim()) {
    return ElMessage.warning('请选择班级并填写登录账号和教师编号')
  }
  busy.value = true
  try {
    await saveFamilyHeadTeacher(form)
    ElMessage.success('班主任已保存')
    open.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-button @click="open = true">设置班主任</el-button>
  <el-dialog v-model="open" title="设置班主任" width="540px">
    <el-form label-width="110px">
      <el-form-item label="所属学校"><OrganizationSelect v-model="form.schoolId" school-only /></el-form-item>
      <el-form-item label="行政班">
        <el-select v-model="form.classId" filterable>
          <el-option v-for="item in classes" :key="item.id" :value="item.id"
            :label="item.headTeacherName ? `${item.className} · ${item.headTeacherName}` : item.className" />
        </el-select>
      </el-form-item>
      <el-form-item label="教师账号"><el-input v-model="form.username" placeholder="输入客户管理端已创建的本校账号" /></el-form-item>
      <el-form-item label="教师编号"><el-input v-model="form.teacherCode" placeholder="已有档案请填写完整教师编号" /></el-form-item>
      <el-form-item label="新建教师姓名"><el-input v-model="form.newTeacherName" placeholder="仅新建教师档案时填写" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="busy" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
