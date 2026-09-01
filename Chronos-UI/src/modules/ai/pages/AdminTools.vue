<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">Tool 管理</div>
        <div class="subtitle">维护工具配置与调用方式</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="Tool 名称" class="search-input" @keyup.enter="load" />
        <!-- type filter removed -->
        <el-select v-model="status" placeholder="状态" style="width: 120px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增 Tool</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="toolCode" label="编码" width="180" />
      <el-table-column prop="toolName" label="名称" width="180" />
      <el-table-column prop="implClass" label="实现类" />
      <el-table-column prop="schemaJson" label="Schema" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <span class="status-tag">{{ scope.row.status === 1 ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增 Tool' : '编辑 Tool'" class="dark-dialog">
      <el-form label-width="110px">
        <el-form-item label="编码">
          <el-input v-model="form.toolCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.toolName" />
        </el-form-item>
        <el-form-item label="实现类">
          <el-input v-model="form.implClass" placeholder="com.xxx.ToolImpl" />
        </el-form-item>
        <el-form-item label="Schema JSON">
          <el-input type="textarea" :rows="4" v-model="form.schemaJson" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input type="textarea" :rows="4" v-model="form.configJson" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
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
import { listTools, createTool, updateTool, deleteTool } from '../api'

const items = ref([])
const keyword = ref('')
const status = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ toolCode: '', toolName: '', implClass: '', schemaJson: '', configJson: '', description: '', status: 1 })

const load = async () => {
  const res = await listTools({ searchKey: keyword.value, status: status.value })
  items.value = res?.data || []
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = {
    toolCode: '',
    toolName: '',
    implClass: '',
    schemaJson: JSON.stringify({
      type: 'object',
      properties: {
        prompt: { type: 'string', description: '用户输入' },
        temperature: { type: 'number', default: 0.7 },
        maxTokens: { type: 'number', default: 1024 }
      },
      required: ['prompt']
    }, null, 2),
    configJson: JSON.stringify({
      endpoint: 'https://api.xxx.com/v1/chat/completions',
      model: 'gpt-4o-mini',
      apiKey: 'YOUR_API_KEY'
    }, null, 2),
    description: '',
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
    await createTool(form.value)
  } else {
    await updateTool(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteTool(row.id || row.toolCode)
  load()
}

load()
</script>
