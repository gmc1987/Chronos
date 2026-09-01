<template>
  <div class="admin-page directory-page">
    <div class="header"><div><div class="title">组织架构</div><div class="subtitle">统一维护部门、岗位、职级、员工以及员工任职关系</div></div></div>
    <el-tabs v-model="tab" class="directory-tabs">
      <el-tab-pane label="部门架构" name="departments">
        <div class="toolbar"><el-select v-model="organizationId" placeholder="选择医院/院区" @change="loadDepartments"><el-option v-for="o in organizations" :key="o.id" :label="o.organizationName" :value="o.id" /></el-select><el-button @click="downloadTemplate('departments')">下载模板</el-button><el-upload :show-file-list="false" accept=".xlsx" :http-request="o=>uploadImport('departments',o.file)"><el-button>批量导入</el-button></el-upload><el-button type="primary" :disabled="!organizationId" @click="editDepartment()">新增部门</el-button></div>
        <el-table :data="departmentRows" row-key="id" default-expand-all border :tree-props="{children:'children'}">
          <el-table-column prop="departmentName" label="部门名称" min-width="120"/>
          <el-table-column prop="departmentCode" label="部门编码"/>
          <el-table-column prop="departmentType" label="类型"/>
          <el-table-column prop="leaderEmployeeName" label="负责人"/>
          <el-table-column prop="level" label="层级" width="80"/>
          <el-table-column label="状态" width="90">
            <template #default="s"><el-tag :type="s.row.status===1?'success':'info'">{{ s.row.status===1?'启用':'停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="310">
            <template #default="s">
              <el-button @click="editDepartment(s.row)">编辑</el-button>
              <el-button @click="editDepartment(null,s.row.id)">新增下级</el-button>
              <el-button type="danger" @click="removeDepartment(s.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="岗位管理" name="positions">
        <div class="toolbar"><el-button @click="downloadTemplate('positions')">下载模板</el-button><el-upload :show-file-list="false" accept=".xlsx" :http-request="o=>uploadImport('positions',o.file)"><el-button>批量导入</el-button></el-upload><el-button type="primary" @click="editPosition()">新增岗位</el-button></div>
        <el-table :data="positionRows" border>
          <el-table-column prop="positionCode" label="岗位编码"/>
          <el-table-column prop="positionName" label="岗位名称"/>
          <el-table-column prop="positionCategory" label="岗位类别"/>
          <el-table-column label="管理岗位" width="100">
            <template #default="s">{{ s.row.management?'是':'否' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="s">
              <el-tag :type="s.row.status===1?'success':'info'">{{ s.row.status===1?'启用':'停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250">
            <template #default="s">
              <el-button @click="editPosition(s.row)">编辑</el-button>
              <el-button type="danger" @click="removePosition(s.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="职级管理" name="levels">
        <div class="toolbar"><el-button @click="downloadTemplate('job-levels')">下载模板</el-button><el-upload :show-file-list="false" accept=".xlsx" :http-request="o=>uploadImport('job-levels',o.file)"><el-button>批量导入</el-button></el-upload><el-button type="primary" @click="editLevel()">新增职级</el-button></div>
        <el-table :data="levelRows" border>
          <el-table-column prop="levelCode" label="职级编码"/>
          <el-table-column prop="levelName" label="职级名称"/>
          <el-table-column prop="levelCategory" label="职级序列"/>
          <el-table-column prop="levelSequence" label="等级值"/>
          <el-table-column label="状态" width="90">
            <template #default="s">
              <el-tag :type="s.row.status===1?'success':'info'">{{ s.row.status===1?'启用':'停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250">
            <template #default="s">
              <el-button @click="editLevel(s.row)">编辑</el-button>
              <el-button type="danger" @click="removeLevel(s.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="员工与任职" name="employees">
        <div class="toolbar"><el-input v-model="employeeKeyword" clearable placeholder="搜索工号、姓名或手机号"/><el-button @click="downloadTemplate('employees')">下载模板</el-button><el-upload :show-file-list="false" accept=".xlsx" :http-request="o=>uploadImport('employees',o.file)"><el-button>批量导入</el-button></el-upload><el-button type="primary" @click="editEmployee()">新增员工</el-button></div>
        <el-table :data="filteredEmployees" border>
          <el-table-column prop="employeeCode" label="工号"/>
          <el-table-column prop="employeeName" label="姓名"/>
          <el-table-column prop="gender" label="性别" width="80"/>
          <el-table-column prop="phone" label="手机"/>
          <el-table-column prop="employeeType" label="员工类型"/>
          <el-table-column label="状态" width="90">
            <template #default="s">
              <el-tag :type="s.row.employmentStatus==='ACTIVE'?'success':'info'">{{ statusName(s.row.employmentStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="430">
            <template #default="s">
              <el-button @click="editEmployee(s.row)">编辑</el-button>
              <el-button type="primary" plain @click="manageAssignments(s.row)">任职管理</el-button>
              <el-button type="success" plain @click="manageAccount(s.row)">账号管理</el-button>
              <el-button type="danger" @click="removeEmployee(s.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="departmentDialog" :title="departmentForm.id?'编辑部门':'新增部门'" width="620px"><el-form label-width="100px"><el-form-item label="所属机构"><el-select v-model="departmentForm.organizationId" disabled><el-option v-for="o in organizations" :key="o.id" :label="o.organizationName" :value="o.id"/></el-select></el-form-item><el-form-item label="上级部门"><el-tree-select v-model="departmentForm.parentId" clearable :data="departmentOptions" check-strictly :render-after-expand="false"/></el-form-item><el-form-item label="部门编码"><el-input v-model="departmentForm.departmentCode"/></el-form-item><el-form-item label="部门名称"><el-input v-model="departmentForm.departmentName"/></el-form-item><el-form-item label="部门类型"><el-select v-model="departmentForm.departmentType"><el-option label="行政部门" value="ADMINISTRATIVE"/><el-option label="临床科室" value="CLINICAL"/><el-option label="医技科室" value="MEDICAL_TECHNOLOGY"/><el-option label="护理单元" value="NURSING"/><el-option label="其他部门" value="DEPARTMENT"/></el-select></el-form-item><el-form-item label="部门负责人"><el-select v-model="departmentForm.leaderEmployeeId" clearable filterable><el-option v-for="e in employeeRows" :key="e.id" :label="`${e.employeeName}（${e.employeeCode}）`" :value="e.id"/></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="departmentForm.sortOrder" :min="0"/></el-form-item><el-form-item label="状态"><el-switch v-model="departmentEnabled" active-text="启用" inactive-text="停用"/></el-form-item><el-form-item label="说明"><el-input v-model="departmentForm.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="departmentDialog=false">取消</el-button><el-button type="primary" @click="submitDepartment">保存</el-button></template></el-dialog>
    <el-dialog v-model="positionDialog" :title="positionForm.id?'编辑岗位':'新增岗位'" width="560px"><el-form label-width="90px"><el-form-item label="岗位编码"><el-input v-model="positionForm.positionCode"/></el-form-item><el-form-item label="岗位名称"><el-input v-model="positionForm.positionName"/></el-form-item><el-form-item label="岗位类别"><el-select v-model="positionForm.positionCategory"><el-option label="医疗" value="MEDICAL"/><el-option label="护理" value="NURSING"/><el-option label="医技" value="TECHNICAL"/><el-option label="行政" value="ADMINISTRATIVE"/><el-option label="后勤" value="LOGISTICS"/></el-select></el-form-item><el-form-item label="管理岗位"><el-switch v-model="positionForm.management"/></el-form-item><el-form-item label="排序"><el-input-number v-model="positionForm.sortOrder" :min="0"/></el-form-item><el-form-item label="状态"><el-switch v-model="positionEnabled"/></el-form-item><el-form-item label="说明"><el-input v-model="positionForm.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="positionDialog=false">取消</el-button><el-button type="primary" @click="submitPosition">保存</el-button></template></el-dialog>
    <el-dialog v-model="levelDialog" :title="levelForm.id?'编辑职级':'新增职级'" width="560px"><el-form label-width="90px"><el-form-item label="职级编码"><el-input v-model="levelForm.levelCode"/></el-form-item><el-form-item label="职级名称"><el-input v-model="levelForm.levelName"/></el-form-item><el-form-item label="职级序列"><el-select v-model="levelForm.levelCategory"><el-option label="管理序列" value="MANAGEMENT"/><el-option label="专业技术" value="PROFESSIONAL"/><el-option label="医护序列" value="CLINICAL"/><el-option label="行政后勤" value="ADMINISTRATIVE"/></el-select></el-form-item><el-form-item label="等级值"><el-input-number v-model="levelForm.levelSequence" :min="0"/></el-form-item><el-form-item label="排序"><el-input-number v-model="levelForm.sortOrder" :min="0"/></el-form-item><el-form-item label="状态"><el-switch v-model="levelEnabled"/></el-form-item><el-form-item label="说明"><el-input v-model="levelForm.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="levelDialog=false">取消</el-button><el-button type="primary" @click="submitLevel">保存</el-button></template></el-dialog>
    <el-dialog v-model="employeeDialog" :title="employeeForm.id?'编辑员工':'新增员工'" width="620px"><el-form label-width="90px"><div class="form-grid"><el-form-item label="工号"><el-input v-model="employeeForm.employeeCode"/></el-form-item><el-form-item label="姓名"><el-input v-model="employeeForm.employeeName"/></el-form-item><el-form-item label="性别"><el-select v-model="employeeForm.gender"><el-option label="男" value="MALE"/><el-option label="女" value="FEMALE"/><el-option label="未说明" value="UNSPECIFIED"/></el-select></el-form-item><el-form-item label="员工类型"><el-select v-model="employeeForm.employeeType"><el-option label="正式员工" value="STAFF"/><el-option label="合同员工" value="CONTRACT"/><el-option label="进修人员" value="VISITING"/><el-option label="实习人员" value="INTERN"/></el-select></el-form-item><el-form-item label="手机"><el-input v-model="employeeForm.phone"/></el-form-item><el-form-item label="邮箱"><el-input v-model="employeeForm.email"/></el-form-item><el-form-item label="入职日期"><el-date-picker v-model="employeeForm.hireDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="在职状态"><el-select v-model="employeeForm.employmentStatus"><el-option label="在职" value="ACTIVE"/><el-option label="停职" value="SUSPENDED"/><el-option label="离职" value="LEFT"/></el-select></el-form-item></div></el-form><template #footer><el-button @click="employeeDialog=false">取消</el-button><el-button type="primary" @click="submitEmployee">保存</el-button></template></el-dialog>
    <el-dialog v-model="accountDialog" :title="`${activeAccountEmployee.employeeName||''} · 账号管理`" width="620px">
      <el-alert v-if="activeAccountEmployee.employmentStatus!=='ACTIVE'" title="停职或离职员工不能启用门户账号" type="warning" :closable="false" class="account-alert"/>
      <el-form label-width="110px">
        <el-form-item label="员工工号"><el-input :model-value="activeAccountEmployee.employeeCode" disabled/></el-form-item>
        <el-form-item label="登录账号"><el-input v-model="accountForm.username" :disabled="!!accountForm.id"/></el-form-item>
        <el-form-item v-if="!accountForm.id" label="临时密码"><el-input v-model="accountForm.password" type="password" show-password placeholder="至少10位，包含大小写字母和数字"/></el-form-item>
        <el-form-item label="门户角色"><el-select v-model="accountForm.roleIds" multiple filterable style="width:100%"><el-option v-for="role in roleRows" :key="role.id" :label="role.roleName" :value="role.id"/></el-select></el-form-item>
        <el-form-item v-if="accountForm.id" label="账号状态"><el-switch v-model="accountEnabled" :disabled="activeAccountEmployee.employmentStatus!=='ACTIVE'" active-text="启用" inactive-text="停用"/></el-form-item>
        <el-form-item v-if="accountForm.id" label="安全状态"><span>{{accountForm.accountLocked?'已锁定':'正常'}} · {{accountForm.mustChangePassword?'下次登录须修改密码':'密码已设置'}}</span></el-form-item>
        <el-form-item v-if="accountForm.id" label="重置密码"><el-input v-model="resetPasswordValue" type="password" show-password placeholder="输入新的临时密码"/></el-form-item>
      </el-form>
      <template #footer>
        <template v-if="accountForm.id"><el-button v-if="accountForm.accountLocked" @click="unlockAccount">解锁</el-button><el-button @click="forceLogoutAccount">强制下线</el-button><el-button :disabled="!resetPasswordValue" @click="resetPasswordAccount">重置密码</el-button></template>
        <el-button @click="accountDialog=false">取消</el-button><el-button type="primary" @click="saveAccount">{{accountForm.id?'保存账号':'开通账号'}}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="assignmentDialog" :title="`${activeEmployee.employeeName||''} · 任职管理`" width="900px"><div class="assignment-head"><span>一个员工可以在多个院区或部门任职，但只能有一个主任职。</span><el-button type="primary" @click="editAssignment()">新增任职</el-button></div><el-table :data="assignmentRows" border><el-table-column prop="organizationName" label="机构"/><el-table-column prop="departmentName" label="部门"/><el-table-column prop="positionName" label="岗位"/><el-table-column prop="jobLevelName" label="职级"/><el-table-column label="主任职" width="80"><template #default="s">{{s.row.primaryAssignment?'是':'否'}}</template></el-table-column><el-table-column label="操作" width="140"><template #default="s"><el-button @click="editAssignment(s.row)">编辑</el-button><el-button type="danger" @click="removeAssignment(s.row.id)">删除</el-button></template></el-table-column></el-table></el-dialog>
    <el-dialog v-model="assignmentEditDialog" :title="assignmentForm.id?'编辑任职':'新增任职'" width="620px" append-to-body><el-form label-width="90px"><el-form-item label="所属机构"><el-select v-model="assignmentForm.organizationId" @change="loadAssignmentDepartments"><el-option v-for="o in organizations" :key="o.id" :label="o.organizationName" :value="o.id"/></el-select></el-form-item><el-form-item label="任职部门"><el-tree-select v-model="assignmentForm.departmentId" :data="assignmentDepartmentOptions" check-strictly/></el-form-item><el-form-item label="岗位"><el-select v-model="assignmentForm.positionId"><el-option v-for="p in positionRows" :key="p.id" :label="p.positionName" :value="p.id"/></el-select></el-form-item><el-form-item label="职级"><el-select v-model="assignmentForm.jobLevelId" clearable><el-option v-for="l in levelRows" :key="l.id" :label="l.levelName" :value="l.id"/></el-select></el-form-item><el-form-item label="主任职"><el-switch v-model="assignmentForm.primaryAssignment"/></el-form-item><el-form-item label="部门负责人"><el-switch v-model="assignmentForm.departmentLeader"/></el-form-item><el-form-item label="生效日期"><el-date-picker v-model="assignmentForm.effectiveFrom" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="失效日期"><el-date-picker v-model="assignmentForm.effectiveTo" value-format="YYYY-MM-DD"/></el-form-item></el-form><template #footer><el-button @click="assignmentEditDialog=false">取消</el-button><el-button type="primary" @click="submitAssignment">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orgList, organizationUnits, saveOrganizationUnit, deleteOrganizationUnit, positions, savePosition, deletePosition, jobLevels, saveJobLevel, deleteJobLevel, employees, saveEmployee, deleteEmployee, employeeAssignments, saveEmployeeAssignment, deleteEmployeeAssignment, userByEmployee, createUser, updateUser, listRoles, unlockUser, forceLogoutUser, resetUserPassword, downloadDirectoryImportTemplate, importDirectoryData } from '../api'
const tab=ref('departments'),organizations=ref([]),organizationId=ref(''),departmentRows=ref([]),positionRows=ref([]),levelRows=ref([]),employeeRows=ref([]),assignmentRows=ref([]),employeeKeyword=ref('')
const departmentDialog=ref(false),positionDialog=ref(false),levelDialog=ref(false),employeeDialog=ref(false),assignmentDialog=ref(false),assignmentEditDialog=ref(false)
const accountDialog=ref(false),activeAccountEmployee=ref({}),accountForm=ref({}),accountEnabled=ref(true),roleRows=ref([]),resetPasswordValue=ref('')
const departmentForm=ref({}),positionForm=ref({}),levelForm=ref({}),employeeForm=ref({}),assignmentForm=ref({}),activeEmployee=ref({})
const departmentEnabled=ref(true),positionEnabled=ref(true),levelEnabled=ref(true),assignmentDepartmentRows=ref([])
const treeOptions=(rows,exclude)=>rows.filter(x=>x.id!==exclude).map(x=>({value:x.id,label:x.departmentName,children:treeOptions(x.children||[],exclude)}))
const departmentOptions=computed(()=>treeOptions(departmentRows.value,departmentForm.value.id)),assignmentDepartmentOptions=computed(()=>treeOptions(assignmentDepartmentRows.value))
const filteredEmployees=computed(()=>{const k=employeeKeyword.value.trim().toLowerCase();return !k?employeeRows.value:employeeRows.value.filter(e=>[e.employeeCode,e.employeeName,e.phone].some(v=>String(v||'').toLowerCase().includes(k)))})
const loadAll=async()=>{const [o,p,l,e]=await Promise.all([orgList({page:0,size:500}),positions(),jobLevels(),employees()]);organizations.value=o?.data?.content||[];positionRows.value=p?.data||[];levelRows.value=l?.data||[];employeeRows.value=e?.data||[];if(!organizationId.value&&organizations.value.length)organizationId.value=organizations.value[0].id;if(organizationId.value)await loadDepartments()}
const loadDepartments=async()=>{departmentRows.value=organizationId.value?(await organizationUnits(organizationId.value))?.data||[]:[]}
const editDepartment=(row=null,parentId='')=>{departmentForm.value=row?{...row}:{organizationId:organizationId.value,parentId,departmentType:'DEPARTMENT',sortOrder:0,status:1};departmentEnabled.value=departmentForm.value.status!==0;departmentDialog.value=true}
const submitDepartment=async()=>{departmentForm.value.status=departmentEnabled.value?1:0;await saveOrganizationUnit(departmentForm.value);departmentDialog.value=false;await loadDepartments();ElMessage.success('部门已保存')}
const removeDepartment=async id=>{await confirmDelete();await deleteOrganizationUnit(id);await loadDepartments()}
const editPosition=(row={})=>{positionForm.value={management:false,status:1,sortOrder:0,...row};positionEnabled.value=positionForm.value.status!==0;positionDialog.value=true}
const submitPosition=async()=>{positionForm.value.status=positionEnabled.value?1:0;await savePosition(positionForm.value);positionDialog.value=false;await loadAll();ElMessage.success('岗位已保存')}
const removePosition=async id=>{await confirmDelete();await deletePosition(id);await loadAll()}
const editLevel=(row={})=>{levelForm.value={levelSequence:0,status:1,sortOrder:0,...row};levelEnabled.value=levelForm.value.status!==0;levelDialog.value=true}
const submitLevel=async()=>{levelForm.value.status=levelEnabled.value?1:0;await saveJobLevel(levelForm.value);levelDialog.value=false;await loadAll();ElMessage.success('职级已保存')}
const removeLevel=async id=>{await confirmDelete();await deleteJobLevel(id);await loadAll()}
const editEmployee=(row={})=>{employeeForm.value={employeeType:'STAFF',employmentStatus:'ACTIVE',gender:'UNSPECIFIED',...row};employeeDialog.value=true}
const submitEmployee=async()=>{await saveEmployee(employeeForm.value);employeeDialog.value=false;await loadAll();ElMessage.success('员工已保存')}
const removeEmployee=async id=>{await confirmDelete();await deleteEmployee(id);await loadAll()}
const manageAccount=async employee=>{activeAccountEmployee.value=employee;const [account,roles]=await Promise.all([userByEmployee(employee.id),listRoles({page:0,size:500})]);roleRows.value=roles?.data?.content||[];const current=account?.data;accountForm.value=current?{...current,roleIds:(current.roles||[]).map(r=>r.id)}:{username:employee.employeeCode,password:'',employeeId:employee.id,displayName:employee.employeeName,phone:employee.phone,email:employee.email,accountType:'STAFF',status:1,mustChangePassword:true,roleIds:[]};accountEnabled.value=current?current.status!==0:true;resetPasswordValue.value='';accountDialog.value=true}
const saveAccount=async()=>{if(accountForm.value.id){await updateUser({id:accountForm.value.id,status:accountEnabled.value?1:0,roleIds:accountForm.value.roleIds})}else{await createUser(accountForm.value)}accountDialog.value=false;ElMessage.success(accountForm.value.id?'账号已保存':'账号已开通，首次登录须修改密码')}
const unlockAccount=async()=>{await unlockUser(accountForm.value.id);accountForm.value.accountLocked=false;ElMessage.success('账号已解锁')}
const forceLogoutAccount=async()=>{await forceLogoutUser(accountForm.value.id);ElMessage.success('账号已强制下线')}
const resetPasswordAccount=async()=>{await resetUserPassword(accountForm.value.id,resetPasswordValue.value);resetPasswordValue.value='';accountForm.value.mustChangePassword=true;ElMessage.success('密码已重置，用户下次登录须修改密码')}
const manageAssignments=async row=>{activeEmployee.value=row;assignmentRows.value=(await employeeAssignments(row.id))?.data||[];assignmentDialog.value=true}
const loadAssignmentDepartments=async()=>{assignmentDepartmentRows.value=assignmentForm.value.organizationId?(await organizationUnits(assignmentForm.value.organizationId))?.data||[]:[];assignmentForm.value.departmentId=''}
const editAssignment=async(row={})=>{assignmentForm.value={employeeId:activeEmployee.value.id,primaryAssignment:false,departmentLeader:false,status:1,...row};if(assignmentForm.value.organizationId)assignmentDepartmentRows.value=(await organizationUnits(assignmentForm.value.organizationId))?.data||[];assignmentEditDialog.value=true}
const submitAssignment=async()=>{await saveEmployeeAssignment(assignmentForm.value);assignmentEditDialog.value=false;await manageAssignments(activeEmployee.value);ElMessage.success('任职关系已保存')}
const removeAssignment=async id=>{await confirmDelete();await deleteEmployeeAssignment(id);await manageAssignments(activeEmployee.value)}
const confirmDelete=()=>ElMessageBox.confirm('删除后不可恢复，确认继续？','操作确认',{type:'warning'})
const importNames={departments:'组织架构',positions:'岗位', 'job-levels':'职级',employees:'员工与任职'}
const downloadTemplate=async type=>{const blob=await downloadDirectoryImportTemplate(type);const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`Chronos-${importNames[type]}批量导入模板.xlsx`;a.click();URL.revokeObjectURL(url)}
const downloadImportErrors=(type,errors=[])=>{const rows=errors.map((e,i)=>`<tr><td>${i+1}</td><td>${String(e).replaceAll('&','&amp;').replaceAll('<','&lt;')}</td></tr>`).join('');const blob=new Blob([`<html><meta charset="UTF-8"><table><tr><th>序号</th><th>错误原因</th></tr>${rows}</table></html>`],{type:'application/vnd.ms-excel'});const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`Chronos-${importNames[type]}导入错误报告.xls`;a.click();URL.revokeObjectURL(url)}
const uploadImport=async(type,file)=>{try{const preview=await importDirectoryData(type,file,true);if(preview?.data?.valid===false){downloadImportErrors(type,preview.data.errors);ElMessage.error('校验失败，已下载错误报告');return}await ElMessageBox.confirm(`校验通过，将新增或更新 ${preview?.data?.successCount||0} 条数据。确认正式导入？`,'导入确认',{type:'warning'});const result=await importDirectoryData(type,file,false);ElMessage.success(`${importNames[type]}导入成功，共处理 ${result?.data?.successCount||0} 条`);await loadAll()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'批量导入已取消');throw e}}
const statusName=v=>({ACTIVE:'在职',SUSPENDED:'停职',LEFT:'离职'}[v]||v)
loadAll()
</script>
<style scoped>.directory-tabs{padding:20px 22px;background:#fff;border:1px solid #e1e7eb;border-radius:20px}.toolbar{display:flex;justify-content:flex-end;gap:10px;margin-bottom:16px}.toolbar .el-select{width:280px}.toolbar .el-input{width:300px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.assignment-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;color:#84919b;font-size:12px}.account-alert{margin-bottom:18px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.toolbar{flex-wrap:wrap}.toolbar .el-select,.toolbar .el-input{width:100%}}</style>
