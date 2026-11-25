<template>
  <div class="incidents">
    <h3>考试异常</h3>
    <div class="controls">
      <el-select v-model="sessionId" placeholder="选择异常所在场次">
        <el-option v-for="item in sessions" :key="item.id" :label="sessionName(item)" :value="item.id" />
      </el-select>
      <el-button v-if="canManage" :disabled="!sessionId" @click="reporting = true">登记考试异常</el-button>
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="incidentType" label="类型" width="120" />
      <el-table-column prop="description" label="描述" min-width="180" />
      <el-table-column prop="severity" label="等级" width="100" />
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ statuses[row.status] }}</template></el-table-column>
      <el-table-column prop="conclusion" label="结论" min-width="130" />
      <AdaptiveActionColumn v-if="canManage" label="操作">
        <template #default="{ row }">
          <el-button v-if="steps[row.status]" type="primary" link @click="handle(row)">{{ steps[row.status].label }}</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="reporting" title="登记考试异常" width="min(500px, 95vw)">
      <el-form label-width="95px">
        <el-form-item label="异常类型"><el-input v-model="form.incidentType" maxlength="32" /></el-form-item>
        <el-form-item label="等级"><el-select v-model="form.severity"><el-option v-for="(label, code) in risks"
          :key="code" :label="label" :value="code" /></el-select></el-form-item>
        <el-form-item label="异常描述"><el-input v-model="form.description" type="textarea" maxlength="2000" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="report">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../../api/http'
import { hasAdminPermission } from '../../../store/auth'

const props = defineProps({ sessions: Array, sessionName: Function })
const root = '/admin/education/exam'
const sessionId = ref('')
const rows = ref([])
const reporting = ref(false)
const saving = ref(false)
const canManage = hasAdminPermission('education:exam:incident:manage')
const risks = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '重大' }
const statuses = { REPORTED: '已登记', UNDER_REVIEW: '核查中', DISPOSED: '已处置', CLOSED: '已结案' }
const steps = {
  REPORTED: { label: '启动核查', actionType: 'REVIEW' },
  UNDER_REVIEW: { label: '登记处置', actionType: 'DISPOSE' },
  DISPOSED: { label: '结案', actionType: 'CLOSE' },
}
const form = reactive({ incidentType: '', description: '', severity: 'LOW' })
async function load() {
  const id = sessionId.value
  if (!id) return
  const result = await http.get(root + '/sessions/' + encodeURIComponent(id) + '/incidents')
  if (id === sessionId.value) rows.value = result.data
}
watch(sessionId, () => { rows.value = []; load() })

async function report() {
  saving.value = true
  try {
    await http.post(root + '/incidents', { ...form, sessionId: sessionId.value })
    reporting.value = false
    Object.assign(form, { incidentType: '', description: '', severity: 'LOW' })
    ElMessage.success('异常已登记')
    await load()
  } finally {
    saving.value = false
  }
}

async function handle(row) {
  const { label, actionType } = steps[row.status]
  const result = await ElMessageBox.prompt('填写处置记录', label, {
    inputValidator: value => Boolean(value?.trim()) || '请填写处置记录',
  }).catch(() => null)
  if (!result) return
  await http.post(root + '/incidents/' + row.id + '/actions', { actionType, conclusion: result.value })
  await load()
}
</script>

<style scoped>
.incidents { margin-top: 20px; }
.controls { display: flex; gap: 10px; margin-bottom: 10px; }
.controls .el-select { width: 260px; }
</style>
