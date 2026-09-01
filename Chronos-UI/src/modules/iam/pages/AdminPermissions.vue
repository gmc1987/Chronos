<template>
  <div class="admin-page permission-page">
    <div class="header">
      <div>
        <div class="title">权限管理</div>
        <div class="subtitle">角色与菜单资源绑定</div>
      </div>
      <el-button type="primary" @click="save">保存</el-button>
    </div>

    <div class="content">
      <div class="role-panel">
        <div class="panel-title">角色列表</div>
        <div class="role-list">
          <button
            v-for="role in roles"
            :key="role.id"
            class="role-item"
            :class="{ active: role.id === currentRoleId }"
            @click="selectRole(role)"
          >
            {{ role.roleName }}
          </button>
        </div>
      </div>
      <div class="tree-panel">
        <div class="panel-title">菜单权限</div>
        <el-tree
          ref="treeRef"
          class="permission-tree"
          :data="treeData"
          show-checkbox
          node-key="key"
          default-expand-all
          :props="{ label: 'label', children: 'children' }"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { listRoles, roleDetail, menuTree, permissions, updateRole } from '../api'

const roles = ref([])
const currentRoleId = ref('')
const treeData = ref([])
const treeRef = ref(null)

const loadRoles = async () => {
  const res = await listRoles({ page: 0, size: 200 })
  roles.value = res?.data?.content || []
  if (!currentRoleId.value && roles.value.length) {
    selectRole(roles.value[0])
  }
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

  treeData.value = mapMenu(menus?.data || [])
}

const selectRole = async (row) => {
  currentRoleId.value = row.id
  await buildTree()
  const detail = await roleDetail(row.id)
  const menuIds = detail?.data?.menuIds || []
  const menuPermissions = detail?.data?.menuPermissions || []

  const checked = []
  menuIds.forEach((id) => checked.push(`menu:${id}`))
  menuPermissions.forEach((mp) => {
    mp.permissionIds?.forEach((pid) => checked.push(`perm:${mp.menuId}:${pid}`))
  })

  setTimeout(() => {
    treeRef.value?.setCheckedKeys(checked)
  }, 0)
}

const save = async () => {
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

  await updateRole({ id: currentRoleId.value, menuIds, menuPermissions })
}

loadRoles()
</script>
