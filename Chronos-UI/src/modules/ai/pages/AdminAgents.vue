<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">Agent 管理</div>
        <div class="subtitle">维护 AgentSpec（JSON/MD）</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="Agent 编码/名称" class="search-input" @keyup.enter="load" />
        <el-select v-model="status" placeholder="状态" style="width: 120px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="refreshRuntime">刷新运行时</el-button>
        <el-button type="primary" @click="openCreate">新增 Agent</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="agentCode" label="编码" width="160" />
      <el-table-column prop="agentName" label="名称" />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <span class="status-tag">{{ scope.row.status === 1 ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastUpdateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增 Agent' : '编辑 Agent'" width="760px" class="dark-dialog">
      <el-form label-width="110px">
        <el-form-item label="Agent 编码">
          <el-input v-model="form.agentCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="Agent 名称">
          <el-input v-model="form.agentName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="Agent 实现类">
          <el-input v-model="form.agentImplClass" placeholder="com.xxx.ScriptAgent" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" />
        </el-form-item>
        <el-form-item label="Spec JSON">
          <el-input type="textarea" :rows="6" v-model="form.specJson" />
        </el-form-item>
        <el-form-item label="Spec MD">
          <el-input type="textarea" :rows="6" v-model="form.specMd" />
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
import { listAgentSpecs, createAgentSpec, updateAgentSpec, deleteAgentSpec, refreshAgentSpecs } from '../api'

const items = ref([])
const keyword = ref('')
const status = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ agentCode: '', agentName: '', description: '', agentImplClass: '', status: 1, version: 1, specJson: '', specMd: '' })

const load = async () => {
  const res = await listAgentSpecs({ searchKey: keyword.value, status: status.value })
  items.value = res?.data || []
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = {
    agentCode: '',
    agentName: '',
    description: '',
    agentImplClass: '',
    status: 1,
    version: 1,
    specJson: JSON.stringify({
      agentId: 'script_agent',
      name: 'Script Agent',
      agentImplClass: 'com.xxx.ScriptAgent',
      skills: ['xxx'],
      tools: ['xxx'],
      inputSchema: {},
      outputSchema: {},
    }, null, 2),
    specMd: '',
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
    await createAgentSpec(form.value)
  } else {
    await updateAgentSpec(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteAgentSpec(row.id)
  load()
}

const refreshRuntime = async () => {
  await refreshAgentSpecs()
}

load()
</script>
