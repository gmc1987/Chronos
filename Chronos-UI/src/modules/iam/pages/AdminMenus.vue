<template>
  <div class="admin-page">
    <div class="header">
      <div><div class="title">菜单管理</div><div class="subtitle">按层级维护后台菜单，展开节点可查看和新增子菜单</div></div>
      <el-button v-permission="['iam:menu:create','iam:menu:manage']" type="primary" @click="openCreate()">新增根菜单</el-button>
    </div>
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索菜单名称或路径" />
      <el-switch v-model="expandAll" active-text="展开全部" />
      <span class="count">共 {{ menuCount }} 个菜单</span>
    </div>
    <el-table
      :key="`${expandAll ? 'expanded' : 'collapsed'}-${keyword}`"
      :data="pagedTree"
      row-key="id"
      border
      :default-expand-all="expandAll || Boolean(keyword.trim())"
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="240">
        <template #default="scope"><span class="menu-name">{{ scope.row.menuName }}</span><el-tag v-if="!scope.row.parentId" size="small" type="info">一级菜单</el-tag></template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" min-width="280"><template #default="scope"><code>{{ scope.row.path || '-' }}</code></template></el-table-column>
      <el-table-column prop="orderNum" label="同级排序" width="110" align="center" />
      <el-table-column label="子菜单" width="90" align="center"><template #default="scope">{{ scope.row.children?.length || 0 }}</template></el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="scope">
          <el-button v-permission="['iam:menu:create','iam:menu:manage']" link type="primary" @click="openCreate(scope.row)">新增子菜单</el-button>
          <el-button v-permission="['iam:menu:update','iam:menu:manage']" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button v-permission="['iam:menu:delete','iam:menu:manage']" link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && visibleTree.length === 0" description="没有匹配的菜单" />
    <div v-if="visibleTree.length" class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="visibleTree.length"
        :page-sizes="[5, 10, 20, 50]"
      />
      <span class="pager-tip">按一级菜单分页，子菜单始终跟随父菜单显示</span>
    </div>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增菜单' : '编辑菜单'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="菜单名称" prop="menuName"><el-input v-model="form.menuName" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="路由路径" prop="path"><el-input v-model="form.path" placeholder="例如 /admin/education/courses" /></el-form-item>
        <el-form-item label="上级菜单">
          <el-tree-select v-model="form.parentId" :data="parentOptions" node-key="id" check-strictly clearable default-expand-all :render-after-expand="false" :props="{ label: 'menuName', children: 'children', disabled: 'disabled' }" placeholder="留空表示一级菜单" style="width: 100%" />
        </el-form-item>
        <el-form-item label="同级排序" prop="orderNum"><el-input-number v-model="form.orderNum" :min="0" :max="9999" /><span class="form-tip">数值越小越靠前</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createMenu, deleteMenu, menuTree, updateMenu } from '../api'

const tree = ref([])
const keyword = ref('')
const expandAll = ref(false)
const loading = ref(false)
const submitting = ref(false)
const page = ref(1)
const pageSize = ref(10)
const showDialog = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const form = ref({ id: null, menuName: '', path: '', parentId: null, orderNum: 0 })
const rules = { menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }], path: [{ max: 500, message: '路由路径不能超过 500 个字符', trigger: 'blur' }] }
const flatten = (nodes = []) => nodes.flatMap(node => [node, ...flatten(node.children || [])])
const menuCount = computed(() => flatten(tree.value).length)
const filterTree = (nodes, value) => nodes.flatMap((node) => {
  const children = filterTree(node.children || [], value)
  const matched = `${node.menuName || ''} ${node.path || ''}`.toLowerCase().includes(value)
  return matched || children.length ? [{ ...node, children }] : []
})
const visibleTree = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  return value ? filterTree(tree.value, value) : tree.value
})
const pagedTree = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return visibleTree.value.slice(start, start + pageSize.value)
})
const markDisabled = (nodes, ids) => nodes.map(node => ({
  ...node,
  disabled: ids.has(node.id),
  children: markDisabled(node.children || [], ids),
}))
const parentOptions = computed(() => {
  if (!form.value.id) return tree.value
  const current = flatten(tree.value).find(item => item.id === form.value.id)
  const descendants = flatten(current?.children || []).map(item => item.id)
  return markDisabled(tree.value, new Set([form.value.id, ...descendants]))
})
const load = async () => {
  loading.value = true
  try {
    tree.value = (await menuTree())?.data || []
  } finally {
    loading.value = false
  }
}
const openCreate = (parent = null) => {
  dialogMode.value = 'create'
  form.value = {
    id: null,
    menuName: '',
    path: '',
    parentId: parent?.id || null,
    orderNum: parent?.children?.length
      ? Math.max(...parent.children.map(item => item.orderNum || 0)) + 10
      : 0,
  }
  showDialog.value = true
}
const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = {
    id: row.id,
    menuName: row.menuName,
    path: row.path || '',
    parentId: row.parentId || null,
    orderNum: row.orderNum || 0,
  }
  showDialog.value = true
}
const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = { ...form.value, parentId: form.value.parentId || null }
    await (dialogMode.value === 'create' ? createMenu(payload) : updateMenu(payload))
    showDialog.value = false
    ElMessage.success('菜单已保存')
    await load()
  } finally {
    submitting.value = false
  }
}
const remove = async (row) => {
  if (row.children?.length) {
    ElMessage.warning('该菜单下仍有子菜单，请先迁移或删除子菜单')
    return
  }
  await ElMessageBox.confirm(`确认删除菜单“${row.menuName}”？`, '删除菜单', {
    type: 'warning',
  })
  await deleteMenu(row.id)
  ElMessage.success('菜单已删除')
  await load()
}
watch([keyword, pageSize], () => {
  page.value = 1
})
watch(visibleTree, () => {
  const lastPage = Math.max(1, Math.ceil(visibleTree.value.length / pageSize.value))
  if (page.value > lastPage) page.value = lastPage
})
load()
</script>

<style scoped>
.header, .toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.header { margin-bottom: 16px; } .toolbar { justify-content: flex-start; margin-bottom: 12px; } .toolbar .el-input { width: 320px; }
.count { margin-left: auto; color: #84909a; font-size: 13px; } .menu-name { margin-right: 8px; font-weight: 500; }
code { color: #52606d; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; } .form-tip { margin-left: 12px; color: #84909a; font-size: 13px; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 16px; margin-top: 16px; }
.pager-tip { color: #84909a; font-size: 12px; }
</style>
