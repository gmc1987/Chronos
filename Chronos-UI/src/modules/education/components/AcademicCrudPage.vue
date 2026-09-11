<template>
  <div class="page">
    <header>
      <div><h2>{{ title }}</h2><p>{{ description }}</p></div>
      <div class="header-actions">
        <slot name="actions" />
        <el-button
          v-if="!permissionPrefix"
          type="primary"
          @click="openCreate"
        >
          新增{{ entityLabel }}
        </el-button>
        <el-button
          v-else
          v-permission="createPermissions"
          type="primary"
          @click="openCreate"
        >
          新增{{ entityLabel }}
        </el-button>
      </div>
    </header>
    <el-table :data="rows" border>
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
      <el-table-column label="操作" :width="actionColumnWidth" class-name="crud-actions">
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
      </el-table-column>
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
          <el-select v-if="field.dictCode" v-model="form[field.prop]"><el-option v-for="option in dictionaryData[field.dictCode] || []" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-select v-else-if="field.options" v-model="form[field.prop]"><el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.prop]" value-format="YYYY-MM-DD" />
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :min="field.min ?? 0" />
          <el-switch v-else-if="field.type === 'boolean'" v-model="form[field.prop]" />
          <el-select v-else-if="field.lookup" v-model="form[field.prop]" filterable><el-option v-for="option in lookupData[field.lookup] || []" :key="option.id" :label="option[field.labelProp]" :value="option.id" /></el-select>
          <el-input v-else v-model="form[field.prop]" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictionaryOptions } from '../../../api/admin'

const props = defineProps({ title: String, description: String, entityLabel: String, columns: Array, fields: Array, defaults: Object, loader: Function, creator: Function, updater: Function, deleter: Function, rowActions: { type: Array, default: () => [] }, permissionPrefix: String, lookups: { type: Array, default: () => [] } })
const rows = ref([]); const dialog = ref(false); const form = reactive({}); const lookupData = reactive({}); const dictionaryData = reactive({})
const page = ref(1); const pageSize = ref(10); const total = ref(0)
const createPermissions = computed(() => [`${props.permissionPrefix}:create`, `${props.permissionPrefix}:manage`])
const updatePermissions = computed(() => [`${props.permissionPrefix}:update`, `${props.permissionPrefix}:manage`])
const actionColumnWidth = computed(() => 72 + props.rowActions.length * 92 + (props.deleter ? 62 : 0))
const reset = value => { Object.keys(form).forEach(key => delete form[key]); Object.assign(form, value) }
const load = async () => {
  const response = await props.loader({ page: page.value - 1, size: pageSize.value })
  rows.value = response.data?.content || response.data || []
  total.value = response.data?.totalElements ?? rows.value.length
}
const changePageSize = () => { page.value = 1; load() }
const loadLookups = async () => { for (const item of props.lookups) { const response = await item.loader(); lookupData[item.key] = response.data || [] } }
const loadDictionaries = async () => {
  const codes = [...new Set((props.fields || []).map(field => field.dictCode).filter(Boolean))]
  await Promise.all(codes.map(async code => {
    const response = await dictionaryOptions(code)
    dictionaryData[code] = (response?.data || []).map(item => ({ label: item.dictName, value: item.dictValue }))
  }))
}
const openCreate = () => { reset({ ...(props.defaults || {}) }); dialog.value = true }
const openEdit = row => { reset({ ...row }); dialog.value = true }
const save = async () => { await (form.id ? props.updater(form.id, form) : props.creator(form)); dialog.value = false; ElMessage.success('保存成功'); await load(); await loadLookups() }
const runRowAction = async (action, row) => {
  await action.run(row)
  ElMessage.success(`${action.label}成功`)
  await load()
}
const displayValue = (column, value) => {
  if (column.dictCode) {
    return dictionaryData[column.dictCode]?.find(option => option.value === value)?.label || value || '-'
  }
  if (column.lookup) {
    const lookup = props.lookups.find(item => item.key === column.lookup)
    return lookupData[column.lookup]?.find(option => option.id === value)?.[lookup?.labelProp] || value || '-'
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
onMounted(async () => { await Promise.all([loadLookups(), loadDictionaries()]); await load() })
</script>

<style scoped>
.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } .header-actions { display: flex; gap: 10px; } .el-pagination { justify-content: flex-end; margin-top: 16px; }
.row-actions { display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap; }
.row-actions .el-button { flex: none; margin-left: 8px; }
h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }
</style>
