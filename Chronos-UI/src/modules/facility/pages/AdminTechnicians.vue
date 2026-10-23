<template>
  <section class="page">
    <header>
      <div><h2>维修人员管理</h2><p>维护维修技能、责任范围、值班时间和自动派单负载上限。</p></div>
      <el-button type="primary" @click="open()">新增维修人员</el-button>
    </header>
    <el-table :data="rows" border>
      <el-table-column prop="employeeCode" label="工号" width="130" />
      <el-table-column prop="technicianName" label="姓名" width="120" />
      <el-table-column prop="skillCategories" label="技能分类" min-width="160" />
      <el-table-column label="责任校区" min-width="180"><template #default="{ row }">{{ organizationNames(row.responsibilityOrganizationIds) }}</template></el-table-column>
      <el-table-column label="责任区域" min-width="150"><template #default="{ row }">{{ row.responsibilityAreas === 'ALL' ? '全部区域' : row.responsibilityAreas }}</template></el-table-column>
      <el-table-column label="值班" min-width="180"><template #default="{ row }">{{ dutyText(row) }}</template></el-table-column>
      <el-table-column label="负载" width="100"><template #default="{ row }">{{ row.currentLoad }} / {{ row.maxConcurrent }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ workLabels[row.workStatus] }}</template></el-table-column>
      <el-table-column label="自动派单" width="100"><template #default="{ row }">{{ row.autoDispatch ? '是' : '否' }}</template></el-table-column>
      <AdaptiveActionColumn label="操作" :width="90"><template #default="{ row }"><el-button link @click="open(row)">编辑</el-button></template></AdaptiveActionColumn>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑维修人员' : '新增维修人员'" width="720px">
      <el-form label-width="120px">
        <el-form-item label="员工">
          <el-select v-model="form.employeeId" filterable :disabled="!!form.id" style="width:100%">
            <el-option v-for="item in employees" :key="item.id" :value="item.id" :label="`${item.employeeName}（${item.employeeCode}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="技能分类">
          <el-select v-model="skills" multiple style="width:100%">
            <el-option v-for="(label, value) in categoryLabels" :key="value" :value="value" :label="label" />
            <el-option value="ALL" label="全类别" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任校区/机构">
          <el-select v-model="responsibilityOrganizations" multiple filterable collapse-tags style="width:100%">
            <el-option value="ALL" label="全部校区和机构" />
            <el-option v-for="item in organizations" :key="item.id" :value="item.id" :label="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任区域关键词">
          <el-input v-model="form.responsibilityAreas" placeholder="逗号分隔，例如：实训楼,图书馆；ALL 表示全部区域" />
        </el-form-item>
        <el-form-item label="值班星期">
          <el-select v-model="dutyDays" multiple style="width:100%">
            <el-option value="ALL" label="每天" />
            <el-option v-for="item in weekOptions" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="值班时段">
          <el-time-picker v-model="dutyRange" is-range value-format="HH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" />
          <span class="hint">支持跨午夜班次</span>
        </el-form-item>
        <el-form-item label="工作状态">
          <el-select v-model="form.workStatus" style="width:100%"><el-option v-for="(label, value) in workLabels" :key="value" :value="value" :label="label" /></el-select>
        </el-form-item>
        <el-form-item label="最大并发工单"><el-input-number v-model="form.maxConcurrent" :min="1" :max="100" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.active" /></el-form-item>
        <el-form-item label="参与自动派单"><el-switch v-model="form.autoDispatch" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { employees as loadEmployees } from '../../iam/api'
import { createTechnician, facilityOrganizationOptions, technicians, updateTechnician } from '../api'
import { categoryLabels } from '../labels'

const rows = ref([]), employees = ref([]), organizations = ref([]), dialog = ref(false)
const skills = ref([]), responsibilityOrganizations = ref(['ALL']), dutyDays = ref(['ALL'])
const dutyRange = ref(['00:00:00', '23:59:59'])
const workLabels = { AVAILABLE: '可接单', BUSY: '忙碌', OFFLINE: '离线' }
const weekOptions = [
  ['MONDAY', '周一'], ['TUESDAY', '周二'], ['WEDNESDAY', '周三'], ['THURSDAY', '周四'],
  ['FRIDAY', '周五'], ['SATURDAY', '周六'], ['SUNDAY', '周日']
].map(([value, label]) => ({ value, label }))
const form = reactive({ id: '', employeeId: '', workStatus: 'AVAILABLE', maxConcurrent: 5, active: true, autoDispatch: true, responsibilityAreas: 'ALL' })
const split = value => (value || '').split(',').map(item => item.trim()).filter(Boolean)
const load = async () => { rows.value = (await technicians()).data || [] }
const open = row => {
  Object.assign(form, row || { id: '', employeeId: '', workStatus: 'AVAILABLE', maxConcurrent: 5, active: true, autoDispatch: true, responsibilityAreas: 'ALL' })
  skills.value = split(row?.skillCategories)
  responsibilityOrganizations.value = split(row?.responsibilityOrganizationIds || 'ALL')
  dutyDays.value = split(row?.dutyDays || 'ALL')
  dutyRange.value = [row?.dutyStartTime || '00:00:00', row?.dutyEndTime || '23:59:59']
  dialog.value = true
}
const save = async () => {
  if (!form.employeeId || !skills.value.length) return ElMessage.warning('请选择员工和技能分类')
  if (!responsibilityOrganizations.value.length) return ElMessage.warning('请选择责任校区或全部校区')
  if (!dutyDays.value.length || !dutyRange.value?.length) return ElMessage.warning('请配置值班时间')
  const body = { ...form, skillCategories: skills.value.join(','), responsibilityOrganizationIds: responsibilityOrganizations.value.join(','), dutyDays: dutyDays.value.join(','), dutyStartTime: dutyRange.value[0], dutyEndTime: dutyRange.value[1] }
  form.id ? await updateTechnician(form.id, body) : await createTechnician(body)
  ElMessage.success('保存成功'); dialog.value = false; load()
}
const organizationNames = value => {
  const ids = split(value)
  if (ids.includes('ALL')) return '全部校区和机构'
  return ids.map(id => organizations.value.find(item => item.id === id)?.name || id).join('、')
}
const dutyText = row => {
  const days = split(row.dutyDays)
  const dayText = days.includes('ALL') ? '每天' : days.map(day => weekOptions.find(item => item.value === day)?.label || day).join('、')
  return `${dayText} ${row.dutyStartTime?.slice(0, 5)}-${row.dutyEndTime?.slice(0, 5)}`
}
onMounted(async () => {
  const [employeeResult, organizationResult] = await Promise.all([loadEmployees({ page: 0, size: 100 }), facilityOrganizationOptions()])
  employees.value = employeeResult.data?.content || employeeResult.data || []
  organizations.value = organizationResult.data || []
  await load()
})
</script>

<style scoped>
.page { padding: 24px }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px }
h2 { margin: 0 0 6px }
p, .hint { margin: 0; color: #84909a }
.hint { margin-left: 12px; font-size: 12px }
</style>
