<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">用户管理</div>
        <div class="subtitle">维护后台用户账号</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="用户名/邮箱" class="search-input" @keyup.enter="load" />
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <el-table :data="users" border style="width: 100%">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增用户' : '编辑用户'">
      <el-form label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" v-if="dialogMode === 'create'">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
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
import { listUsers, createUser, updateUser, deleteUser, listRoles } from '../api'

const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const roles = ref([])

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ username: '', password: '', email: '', status: 1, roleIds: [] })

const loadRoles = async () => {
  const res = await listRoles({ page: 0, size: 200 })
  roles.value = res?.data?.content || []
}

const load = async () => {
  const res = await listUsers({
    page: page.value - 1,
    size: size.value,
    username: keyword.value || undefined,
    email: keyword.value || undefined,
  })
  users.value = res?.data?.content || []
  total.value = res?.data?.totalElements || 0
}

const onPageChange = (val) => {
  page.value = val
  load()
}
const onSizeChange = (val) => {
  size.value = val
  page.value = 1
  load()
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = { username: '', password: '', email: '', status: 1, roleIds: [] }
  showDialog.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = { ...row, password: '' }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createUser(form.value)
  } else {
    await updateUser(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteUser(row.id)
  load()
}

loadRoles()
load()
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.actions {
  display: flex;
  gap: 10px;
}
.search {
  width: 240px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
