<template>
  <div class="admin-page">
    <div v-if="viewMode === 'list'">
      <div class="header"><div><div class="title">表单设计器</div><div class="subtitle">维护工作流主表单和节点附加表单</div></div><el-button type="primary" @click="openCreate">新增表单</el-button></div>
      <el-table :data="forms" border @row-click="openDesigner">
        <el-table-column prop="formKey" label="表单编码" width="180" />
        <el-table-column prop="formName" label="表单名称" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="280"><template #default="scope">
          <el-button size="small" @click.stop="openDesigner(scope.row)">{{ scope.row.status === 'DRAFT' ? '设计' : '查看' }}</el-button>
          <el-button v-if="scope.row.status === 'PUBLISHED'" size="small" @click.stop="newVersion(scope.row)">新版本</el-button>
          <el-button v-if="scope.row.status === 'DRAFT'" size="small" type="success" @click.stop="publishCurrent(scope.row)">发布</el-button>
          <el-button v-if="scope.row.status === 'DRAFT'" size="small" type="danger" @click.stop="removeForm(scope.row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </div>

    <div v-else>
      <div class="header"><div class="header-actions"><el-button @click="back">返回</el-button><div><div class="title">{{ currentForm.formName }}</div><div class="subtitle">{{ currentForm.formKey }} · {{ currentForm.version }} · {{ currentForm.status }}</div></div></div><div><el-button @click="showPreview = true">预览</el-button><el-button v-if="editable" type="primary" @click="openField()">添加字段</el-button></div></div>
      <div class="designer-grid">
        <el-card shadow="never"><template #header>字段列表</template>
          <el-empty v-if="!fields.length" description="暂无字段，请添加字段" />
          <div v-for="(field, index) in fields" :key="field.id" class="field-row" @click="openField(field)">
            <div><strong>{{ field.fieldLabel }}</strong><small>{{ field.fieldKey }} · {{ fieldTypeLabel(field.fieldType) }}</small></div>
            <div class="field-actions"><el-tag v-if="field.required" size="small" type="danger">必填</el-tag><el-button v-if="editable && index > 0" link @click.stop="moveField(index, -1)">上移</el-button><el-button v-if="editable && index < fields.length - 1" link @click.stop="moveField(index, 1)">下移</el-button><el-button v-if="editable" link type="danger" @click.stop="removeField(field)">删除</el-button></div>
          </div>
        </el-card>
        <el-card shadow="never"><template #header>表单信息</template>
          <el-form label-width="90px"><el-form-item label="表单名称"><el-input v-model="currentForm.formName" :disabled="!editable" /></el-form-item><el-form-item label="描述"><el-input v-model="currentForm.description" type="textarea" :disabled="!editable" /></el-form-item><el-form-item v-if="editable"><el-button type="primary" @click="saveFormInfo">保存信息</el-button></el-form-item></el-form>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="showFormDialog" title="新增表单" width="560px"><el-form label-width="90px"><el-form-item label="表单编码"><el-input v-model="formDraft.formKey" placeholder="例如 LEAVE_FORM" /></el-form-item><el-form-item label="表单名称"><el-input v-model="formDraft.formName" /></el-form-item><el-form-item label="版本"><el-input v-model="formDraft.version" /></el-form-item><el-form-item label="描述"><el-input v-model="formDraft.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="showFormDialog = false">取消</el-button><el-button type="primary" @click="createCurrentForm">创建并设计</el-button></template></el-dialog>

    <el-dialog v-model="showFieldDialog" :title="fieldDraft.id ? '编辑字段' : '添加字段'" width="620px"><el-form label-width="100px"><el-form-item label="字段Key"><el-input v-model="fieldDraft.fieldKey" :disabled="!!fieldDraft.id" placeholder="例如 applicantName" /></el-form-item><el-form-item label="字段名称"><el-input v-model="fieldDraft.fieldLabel" /></el-form-item><el-form-item label="字段类型"><el-select v-model="fieldDraft.fieldType" style="width: 100%"><el-option v-for="item in fieldTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="必填"><el-switch v-model="fieldDraft.required" /></el-form-item><el-form-item v-if="optionField" label="选项"><el-input v-model="optionText" type="textarea" placeholder="每行一个选项，格式：值|显示名称" /></el-form-item></el-form><template #footer><el-button @click="showFieldDialog = false">取消</el-button><el-button type="primary" @click="saveField">保存</el-button></template></el-dialog>

    <el-dialog v-model="showPreview" title="表单预览" width="680px"><el-form label-width="120px"><el-form-item v-for="field in fields" :key="field.id" :label="field.fieldLabel" :required="field.required"><el-input v-if="['TEXT','NUMBER','DATE','DATETIME'].includes(field.fieldType)" :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'" disabled :placeholder="fieldTypeLabel(field.fieldType)" /><el-input v-else-if="field.fieldType === 'TEXTAREA'" type="textarea" disabled /><el-select v-else-if="['SELECT','RADIO','CHECKBOX'].includes(field.fieldType)" disabled style="width: 100%"><el-option v-for="option in parseOptions(field.optionsJson)" :key="option.value" :label="option.label" :value="option.value" /></el-select><el-upload v-else-if="field.fieldType === 'FILE'" disabled action="#"><el-button disabled>选择文件</el-button></el-upload><el-switch v-else-if="field.fieldType === 'BOOLEAN'" disabled /><el-input v-else disabled /></el-form-item></el-form></el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listForms, createForm, updateForm, deleteForm, publishForm, createFormVersion, listFormFields, createFormField, updateFormField, deleteFormField } from '../../../api/admin'

const viewMode = ref('list'), forms = ref([]), fields = ref([]), currentForm = ref({})
const showFormDialog = ref(false), showFieldDialog = ref(false), showPreview = ref(false), optionText = ref('')
const formDraft = ref({ formKey: '', formName: '', version: 'v1', description: '' })
const fieldDraft = ref({ id: '', formId: '', fieldKey: '', fieldLabel: '', fieldType: 'TEXT', required: false, sortOrder: 0, optionsJson: '' })
const fieldTypes = [{ label: '单行文本', value: 'TEXT' }, { label: '多行文本', value: 'TEXTAREA' }, { label: '数字', value: 'NUMBER' }, { label: '日期', value: 'DATE' }, { label: '日期时间', value: 'DATETIME' }, { label: '下拉选择', value: 'SELECT' }, { label: '单选', value: 'RADIO' }, { label: '多选', value: 'CHECKBOX' }, { label: '开关', value: 'BOOLEAN' }, { label: '附件', value: 'FILE' }]
const editable = computed(() => currentForm.value.status === 'DRAFT')
const optionField = computed(() => ['SELECT', 'RADIO', 'CHECKBOX'].includes(fieldDraft.value.fieldType))
const load = async () => { const res = await listForms({ page: 0, size: 200 }); forms.value = res?.data?.content || [] }
const loadFields = async () => { const res = await listFormFields(currentForm.value.id); fields.value = res?.data || [] }
const openCreate = () => { formDraft.value = { formKey: '', formName: '', version: 'v1', description: '' }; showFormDialog.value = true }
const createCurrentForm = async () => { const res = await createForm(formDraft.value); showFormDialog.value = false; await load(); if (res?.data) await openDesigner(res.data) }
const openDesigner = async (row) => { currentForm.value = { ...row }; viewMode.value = 'designer'; await loadFields() }
const back = async () => { viewMode.value = 'list'; await load() }
const saveFormInfo = async () => { const res = await updateForm(currentForm.value); if (res?.data) currentForm.value = res.data; ElMessage.success('表单信息已保存') }
const openField = (field) => { if (!editable.value) return; fieldDraft.value = field ? { ...field } : { id: '', formId: currentForm.value.id, fieldKey: '', fieldLabel: '', fieldType: 'TEXT', required: false, sortOrder: fields.value.length, optionsJson: '' }; optionText.value = parseOptions(fieldDraft.value.optionsJson).map((item) => `${item.value}|${item.label}`).join('\n'); showFieldDialog.value = true }
const saveField = async () => { const options = optionField.value ? optionText.value.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => { const [value, label] = line.split('|'); return { value, label: label || value } }) : []; const payload = { ...fieldDraft.value, optionsJson: JSON.stringify(options) }; if (payload.id) await updateFormField(payload); else await createFormField(payload); showFieldDialog.value = false; await loadFields() }
const removeField = async (field) => { await ElMessageBox.confirm(`确认删除字段“${field.fieldLabel}”？`, '删除字段', { type: 'warning' }); await deleteFormField(field.id); await loadFields() }
const moveField = async (index, offset) => { const other = fields.value[index + offset], current = fields.value[index]; const sort = current.sortOrder; await updateFormField({ ...current, sortOrder: other.sortOrder }); await updateFormField({ ...other, sortOrder: sort }); await loadFields() }
const removeForm = async (form) => { await ElMessageBox.confirm(`确认删除草稿表单“${form.formName}”？`, '删除表单', { type: 'warning' }); await deleteForm(form.id); await load() }
const publishCurrent = async (form) => { await ElMessageBox.confirm('发布后当前版本不可修改，确认发布？', '发布表单', { type: 'warning' }); await publishForm(form.id); await load(); ElMessage.success('表单发布成功') }
const newVersion = async (form) => { const result = await ElMessageBox.prompt('请输入新版本号', '创建表单新版本', { inputValue: nextVersion(form.version), inputPattern: /^\S+$/, inputErrorMessage: '版本不能为空' }); const res = await createFormVersion(form.id, result.value); await load(); if (res?.data) await openDesigner(res.data) }
const nextVersion = (version) => { const match = String(version || '').match(/^(.*?)(\d+)$/); return match ? `${match[1]}${Number(match[2]) + 1}` : `${version}.1` }
const parseOptions = (json) => { try { return JSON.parse(json || '[]') } catch { return [] } }
const fieldTypeLabel = (type) => fieldTypes.find((item) => item.value === type)?.label || type
load()
</script>

<style scoped>
.header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.header-actions{display:flex;align-items:center;gap:12px}.designer-grid{display:grid;grid-template-columns:minmax(520px,2fr) minmax(300px,1fr);gap:16px}.field-row{display:flex;align-items:center;justify-content:space-between;padding:12px;border:1px solid #e5e7eb;border-radius:6px;margin-bottom:8px;cursor:pointer}.field-row:hover{border-color:#409eff}.field-row small{display:block;color:#94a3b8;margin-top:4px}.field-actions{display:flex;align-items:center;gap:4px}@media(max-width:900px){.designer-grid{grid-template-columns:1fr}}
</style>
