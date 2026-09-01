<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">字典维护</div>
        <div class="subtitle">维护字典项（树形结构）</div>
      </div>
      <el-button type="primary" @click="openCreate">新增字典</el-button>
    </div>

    <el-tree
      class="dict-tree"
      :data="dicts"
      node-key="id"
      :props="{ label: 'dictName', children: 'children' }"
      default-expand-all
    >
      <template #default="{ data }">
        <div class="tree-row">
          <span>{{ data.dictName }} ({{ data.dictCode || data.dictValue }})</span>
          <div class="tree-actions">
            <el-button size="small" @click="openCreate(data)">新增</el-button>
            <el-button size="small" @click="openEdit(data)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(data)">删除</el-button>
          </div>
        </div>
      </template>
    </el-tree>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增字典' : '编辑字典'">
      <el-form label-width="90px">
        <el-form-item label="字典编码">
          <el-input v-model="form.dictCode" />
        </el-form-item>
        <el-form-item label="字典名称">
          <el-input v-model="form.dictName" />
        </el-form-item>
        <el-form-item label="父级">
          <el-input v-model="form.parentId" />
        </el-form-item>
        <el-form-item label="字典值">
          <el-input v-model="form.dictValue" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { dictTree, createDict, updateDict, deleteDict } from '../api'

const dicts = ref([])
const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ dictCode: '', dictName: '', parentId: '', dictValue: '', status: 1 })

const load = async () => {
  const res = await dictTree()
  dicts.value = res?.data || []
}

const openCreate = (parent = null) => {
  dialogMode.value = 'create'
  form.value = {
    dictCode: '',
    dictName: '',
    parentId: parent?.id || '',
    dictValue: '',
    status: 1,
  }
  showDialog.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = { ...row }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createDict(form.value)
  } else {
    await updateDict(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteDict(row.id)
  load()
}

load()
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.tree-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.tree-actions {
  display: flex;
  gap: 6px;
}
.dict-tree {
  background: #0f1322;
  border-radius: 12px;
  padding: 12px;
}
</style>
