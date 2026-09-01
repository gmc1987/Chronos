<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">客户管理</div>
        <div class="subtitle">维护 ToC / ToB 客户</div>
      </div>
      <div class="actions">
        <el-select v-model="customerType" placeholder="客户类型" @change="load" style="width: 140px">
          <el-option label="全部" value="" />
          <el-option label="ToC" value="0" />
          <el-option label="ToB" value="1" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增客户</el-button>
      </div>
    </div>

    <el-table :data="customers" border style="width: 100%">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="电话" />
      <el-table-column prop="customerType" label="类型" width="120">
        <template #default="scope">
          {{ scope.row.customerType === '1' ? 'ToB' : 'ToC' }}
        </template>
      </el-table-column>
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增客户' : '编辑客户'">
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
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.customerType">
            <el-option label="ToC" value="0" />
            <el-option label="ToB" value="1" />
          </el-select>
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
import { customerList, createCustomer, updateCustomer, deleteCustomer } from '../api'

const customers = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const customerType = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ username: '', password: '', email: '', phone: '', status: 1, customerType: '0' })

const load = async () => {
  const res = await customerList({ page: page.value - 1, size: size.value, customerType: customerType.value || undefined })
  customers.value = res?.data?.content || []
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
  form.value = { username: '', password: '', email: '', phone: '', status: 1, customerType: '0' }
  showDialog.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = { ...row }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createCustomer(form.value)
  } else {
    await updateCustomer(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteCustomer(row.id)
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
.actions {
  display: flex;
  gap: 12px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
