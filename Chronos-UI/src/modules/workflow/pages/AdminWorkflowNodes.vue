<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">节点模板管理</div>
        <div class="subtitle">维护工作流节点模板（供前端画布选择）</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="节点名称/Key" class="search-input" @keyup.enter="load" />
        <el-button type="primary" @click="openCreate">新增节点</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="nodeKey" label="节点Key" width="180" />
      <el-table-column prop="nodeName" label="节点名称" />
      <el-table-column prop="nodeType" label="类型" width="140" />
      <el-table-column prop="executor" label="执行器/Agent" width="160" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="tags" label="标签" width="140" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增节点' : '编辑节点'" width="760px" class="dark-dialog">
      <el-form label-width="120px">
        <el-form-item label="节点Key">
          <el-input v-model="form.nodeKey" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="form.nodeName" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-input v-model="form.nodeType" />
        </el-form-item>
        <el-form-item label="执行器/Agent">
          <el-input v-model="form.executor" placeholder="agentCode" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="标签，逗号分隔" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input v-model="form.sort" />
        </el-form-item>
        <el-form-item label="Input Schema">
          <el-input type="textarea" :rows="4" v-model="form.inputSchema" />
        </el-form-item>
        <el-form-item label="Output Schema">
          <el-input type="textarea" :rows="4" v-model="form.outputSchema" />
        </el-form-item>
        <el-form-item label="Properties JSON">
          <el-input type="textarea" :rows="4" v-model="form.propertiesJson" />
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
import {
  listWorkflowNodeTemplates,
  createWorkflowNodeTemplate,
  updateWorkflowNodeTemplate,
  deleteWorkflowNodeTemplate,
} from '../api'

const items = ref([])
const keyword = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({
  nodeKey: '',
  nodeName: '',
  nodeType: '',
  executor: '',
  status: '',
  tags: '',
  sort: '',
  inputSchema: '',
  outputSchema: '',
  propertiesJson: '',
})

const load = async () => {
  const res = await listWorkflowNodeTemplates({ searchKey: keyword.value })
  items.value = res?.data || []
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = {
    nodeKey: '',
    nodeName: '',
    nodeType: '',
    executor: '',
    status: '',
    tags: '',
    sort: '',
    inputSchema: '',
    outputSchema: '',
    propertiesJson: '',
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
    await createWorkflowNodeTemplate(form.value)
  } else {
    await updateWorkflowNodeTemplate(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteWorkflowNodeTemplate(row.id)
  load()
}

load()
</script>
