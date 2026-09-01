<template>
  <el-form :model="value" label-width="120px">
    <el-form-item v-for="field in fields" :key="field.fieldKey" :label="field.fieldLabel" :required="field.required">
      <el-input v-if="field.fieldType === 'TEXT'" v-model="value[field.fieldKey]" :disabled="readonly(field)" />
      <el-input v-else-if="field.fieldType === 'TEXTAREA'" v-model="value[field.fieldKey]" type="textarea" :disabled="readonly(field)" />
      <el-input-number v-else-if="field.fieldType === 'NUMBER'" v-model="value[field.fieldKey]" :disabled="readonly(field)" style="width:100%" />
      <el-date-picker v-else-if="field.fieldType === 'DATE'" v-model="value[field.fieldKey]" type="date" value-format="YYYY-MM-DD" :disabled="readonly(field)" style="width:100%" />
      <el-date-picker v-else-if="field.fieldType === 'DATETIME'" v-model="value[field.fieldKey]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :disabled="readonly(field)" style="width:100%" />
      <el-select v-else-if="field.fieldType === 'SELECT'" v-model="value[field.fieldKey]" :disabled="readonly(field)" style="width:100%"><el-option v-for="option in options(field)" :key="option.value" :label="option.label" :value="option.value" /></el-select>
      <el-radio-group v-else-if="field.fieldType === 'RADIO'" v-model="value[field.fieldKey]" :disabled="readonly(field)"><el-radio v-for="option in options(field)" :key="option.value" :value="option.value">{{ option.label }}</el-radio></el-radio-group>
      <el-checkbox-group v-else-if="field.fieldType === 'CHECKBOX'" v-model="value[field.fieldKey]" :disabled="readonly(field)"><el-checkbox v-for="option in options(field)" :key="option.value" :value="option.value">{{ option.label }}</el-checkbox></el-checkbox-group>
      <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="value[field.fieldKey]" :disabled="readonly(field)" />
      <el-upload v-else-if="field.fieldType === 'FILE'" action="#" :auto-upload="false" :disabled="readonly(field)"><el-button :disabled="readonly(field)">选择附件</el-button></el-upload>
      <el-input v-else v-model="value[field.fieldKey]" :disabled="readonly(field)" />
    </el-form-item>
  </el-form>
</template>
<script setup>
const value = defineModel({ type: Object, required: true })
defineProps({ fields: { type: Array, default: () => [] } })
const readonly = (field) => field.permission && field.permission !== 'EDIT'
const options = (field) => { try { return JSON.parse(field.optionsJson || '[]') } catch { return [] } }
</script>
