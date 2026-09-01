<template>
  <div class="admin-page">
    <div class="header"><div><div class="title">统一门户配置</div><div class="subtitle">管理门户应用、Widget 与模块接入标识</div></div></div>
    <el-tabs v-model="tab">
      <el-tab-pane label="应用中心" name="apps">
        <div class="toolbar"><el-button type="primary" @click="openApp()">新增应用</el-button></div>
        <el-table :data="apps" border>
          <el-table-column prop="appName" label="应用名称" /><el-table-column prop="appCode" label="编码" />
          <el-table-column prop="routePath" label="访问地址" /><el-table-column prop="requiredPermission" label="权限码" />
          <el-table-column prop="enabled" label="启用" width="80"><template #default="s">{{ s.row.enabled ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="操作" width="160"><template #default="s"><el-button size="small" @click="openApp(s.row)">编辑</el-button><el-button size="small" type="danger" @click="removeApp(s.row)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="Widget" name="widgets">
        <div class="toolbar"><el-button type="primary" @click="openWidget()">新增 Widget</el-button></div>
        <el-table :data="widgets" border>
          <el-table-column prop="widgetName" label="名称" /><el-table-column prop="widgetCode" label="编码" />
          <el-table-column prop="providerCode" label="Provider" /><el-table-column prop="componentName" label="前端组件" />
          <el-table-column prop="defaultSize" label="默认尺寸" width="100" /><el-table-column label="操作" width="160"><template #default="s"><el-button size="small" @click="openWidget(s.row)">编辑</el-button><el-button size="small" type="danger" @click="removeWidget(s.row)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="appVisible" :title="appForm.id ? '编辑应用' : '新增应用'">
      <el-form label-width="110px"><el-form-item label="应用名称"><el-input v-model="appForm.appName" /></el-form-item><el-form-item label="应用编码"><el-input v-model="appForm.appCode" :disabled="!!appForm.id" /></el-form-item><el-form-item label="说明"><el-input v-model="appForm.description" /></el-form-item><el-form-item label="访问地址"><el-input v-model="appForm.routePath" /></el-form-item><el-form-item label="图标"><el-input v-model="appForm.icon" /></el-form-item><el-form-item label="权限码"><el-input v-model="appForm.requiredPermission" /></el-form-item><el-form-item label="角色编码范围"><el-input v-model="appForm.audienceRoleCodes" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="机构ID范围"><el-input v-model="appForm.audienceOrganizationIds" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="部门ID范围"><el-input v-model="appForm.audienceDepartmentIds" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="打开方式"><el-select v-model="appForm.openMode"><el-option label="门户内部" value="INTERNAL"/><el-option label="新窗口" value="EXTERNAL"/></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="appForm.sortOrder" /></el-form-item><el-form-item label="状态"><el-switch v-model="appForm.enabled" active-text="启用" /></el-form-item><el-form-item label="推荐"><el-switch v-model="appForm.recommended" /></el-form-item></el-form>
      <template #footer><el-button @click="appVisible=false">取消</el-button><el-button type="primary" @click="saveApp">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="widgetVisible" :title="widgetForm.id ? '编辑 Widget' : '新增 Widget'">
      <el-form label-width="110px"><el-form-item label="名称"><el-input v-model="widgetForm.widgetName" /></el-form-item><el-form-item label="编码"><el-input v-model="widgetForm.widgetCode" :disabled="!!widgetForm.id" /></el-form-item><el-form-item label="Provider"><el-input v-model="widgetForm.providerCode" /></el-form-item><el-form-item label="前端组件"><el-input v-model="widgetForm.componentName" /></el-form-item><el-form-item label="权限码"><el-input v-model="widgetForm.requiredPermission" /></el-form-item><el-form-item label="角色编码范围"><el-input v-model="widgetForm.audienceRoleCodes" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="机构ID范围"><el-input v-model="widgetForm.audienceOrganizationIds" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="部门ID范围"><el-input v-model="widgetForm.audienceDepartmentIds" placeholder="逗号分隔，留空表示不限"/></el-form-item><el-form-item label="默认尺寸"><el-select v-model="widgetForm.defaultSize"><el-option v-for="s in ['SMALL','MEDIUM','LARGE','FULL']" :key="s" :label="s" :value="s"/></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="widgetForm.sortOrder" /></el-form-item><el-form-item label="状态"><el-switch v-model="widgetForm.enabled" active-text="启用" /></el-form-item></el-form>
      <template #footer><el-button @click="widgetVisible=false">取消</el-button><el-button type="primary" @click="saveWidget">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { createPortalApplication, createPortalWidget, deletePortalApplication, deletePortalWidget, portalAdminApplications, portalAdminWidgets, updatePortalApplication, updatePortalWidget } from '../../../api/admin'
const tab=ref('apps'),apps=ref([]),widgets=ref([]),appVisible=ref(false),widgetVisible=ref(false)
const blankApp=()=>({appName:'',appCode:'',description:'',routePath:'/portal/apps',icon:'',requiredPermission:'',audienceRoleCodes:'',audienceOrganizationIds:'',audienceDepartmentIds:'',openMode:'INTERNAL',sortOrder:0,enabled:true,recommended:false})
const blankWidget=()=>({widgetName:'',widgetCode:'',providerCode:'',componentName:'',requiredPermission:'',audienceRoleCodes:'',audienceOrganizationIds:'',audienceDepartmentIds:'',defaultSize:'MEDIUM',sortOrder:0,enabled:true})
const appForm=ref(blankApp()),widgetForm=ref(blankWidget())
const load=async()=>{ apps.value=(await portalAdminApplications()).data||[];widgets.value=(await portalAdminWidgets()).data||[] }
const openApp=(row)=>{appForm.value=row?{...row}:blankApp();appVisible.value=true}
const openWidget=(row)=>{widgetForm.value=row?{...row}:blankWidget();widgetVisible.value=true}
const saveApp=async()=>{appForm.value.id?await updatePortalApplication(appForm.value.id,appForm.value):await createPortalApplication(appForm.value);appVisible.value=false;await load()}
const saveWidget=async()=>{widgetForm.value.id?await updatePortalWidget(widgetForm.value.id,widgetForm.value):await createPortalWidget(widgetForm.value);widgetVisible.value=false;await load()}
const removeApp=async(row)=>{await deletePortalApplication(row.id);await load()}
const removeWidget=async(row)=>{await deletePortalWidget(row.id);await load()}
onMounted(load)
</script>

<style scoped>.header{margin-bottom:16px}.title{font-size:22px;font-weight:700}.subtitle{margin-top:5px;color:#8d98aa}.toolbar{display:flex;justify-content:flex-end;margin:8px 0 12px}</style>
