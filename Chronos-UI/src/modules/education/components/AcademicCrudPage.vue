<template>
  <div class="page">
    <header>
      <div><h2>{{ title }}</h2><p>{{ description }}</p></div>
      <div class="header-actions">
        <slot name="actions" />
        <span v-if="importKind" v-permission="importKind === 'TEACHER_PROFILES' ? updatePermissions : createPermissions">
          <AcademicCatalogImport
            :kind="importKind"
            :label="entityLabel"
            :organization-id="campusScoped || organizationScoped ? campusId : undefined"
            @imported="load"
          />
        </span>
        <el-button
          v-if="allowCreate && !permissionPrefix"
          type="primary"
           :disabled="(campusScoped || organizationScoped) && !campusId"
          @click="openCreate"
        >
          新增{{ entityLabel }}
        </el-button>
        <el-button
          v-else-if="allowCreate"
          v-permission="createPermissions"
          type="primary"
           :disabled="(campusScoped || organizationScoped) && !campusId"
          @click="openCreate"
        >
          新增{{ entityLabel }}
        </el-button>
      </div>
    </header>
    <div v-if="campusScoped || organizationScoped || searchable || $slots.filters" class="query-controls">
      <div v-if="campusScoped || organizationScoped" class="organization-filter">
        <OrganizationSelect v-model="campusId" auto-select-single school-or-campus-only />
      </div>
      <template v-if="searchable">
        <el-input v-model="keyword" clearable placeholder="输入关键词查询" style="width:220px"
          @keyup.enter="reloadFromFirstPage" @clear="reloadFromFirstPage" />
        <el-button :disabled="(campusScoped || organizationScoped) && !campusId" @click="reloadFromFirstPage">查询</el-button>
      </template>
      <slot name="filters" :lookups="lookupData" :reload="reloadFromFirstPage" />
    </div>
    <slot name="summary" :data="listResult" :loading="loading" />
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :label="column.label"
        :prop="column.prop"
        :width="column.width"
        :min-width="column.minWidth"
      >
        <template #default="scope">
          {{ displayValue(column, scope.row[column.prop]) }}
        </template>
      </el-table-column>
      <AdaptiveActionColumn label="操作" :width="actionColumnWidth" class-name="crud-actions">
        <template #default="scope">
          <div class="row-actions">
            <el-button
              v-if="!permissionPrefix"
              link
              type="primary"
              @click="openEdit(scope.row)"
            >
              编辑
            </el-button>
            <el-button
              v-else
              v-permission="updatePermissions"
              link
              type="primary"
              @click="openEdit(scope.row)"
            >
              编辑
            </el-button>
            <el-button v-for="action in rowActions" :key="action.label" link :type="action.type || 'primary'" @click="runRowAction(action, scope.row)">{{ action.label }}</el-button>
            <el-button v-if="deleter" link type="danger" @click="remove(scope.row)">删除</el-button>
          </div>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="load"
    />
    <el-dialog v-model="dialog" :title="`${form.id ? '编辑' : '新增'}${entityLabel}`" width="640px">
      <el-form label-width="110px">
        <el-form-item v-for="field in fields" :key="field.prop" :label="field.label">
          <el-input v-if="form.id && field.readOnlyDisplay" :model-value="field.readOnlyDisplay(form[field.prop])" readonly />
          <el-select v-else-if="field.dictCode" filterable v-model="form[field.prop]" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)"><el-option v-for="option in dictionaryData[field.dictCode] || []" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-select v-else-if="field.options" v-model="form[field.prop]" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)"><el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.prop]" value-format="YYYY-MM-DD" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)" />
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :min="field.min ?? 0" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)" />
          <el-switch v-else-if="field.type === 'boolean'" v-model="form[field.prop]" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)" />
          <el-select v-else-if="field.lookup" v-model="form[field.prop]" filterable :multiple="field.multiple" :clearable="field.clearable" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)"><el-option v-for="option in (lookupData[field.lookup] || []).filter(option => !field.optionFilter || field.optionFilter(option, form))" :key="option.id" :label="option[field.labelProp]" :value="option.id" /></el-select>
          <el-input v-else v-model="form[field.prop]" :placeholder="field.placeholder" :disabled="field.disabled || (field.immutableOnEdit && !!form.id)" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import AcademicCatalogImport from './AcademicCatalogImport.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { dictionaryOptions } from '../../../api/admin'

const props = defineProps({ importKind: String, searchable: Boolean, organizationScoped: Boolean, campusScoped: Boolean, title: String, description: String, entityLabel: String, columns: Array, fields: Array, defaults: Object, loader: Function, creator: Function, updater: Function, deleter: Function, rowActions: { type: Array, default: () => [] }, permissionPrefix: String, lookups: { type: Array, default: () => [] }, allowCreate: { type: Boolean, default: true } })
const rows = ref([]); const loading = ref(false); const dialog = ref(false); const form = reactive({}); const lookupData = reactive({}); const dictionaryData = reactive({})
const campusId = defineModel('campusId', { type: String, default: '' })
const page = ref(1); const pageSize = ref(10); const total = ref(0)
const listResult = ref(null)
const keyword = ref('')
const createPermissions = computed(() => [`${props.permissionPrefix}:create`, `${props.permissionPrefix}:manage`])
const updatePermissions = computed(() => [`${props.permissionPrefix}:update`, `${props.permissionPrefix}:manage`])
const actionColumnWidth = computed(() => 72 + props.rowActions.length * 92 + (props.deleter ? 62 : 0))
const reset = value => { Object.keys(form).forEach(key => delete form[key]); Object.assign(form, value) }
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  if ((props.campusScoped || props.organizationScoped) && !campusId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const response = await props.loader({ page: page.value - 1, size: pageSize.value, ...(props.searchable && keyword.value.trim() ? { keyword: keyword.value.trim() } : {}), ...(props.organizationScoped ? { organizationId: campusId.value } : props.campusScoped ? { campusId: campusId.value } : {}) })
    if (sequence !== loadSequence) return
    listResult.value = response.data
    rows.value = response.data?.content || response.data || []
    total.value = response.data?.totalElements ?? rows.value.length
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const reloadFromFirstPage = () => {
  rows.value = []
  total.value = 0
  listResult.value = null
  page.value = 1
  return load()
}
watch(campusId, () => {
  keyword.value = ''
  dialog.value = false
  reloadFromFirstPage()
  for (const item of props.lookups.filter(lookup => lookup.campusScoped)) loadLookup(item)
})
const changePageSize = () => { page.value = 1; load() }
const lookupVersions = {}
const loadLookup = async item => {
  const version = (lookupVersions[item.key] || 0) + 1
  lookupVersions[item.key] = version
  if (item.campusScoped) {
    lookupData[item.key] = []
    if (!campusId.value) return
  }
  const argument = item.campusScoped ? campusId.value : item.dependentOn ? form[item.dependentOn] : undefined
  const response = await item.loader(argument)
  if (lookupVersions[item.key] === version) lookupData[item.key] = response.data?.content || response.data || []
}
const loadLookups = async () => { for (const item of props.lookups) await loadLookup(item) }
for (const dependency of new Set(props.lookups.map(item => item.dependentOn).filter(Boolean))) {
  watch(() => form[dependency], () => {
    for (const item of props.lookups.filter(lookup => lookup.dependentOn === dependency)) loadLookup(item)
  })
}
const loadDictionaries = async () => {
  const codes = [...new Set((props.fields || []).map(field => field.dictCode).filter(Boolean))]
  await Promise.all(codes.map(async code => {
    const response = await dictionaryOptions(code)
    dictionaryData[code] = (response?.data || []).map(item => ({ label: item.dictName, value: item.dictValue }))
  }))
}
const openCreate = () => { reset({ ...(props.defaults || {}), ...(props.organizationScoped ? { organizationId: campusId.value } : props.campusScoped ? { campusId: campusId.value } : {}) }); dialog.value = true }
const openEdit = row => { reset({ ...row }); dialog.value = true }
const save = async () => { await (form.id ? props.updater(form.id, form) : props.creator(form)); dialog.value = false; ElMessage.success('保存成功'); await load(); await loadLookups() }
const runRowAction = async (action, row) => {
  await action.run(row)
  if (action.successMessage !== false) ElMessage.success(`${action.label}成功`)
  if (action.reload !== false) await load()
}
const displayValue = (column, value) => {
	if (column.valueMap) return column.valueMap[value] || value || '-'
  if (column.dictCode) {
    return dictionaryData[column.dictCode]?.find(option => option.value === value)?.label || value || '-'
  }
  if (column.lookup) {
    const lookup = props.lookups.find(item => item.key === column.lookup)
    const label = id => lookupData[column.lookup]?.find(option => option.id === id)?.[lookup?.labelProp] || id
    return Array.isArray(value) ? value.map(label).join('、') || '-' : label(value) || '-'
  }
  if (column.type === 'boolean') return value ? '是' : '否'
  return value === null || value === undefined || value === '' ? '-' : value
}
const remove = async row => {
  await ElMessageBox.confirm(`确认删除${props.entityLabel}“${row[props.columns[0]?.prop] || ''}”？`, '删除确认', { type: 'warning' })
  await props.deleter(row.id)
  ElMessage.success('删除成功')
  if (!rows.value.length && page.value > 1) page.value -= 1
  await load()
}
// 列表与表单下拉数据互不依赖，首次进入时并行加载；大规模教职工数据下
// 不再先等待全量下拉项，避免教师档案页面长时间显示为“0 条”。
onMounted(async () => { await Promise.all([load(), loadLookups(), loadDictionaries()]) })
</script>

<style scoped>
.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } .header-actions { display: flex; gap: 10px; } .el-pagination { justify-content: flex-end; margin-top: 16px; }
.query-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.organization-filter { width: 260px; }
.row-actions { display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap; }
.row-actions .el-button { flex: none; margin-left: 8px; }
h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }
</style>
