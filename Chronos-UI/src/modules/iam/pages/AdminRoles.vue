<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">角色管理</div>
        <div class="subtitle">配置角色与菜单/按钮权限</div>
      </div>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table :data="roles" border style="width: 100%">
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="roleCode" label="角色编码" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <!-- <el-button size="small" @click="openAuth(scope.row)">授权</el-button> -->
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增角色' : '编辑角色'">
      <el-form label-width="90px">
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" :disabled="dialogMode === 'edit' && form.builtIn" placeholder="例如 DOCTOR" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAuth" title="角色授权" width="720px">
      <div class="auth-panel">
        <div class="tree-panel">
          <div class="panel-title">菜单 & 权限</div>
          <el-tree
            ref="treeRef"
            :data="menuTreeData"
            show-checkbox
            node-key="key"
            default-expand-all
            :props="{ label: 'label', children: 'children' }"
            @check="onTreeCheck"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showAuth = false">取消</el-button>
        <el-button type="primary" @click="saveAuth">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { listRoles, createRole, updateRole, deleteRole, roleDetail, menuTree, permissions } from '../api'

const roles = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({ id: '', roleName: '', roleCode: '', description: '' })

const showAuth = ref(false)
const currentRole = ref(null)
const menuTreeData = ref([])
const treeRef = ref(null)

const load = async () => {
  const res = await listRoles({ page: page.value - 1, size: size.value })
  roles.value = res?.data?.content || []
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
  form.value = { id: '', roleName: '', roleCode: '', description: '' }
  showDialog.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = { ...row }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createRole(form.value)
  } else {
    await updateRole(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteRole(row.id)
  load()
}

const buildTree = async () => {
  const menus = await menuTree()
  const perms = await permissions({ page: 0, size: 500 })
  const permList = perms?.data?.content || []
  const mapMenu = (nodes = []) =>
    nodes.map((m) => {
      const childrenMenus = m.children?.length ? mapMenu(m.children) : []
      const menuPerms = childrenMenus.length
        ? []
        : permList.map((p) => ({
            key: `perm:${m.id}:${p.id}`,
            id: p.id,
            type: 'permission',
            label: `${p.permissionName} (${p.permissionCode})`,
          }))

      return {
        key: `menu:${m.id}`,
        id: m.id,
        type: 'menu',
        label: m.menuName || m.name,
        children: [...childrenMenus, ...menuPerms],
      }
    })

  menuTreeData.value = mapMenu(menus?.data || [])
}

const openAuth = async (row) => {
  currentRole.value = row
  await buildTree()
  const detail = await roleDetail(row.id)
  const menuIds = detail?.data?.menuIds || []
  const menuPermissions = detail?.data?.menuPermissions || []

  const checkedKeys = []
  menuIds.forEach((id) => checkedKeys.push(`menu:${id}`))
  menuPermissions.forEach((mp) => {
    mp.permissionIds?.forEach((pid) => checkedKeys.push(`perm:${mp.menuId}:${pid}`))
  })

  showAuth.value = true
  setTimeout(() => {
    treeRef.value?.setCheckedKeys(checkedKeys)
  }, 0)
}

const onTreeCheck = () => {}

const saveAuth = async () => {
  const checked = treeRef.value?.getCheckedKeys() || []
  const menuIds = []
  const permissionMap = {}
  checked.forEach((key) => {
    if (key.startsWith('menu:')) {
      menuIds.push(key.split(':')[1])
    } else if (key.startsWith('perm:')) {
      const parts = key.split(':')
      const menuId = parts[1]
      const permId = parts[2]
      if (!permissionMap[menuId]) permissionMap[menuId] = new Set()
      permissionMap[menuId].add(permId)
    }
  })

  const menuPermissions = Object.entries(permissionMap).map(([menuId, set]) => ({
    menuId,
    permissionIds: Array.from(set),
  }))

  await updateRole({ id: currentRole.value.id, menuIds, permissionIds: [], menuPermissions })
  showAuth.value = false
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
.auth-panel {
  display: flex;
}
.tree-panel {
  flex: 1;
}
.panel-title {
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
