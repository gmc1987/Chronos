<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">Skill 管理</div>
        <div class="subtitle">维护技能定义与绑定工具</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="Skill 名称" class="search-input" @keyup.enter="load" />
        <el-select v-model="status" placeholder="状态" style="width: 120px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增 Skill</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="skillCode" label="编码" width="180" />
      <el-table-column prop="skillName" label="名称" width="180" />
      <el-table-column prop="implClass" label="实现类" />
      <el-table-column prop="description" label="描述" />
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增 Skill' : '编辑 Skill'" class="dark-dialog">
      <el-form label-width="110px">
        <el-form-item label="编码">
          <el-input v-model="form.skillCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.skillName" />
        </el-form-item>
        <el-form-item label="实现类">
          <el-input v-model="form.implClass" placeholder="com.xxx.SkillImpl" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input type="textarea" :rows="4" v-model="form.configJson" />
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
import { listSkills, createSkill, updateSkill, deleteSkill } from '../api'

const items = ref([])
const keyword = ref('')
const status = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ skillCode: '', skillName: '', implClass: '', description: '', configJson: '', status: 1 })

const load = async () => {
  const res = await listSkills({ searchKey: keyword.value, status: status.value })
  items.value = res?.data || []
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = {
    skillCode: '',
    skillName: '',
    implClass: '',
    description: '',
    configJson: JSON.stringify({
      tools: ['demo_echo_tool'],
      promptTemplate: '根据用户输入生成剧本大纲',
      temperature: 0.7,
      maxTokens: 1024,
    }, null, 2),
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
  const payload = { ...form.value }
  if (dialogMode.value === 'create') {
    await createSkill(payload)
  } else {
    await updateSkill(payload)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteSkill(row.id || row.skillCode)
  load()
}

load()
</script>
