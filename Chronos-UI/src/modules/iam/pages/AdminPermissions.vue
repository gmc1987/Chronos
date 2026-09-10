<template>
  <div class="admin-page permission-page">
    <div class="header"><div><div class="title">授权管理</div><div class="subtitle">按菜单操作、流程能力和数据范围分别为角色授权</div></div><el-button v-permission="['iam:role:authorize','iam:role:manage']" type="primary" :disabled="!currentRoleId" @click="saveCurrent">保存当前页签</el-button></div>
    <div class="content">
      <aside class="role-panel"><div class="panel-title">角色列表</div><div class="role-list"><button v-for="role in roles" :key="role.id" class="role-item" :class="{ active: role.id === currentRoleId }" @click="selectRole(role)">{{ role.roleName }}</button></div><el-pagination v-if="roleTotal" class="role-pager" small layout="prev, pager, next" :total="roleTotal" :current-page="rolePage" :page-size="roleSize" @current-change="onRolePageChange"/></aside>
      <section class="authorization-panel">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="菜单权限" name="menu">
            <div class="tab-description">配置角色可见菜单及菜单内操作。本页只展示菜单操作权限，不包含流程权限。</div>
            <el-tree ref="menuTreeRef" class="permission-tree" :data="menuTreeData" show-checkbox check-strictly node-key="key" default-expand-all :props="{ label: 'label', children: 'children' }" />
          </el-tab-pane>
          <el-tab-pane label="流程权限" name="workflow">
            <div class="tab-description">配置流程设计、发布、发起、审批和实例管理等平台能力。具体流程的访问范围仍由流程 ACL 控制。</div>
            <el-checkbox-group v-model="workflowPermissionIds" class="workflow-grid">
              <el-checkbox v-for="permission in workflowPermissions" :key="permission.id" :value="permission.id" border><span class="permission-name">{{ permission.permissionName }}</span><span class="permission-code">{{ permission.permissionCode }}</span></el-checkbox>
            </el-checkbox-group>
            <el-empty v-if="!workflowPermissions.length" description="暂无流程权限定义" />
          </el-tab-pane>
          <el-tab-pane label="数据权限" name="data">
            <div class="tab-description">限制角色能够查询和操作的业务数据范围，数据范围不会影响菜单是否可见。</div>
            <el-form label-width="130px" class="data-scope-form">
              <el-form-item label="数据范围"><el-select v-model="dataScopeType" style="width:360px"><el-option v-for="item in dataScopeTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
              <el-form-item v-if="dataScopeType === 'CUSTOM_ORGANIZATION'" label="指定机构"><el-select v-model="customOrganizationIds" multiple filterable style="width:100%" placeholder="请选择机构"><el-option v-for="item in organizations" :key="item.id" :label="item.organizationName || item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item v-if="dataScopeType === 'CUSTOM_DEPARTMENT'" label="指定部门"><el-select v-model="customDepartmentIds" multiple filterable style="width:100%" placeholder="请选择部门"><el-option v-for="item in departments" :key="item.id" :label="item.organizationUnitName || item.unitName || item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item v-if="dataScopeType === 'CUSTOM_EMPLOYEE'" label="指定员工"><el-select v-model="customEmployeeIds" multiple filterable style="width:100%" placeholder="请选择员工"><el-option v-for="item in employeeRows" :key="item.id" :label="item.employeeName || item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item v-if="dataScopeType === 'CUSTOM_EDUCATION_CLASS'" label="指定班级">
                <el-select v-model="customClassIds" multiple filterable style="width:100%" placeholder="请选择班级">
                  <el-option v-for="item in educationClasses" :key="item.id" :label="item.className" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="dataScopeType === 'CUSTOM_EDUCATION_GRADE'" label="指定年级">
                <el-select v-model="customGradeIds" multiple filterable style="width:100%" placeholder="请选择年级">
                  <el-option v-for="item in educationGrades" :key="item.id" :label="item.gradeName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoles, roleDetail, menuTree, permissions, updateRole, orgList, organizationUnits, employees } from '../api'
import { listAdministrativeClasses, listEducationGrades } from '../../../api/admin'

const roles = ref([]), roleTotal = ref(0), rolePage = ref(1), roleSize = ref(10), currentRoleId = ref(''), activeTab = ref('menu'), menuTreeRef = ref(null), menuTreeData = ref([])
const actionPermissionIds = ref(new Set()), workflowPermissions = ref([]), workflowPermissionIds = ref([])
const organizations = ref([]), departments = ref([]), employeeRows = ref([]), dataScopeType = ref('SELF')
const customOrganizationIds = ref([]), customDepartmentIds = ref([]), customEmployeeIds = ref([])
const customClassIds = ref([]), customGradeIds = ref([])
const educationClasses = ref([]), educationGrades = ref([])
const dataScopeTypes = ref([])

const mapMenus = (nodes, actions) => nodes.map((menu) => {
  const children = mapMenus(menu.children || [], actions)
  const operationNodes = actions.filter((permission) => permission.menuId === menu.id).map((permission) => ({ key: `perm:${menu.id}:${permission.id}`, id: permission.id, type: 'permission', label: `${permission.permissionName}（${permission.permissionCode}）` }))
  return { key: `menu:${menu.id}`, id: menu.id, type: 'menu', label: menu.menuName || menu.name, children: [...children, ...operationNodes] }
})

const loadCatalogs = async () => {
  const [menusRes, actionsRes, workflowsRes, dataRes, orgRes, employeeRes] = await Promise.all([
    menuTree(),
    permissions({ page: 0, size: 500, permissionType: 'MENU_ACTION' }),
    permissions({ page: 0, size: 500, permissionType: 'WORKFLOW' }),
    permissions({ page: 0, size: 500, permissionType: 'DATA' }),
    orgList({ page: 0, size: 500 }),
    employees()
  ])
  // 班级、年级属于教育行业扩展资源。医院模板没有对应接口时，
  // 不能因此阻断菜单权限、流程权限和通用数据权限的授权页面。
  const [classResult, gradeResult] = await Promise.allSettled([
    listAdministrativeClasses(),
    listEducationGrades()
  ])
  const actions = (actionsRes?.data?.content || []).filter((item) => item.status === 1)
  actionPermissionIds.value = new Set(actions.map((item) => item.id)); workflowPermissions.value = (workflowsRes?.data?.content || []).filter((item) => item.status === 1)
  dataScopeTypes.value = (dataRes?.data?.content || []).filter((item) => item.status === 1).map((item) => ({ label: item.permissionName, value: item.scopeType }))
  organizations.value = orgRes?.data?.content || orgRes?.data || []; employeeRows.value = employeeRes?.data || []
  educationClasses.value = classResult.status === 'fulfilled'
    ? classResult.value?.data?.content || classResult.value?.data || []
    : []
  educationGrades.value = gradeResult.status === 'fulfilled'
    ? gradeResult.value?.data?.content || gradeResult.value?.data || []
    : []
  menuTreeData.value = mapMenus(menusRes?.data || [], actions)
  const unitResponses = await Promise.all(organizations.value.map((item) => organizationUnits(item.id)))
  departments.value = unitResponses.flatMap((res) => res?.data || [])
}

const loadRoles = async () => { const res = await listRoles({ page: rolePage.value - 1, size: roleSize.value }); roles.value = res?.data?.content || []; roleTotal.value = res?.data?.totalElements || 0; if (!currentRoleId.value && roles.value.length) await selectRole(roles.value[0]) }
const onRolePageChange = async (value) => { rolePage.value = value; await loadRoles() }
const selectRole = async (role) => {
  currentRoleId.value = role.id
  const detail = (await roleDetail(role.id))?.data || {}
  workflowPermissionIds.value = [...(detail.workflowPermissionIds || [])]; applyDataScopes(detail.dataScopes || [])
  const checked = []
  ;(detail.menuIds || []).forEach((id) => checked.push(`menu:${id}`))
  ;(detail.menuPermissions || []).forEach((item) => { ;(item.permissionIds || []).filter((id) => actionPermissionIds.value.has(id)).forEach((id) => checked.push(`perm:${item.menuId}:${id}`)) })
  await nextTick(); menuTreeRef.value?.setCheckedKeys(checked)
}
const applyDataScopes = (scopes) => {
  customOrganizationIds.value = scopes.filter((item) => item.scopeType === 'CUSTOM_ORGANIZATION').map((item) => item.organizationId)
  customDepartmentIds.value = scopes.filter((item) => item.scopeType === 'CUSTOM_DEPARTMENT').map((item) => item.organizationUnitId)
  customEmployeeIds.value = scopes.filter((item) => item.scopeType === 'CUSTOM_EMPLOYEE').map((item) => item.employeeId)
  customClassIds.value = scopes.filter((item) => item.scopeType === 'CUSTOM_EDUCATION_CLASS').map((item) => item.resourceId)
  customGradeIds.value = scopes.filter((item) => item.scopeType === 'CUSTOM_EDUCATION_GRADE').map((item) => item.resourceId)
  dataScopeType.value = scopes[0]?.scopeType || 'SELF'
}

const saveCurrent = async () => {
  if (activeTab.value === 'menu') await saveMenuPermissions()
  if (activeTab.value === 'workflow') await updateRole({ id: currentRoleId.value, workflowPermissionIds: workflowPermissionIds.value })
  if (activeTab.value === 'data') {
    const dataScopes = buildDataScopes()
    if (!dataScopes.length) { ElMessage.warning('自定义数据范围至少需要选择一项'); return }
    await updateRole({ id: currentRoleId.value, dataScopes })
  }
  ElMessage.success('授权已保存，相关用户需要重新登录后生效')
}
const saveMenuPermissions = async () => {
  const checked = menuTreeRef.value?.getCheckedKeys() || [], halfChecked = menuTreeRef.value?.getHalfCheckedKeys() || []
  const menuIds = [...new Set([...checked, ...halfChecked].filter((key) => key.startsWith('menu:')).map((key) => key.slice(5)))]
  const permissionMap = {}
  checked.filter((key) => key.startsWith('perm:')).forEach((key) => { const [, menuId, permissionId] = key.split(':'); if (!permissionMap[menuId]) permissionMap[menuId] = []; permissionMap[menuId].push(permissionId) })
  const menuActionPermissionIds = Object.values(permissionMap).flat()
  await updateRole({ id: currentRoleId.value, menuIds, menuActionPermissionIds })
}
const buildDataScopes = () => {
  if (dataScopeType.value === 'CUSTOM_ORGANIZATION') return customOrganizationIds.value.map((organizationId) => ({ scopeType: dataScopeType.value, organizationId }))
  if (dataScopeType.value === 'CUSTOM_DEPARTMENT') return customDepartmentIds.value.map((organizationUnitId) => ({ scopeType: dataScopeType.value, organizationUnitId }))
  if (dataScopeType.value === 'CUSTOM_EMPLOYEE') return customEmployeeIds.value.map((employeeId) => ({ scopeType: dataScopeType.value, employeeId }))
  if (dataScopeType.value === 'CUSTOM_EDUCATION_CLASS') return customClassIds.value.map((resourceId) => ({ scopeType: dataScopeType.value, resourceType: 'EDUCATION_CLASS', resourceId }))
  if (dataScopeType.value === 'CUSTOM_EDUCATION_GRADE') return customGradeIds.value.map((resourceId) => ({ scopeType: dataScopeType.value, resourceType: 'EDUCATION_GRADE', resourceId }))
  return [{ scopeType: dataScopeType.value }]
}
const init = async () => { await loadCatalogs(); await loadRoles() }
init()
</script>

<style scoped>
.header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.content{display:grid;grid-template-columns:220px minmax(0,1fr);gap:18px;min-height:560px}.role-panel,.authorization-panel{border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-bg-color)}.role-panel{padding:14px}.authorization-panel{padding:8px 18px 18px}.panel-title{font-weight:600;margin-bottom:10px}.role-list{display:flex;flex-direction:column;gap:6px}.role-item{padding:9px 10px;border:0;border-radius:5px;background:transparent;text-align:left;cursor:pointer;color:var(--el-text-color-primary)}.role-item:hover,.role-item.active{background:var(--el-color-primary-light-9);color:var(--el-color-primary)}.tab-description{margin-bottom:16px;padding:10px 12px;border-radius:6px;background:var(--el-fill-color-light);color:var(--el-text-color-secondary);font-size:13px}.permission-tree{max-height:560px;overflow:auto}.workflow-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px}.workflow-grid :deep(.el-checkbox){width:100%;height:auto;min-height:52px;margin:0;padding:9px 12px}.workflow-grid :deep(.el-checkbox__label){display:flex;min-width:0;flex-direction:column}.permission-name{font-weight:500}.permission-code{margin-top:3px;color:var(--el-text-color-secondary);font-size:12px}.data-scope-form{max-width:820px;padding-top:4px}
</style>
