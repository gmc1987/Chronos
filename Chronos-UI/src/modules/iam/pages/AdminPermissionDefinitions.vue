<template>
  <div class="admin-page">
    <div class="header"><div><div class="title">权限定义</div><div class="subtitle">统一维护菜单操作、流程能力和数据范围策略</div></div><el-button v-permission="['iam:permission:create','iam:permission:manage']" type="primary" @click="openCreate">新增定义</el-button></div>
    <el-tabs v-model="activeType" @tab-change="onTypeChange">
      <el-tab-pane label="菜单操作权限" name="MENU_ACTION" />
      <el-tab-pane label="流程能力权限" name="WORKFLOW" />
      <el-tab-pane label="数据权限策略" name="DATA" />
    </el-tabs>
    <div class="query-bar">
      <el-input v-model="keyword" clearable placeholder="权限名称或编码" @keyup.enter="search" />
      <el-select v-model="statusFilter" clearable placeholder="全部状态">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="permissionName" label="权限名称" min-width="150" />
      <el-table-column prop="permissionCode" label="权限编码" min-width="220" />
      <el-table-column v-if="activeType === 'MENU_ACTION'" label="所属菜单" min-width="140"><template #default="scope">{{ menuName(scope.row.menuId) }}</template></el-table-column>
      <el-table-column v-if="activeType === 'MENU_ACTION'" prop="actionType" label="操作类型" width="110" />
      <el-table-column v-if="activeType === 'WORKFLOW'" prop="resourceType" label="流程资源" width="130" />
      <el-table-column v-if="activeType === 'DATA'" prop="scopeType" label="范围类型" width="190" />
      <el-table-column prop="description" label="描述" min-width="180" />
      <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="250"><template #default="scope"><el-button v-permission="['iam:permission:update','iam:permission:manage']" size="small" @click="openEdit(scope.row)">编辑</el-button><el-button v-permission="['iam:permission:disable','iam:permission:manage']" size="small" @click="toggleStatus(scope.row)">{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button><el-button v-if="!scope.row.builtIn" v-permission="['iam:permission:delete','iam:permission:manage']" size="small" type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑权限定义' : '新增权限定义'" width="680px">
      <el-form label-width="110px">
        <el-form-item label="权限类型"><el-select v-model="form.permissionType" :disabled="!!form.id" style="width:100%"><el-option label="菜单操作权限" value="MENU_ACTION"/><el-option label="流程能力权限" value="WORKFLOW"/><el-option label="数据权限策略" value="DATA"/></el-select></el-form-item>
        <el-form-item label="权限名称"><el-input v-model="form.permissionName" /></el-form-item>
        <el-form-item label="权限编码"><el-input v-model="form.permissionCode" :disabled="!!form.id" placeholder="例如 iam:user:create" /></el-form-item>
        <template v-if="form.permissionType === 'MENU_ACTION'">
          <el-form-item label="所属菜单"><el-select v-model="form.menuId" filterable style="width:100%"><el-option v-for="menu in menuRows" :key="menu.id" :label="menu.menuName" :value="menu.id" /></el-select></el-form-item>
          <el-form-item label="操作类型"><el-select v-model="form.actionType" style="width:100%"><el-option v-for="item in actionTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="API路径"><el-input v-model="form.resourcePattern" placeholder="可选，例如 /api/admin/users/**" /></el-form-item>
          <el-form-item label="HTTP方法"><el-select v-model="form.httpMethod" clearable style="width:100%"><el-option v-for="item in httpMethods" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        </template>
        <template v-if="form.permissionType === 'WORKFLOW'">
          <el-form-item label="流程资源"><el-select v-model="form.resourceType" style="width:100%"><el-option v-for="item in workflowResources" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="操作类型"><el-input v-model="form.actionType" placeholder="例如 VIEW、PUBLISH、APPROVE" /></el-form-item>
        </template>
        <template v-if="form.permissionType === 'DATA'">
          <el-form-item label="范围类型"><el-select v-model="form.scopeType" style="width:100%"><el-option v-for="item in scopeTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="数据资源"><el-input v-model="form.resourceType" placeholder="留空表示通用策略" /></el-form-item>
          <el-form-item label="扩展配置"><el-input v-model="form.configJson" type="textarea" :rows="4" placeholder="可选 JSON 配置" /></el-form-item>
        </template>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="enabled" active-text="启用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissions, createPermission, updatePermission, deletePermission, menuTree } from '../api'
const activeType=ref('MENU_ACTION'),rows=ref([]),menuRows=ref([]),dialogVisible=ref(false)
const page=ref(1),size=ref(10),total=ref(0),keyword=ref(''),statusFilter=ref()
const blank=()=>({id:'',permissionName:'',permissionCode:'',permissionType:activeType.value,menuId:'',actionType:'',resourceType:'',scopeType:'',resourcePattern:'',httpMethod:'',configJson:'',description:'',status:1})
const form=ref(blank()),enabled=computed({get:()=>form.value.status===1,set:value=>form.value.status=value?1:0})
const actionTypes=['VIEW','CREATE','UPDATE','DELETE','UPLOAD','DOWNLOAD','IMPORT','EXPORT','MANAGE'],httpMethods=['GET','POST','PUT','PATCH','DELETE']
const workflowResources=['WORKFLOW','DEFINITION','FORM','INSTANCE','TASK','MONITOR','DIRECTORY']
const scopeTypes=['ALL','ORGANIZATION','DEPARTMENT','DEPARTMENT_AND_CHILDREN','SELF','CUSTOM_ORGANIZATION','CUSTOM_DEPARTMENT','CUSTOM_EMPLOYEE']
const flatten=(nodes=[])=>nodes.flatMap(item=>[item,...flatten(item.children||[])])
const load=async()=>{
  const res=await permissions({page:page.value-1,size:size.value,permissionType:activeType.value,keyword:keyword.value||undefined,status:statusFilter.value})
  rows.value=res?.data?.content||[]
  total.value=res?.data?.totalElements||0
}
const search=()=>{page.value=1;load()}
const resetSearch=()=>{keyword.value='';statusFilter.value=undefined;page.value=1;load()}
const onTypeChange=()=>{page.value=1;load()}
const onPageChange=value=>{page.value=value;load()}
const onSizeChange=value=>{size.value=value;page.value=1;load()}
const loadMenus=async()=>{menuRows.value=flatten((await menuTree())?.data||[])}
const menuName=id=>menuRows.value.find(item=>item.id===id)?.menuName||'-'
const openCreate=()=>{form.value=blank();dialogVisible.value=true}
const openEdit=row=>{form.value={...blank(),...row};dialogVisible.value=true}
const save=async()=>{if(form.value.id)await updatePermission(form.value);else await createPermission(form.value);dialogVisible.value=false;ElMessage.success('权限定义已保存');load()}
const toggleStatus=async row=>{await updatePermission({...row,status:row.status===1?0:1});ElMessage.success('状态已更新');load()}
const remove=async row=>{
  await ElMessageBox.confirm(`确定删除权限“${row.permissionName}”吗？删除后无法恢复。`, '删除权限定义', {type:'warning'})
  await deletePermission(row.id)
  ElMessage.success('权限定义已删除')
  if(rows.value.length===1&&page.value>1)page.value-=1
  load()
}
loadMenus();load()
</script>

<style scoped>
.header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.query-bar{display:flex;gap:10px;margin-bottom:12px}
.query-bar .el-input{width:300px}
.query-bar .el-select{width:140px}
.pager{display:flex;justify-content:flex-end;margin-top:16px}
</style>
