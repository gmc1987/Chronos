<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">菜单管理</div>
        <div class="subtitle">维护后台菜单结构</div>
      </div>
      <el-button type="primary" @click="openCreate">新增菜单</el-button>
    </div>

    <el-table :data="menus" border style="width: 100%">
      <el-table-column prop="menuName" label="菜单名称" />
      <el-table-column prop="path" label="路径" />
      <el-table-column prop="parentName" label="父级菜单" />
      <el-table-column prop="orderNum" label="排序" width="100" />
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增菜单' : '编辑菜单'">
      <el-form label-width="90px">
        <el-form-item label="菜单名称">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item label="父级菜单">
          <el-select v-model="form.parentId" placeholder="请选择" style="width: 100%">
            <el-option label="根节点" value="" />
            <el-option v-for="item in tree" :key="item.id" :label="item.menuName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.orderNum" :min="0" />
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
import { listMenus, menuTree, createMenu, updateMenu, deleteMenu } from '../api'

const menus = ref([])
const tree = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ id: '', menuName: '', path: '', parentId: '', orderNum: 0 })

const loadTree = async () => {
  const res = await menuTree()
  tree.value = res?.data || []
}

const load = async () => {
  const [listRes, treeRes] = await Promise.all([
    listMenus({ page: page.value - 1, size: size.value }),
    menuTree(),
  ])
  const treeData = treeRes?.data || []
  const map = new Map()
  const walk = (nodes = []) => {
    nodes.forEach((n) => {
      map.set(n.id, n.menuName || n.name)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(treeData)
  menus.value = (listRes?.data?.content || []).map((item) => ({
    ...item,
    parentName: map.get(item.parentId) || '-',
  }))
  total.value = listRes?.data?.totalElements || 0
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

const openCreate = async () => {
  dialogMode.value = 'create'
  form.value = { id: null, menuName: '', path: '', parentId: '', orderNum: 0 }
  await loadTree()
  showDialog.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  form.value = { ...row }
  await loadTree()
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createMenu(form.value)
  } else {
    await updateMenu(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteMenu(row.id)
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
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
