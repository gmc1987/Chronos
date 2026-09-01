<template>
  <div class="admin-page">
    <div v-if="viewMode === 'list'">
      <div class="header">
        <div>
          <div class="title">工作流定义</div>
          <div class="subtitle">维护工作流定义列表</div>
        </div>
        <div class="header-actions">
          <el-button @click="$router.push('/admin/workflow/forms')">表单设计器</el-button>
          <span class="ai-switch-label">AI辅助</span>
          <el-switch v-model="aiSetting.enabled" @change="saveAiSetting" />
          <el-button type="primary" @click="openCreateFlow">新增工作流</el-button>
        </div>
      </div>

      <div class="monitor-cards">
        <el-card v-for="item in monitorCards" :key="item.label" shadow="never"><div class="monitor-value">{{ item.value }}</div><div class="monitor-label">{{ item.label }}</div></el-card>
      </div>

      <el-table :data="flows" border style="width: 100%" @row-click="openEditFlow">
        <el-table-column prop="flowCode" label="流程编码" width="170" />
        <el-table-column prop="flowName" label="名称" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="entryNodeKey" label="入口节点" width="160" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="操作" width="310">
          <template #default="scope">
            <el-button size="small" @click.stop="openEditFlow(scope.row)">编辑</el-button>
            <el-button v-if="scope.row.status === 'PUBLISHED'" size="small" @click.stop="newVersion(scope.row)">新版本</el-button>
            <el-button v-if="scope.row.status === 'PUBLISHED'" size="small" type="warning" @click.stop="disableCurrent(scope.row)">停用</el-button>
            <el-button v-if="scope.row.status !== 'PUBLISHED'" size="small" type="danger" @click.stop="removeFlow(scope.row)">删除</el-button>
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
    </div>

    <div v-else class="editor-view">
      <div class="editor-header">
        <div class="left-actions">
          <el-button @click="backToList">返回</el-button>
        </div>
        <div class="right-actions">
          <el-button @click="runCheck(false)">规则检查</el-button>
          <el-button v-if="aiSetting.enabled && flowForm.aiAssistEnabled" type="warning" @click="runCheck(true)">AI全面检查</el-button>
          <el-button type="success" :disabled="!currentFlow?.id" @click="publishCurrent">发布</el-button>
          <el-button type="success" @click="openFlowDialog">保存工作流</el-button>
        </div>
      </div>

      <div class="canvas-toolbar">
        <div class="toolbar-left">
          <el-button size="small" @click="addCanvasNode">添加节点</el-button>
          <el-button size="small" @click="enableConnect">连线</el-button>
          <el-button size="small" type="danger" :disabled="!selectedNodeId" @click="removeSelectedNode">删除选中节点</el-button>
          <el-button size="small" @click="resetView">复位</el-button>
        </div>
        <div class="toolbar-right">
          <el-input v-model="newNodeName" placeholder="节点名称" style="width: 160px" />
          <el-select v-model="newNodeCategory" placeholder="节点分类" style="width: 130px" @change="newNodeType = ''">
            <el-option v-for="group in addableNodeGroups" :key="group.value" :label="group.label" :value="group.value" />
          </el-select>
          <el-select v-model="newNodeType" :disabled="!newNodeCategory" placeholder="具体类型" style="width: 150px">
            <el-option v-for="item in addableNodeTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>

      <el-alert v-if="canvasError" :title="canvasError" type="error" :closable="false" class="canvas-error" />
      <div class="canvas-wrapper" v-loading="canvasLoading">
        <VueFlow
          :key="currentFlow?.id || 'new-flow'"
          v-model:nodes="flowNodes"
          v-model:edges="flowEdges"
          :fit-view-on-init="true"
          :min-zoom="0.1"
          :max-zoom="2.5"
          :pan-on-drag="true"
          :zoom-on-scroll="true"
          :zoom-on-pinch="true"
          @connect="onConnect"
          @node-click="onNodeClick"
          @node-double-click="onNodeDblClick"
          @edge-double-click="onEdgeDoubleClick"
        >
          <Background />
          <Controls />
        </VueFlow>
      </div>
    </div>

    <el-dialog v-model="showFlowDialog" title="保存工作流">
      <el-form label-width="100px">
        <el-form-item label="流程编码">
          <el-input v-model="flowForm.flowCode" :disabled="!!flowForm.id" placeholder="例如 LEAVE_APPROVAL" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="flowForm.flowName" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="flowForm.version" :disabled="!!flowForm.id" />
        </el-form-item>
        <el-form-item label="流程分类">
          <el-select v-model="flowForm.category" filterable placeholder="请选择流程分类" style="width: 100%">
            <el-option v-for="item in flowCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="入口节点">
          <el-select v-model="flowForm.entryNodeKey" placeholder="请选择开始节点" style="width: 100%">
            <el-option v-for="node in entryNodeOptions" :key="node.id" :label="node.data?.label || node.id" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主表单">
          <el-select v-model="flowForm.mainFormId" clearable filterable placeholder="请选择流程主表单" style="width: 100%">
            <el-option v-for="form in formOptions" :key="form.id" :label="`${form.formName} (${form.version})`" :value="form.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="flowForm.status" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in flowStatusOptions"
              :key="item.id"
              :label="item.dictName"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="AI辅助">
          <el-switch v-model="flowForm.aiAssistEnabled" :disabled="!aiSetting.enabled" />
          <span class="form-tip">全局开关关闭时，本流程强制使用人工设计</span>
        </el-form-item>
        <el-form-item label="流程管理员">
          <el-input v-model="flowForm.managerUser" placeholder="管理员账号" />
        </el-form-item>
        <el-form-item label="发起范围">
          <el-input type="textarea" v-model="flowForm.starterScopeJson" placeholder='JSON，例如 {"type":"ALL"}' />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="flowForm.tags" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input type="textarea" v-model="flowForm.description" />
        </el-form-item>
        <el-form-item label="配置JSON">
          <el-input type="textarea" v-model="flowForm.configJson" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFlowDialog = false">取消</el-button>
        <el-button type="primary" @click="submitFlow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEdgeDialog" title="分支条件" width="560px">
      <el-form label-width="100px">
        <el-form-item label="默认分支"><el-switch v-model="edgeForm.isDefault" /><span class="form-tip">默认分支不应再配置条件</span></el-form-item>
        <template v-if="!edgeForm.isDefault">
          <el-form-item label="表单字段"><el-select v-model="edgeForm.fieldKey" filterable style="width:100%" placeholder="请选择主表单字段"><el-option v-for="field in edgeConditionFields" :key="field.fieldKey" :label="`${field.fieldLabel}（${field.fieldKey}）`" :value="field.fieldKey" /></el-select></el-form-item>
          <el-form-item label="比较方式"><el-select v-model="edgeForm.operator" style="width:100%"><el-option label="等于" value="==" /><el-option label="不等于" value="!=" /><el-option label="大于" value=">" /><el-option label="大于等于" value=">=" /><el-option label="小于" value="<" /><el-option label="小于等于" value="<=" /></el-select></el-form-item>
          <el-form-item label="比较值"><el-input v-model="edgeForm.compareValue" placeholder="请输入用于判断的值" /></el-form-item>
          <el-form-item label="条件预览"><el-input :model-value="buildConditionExpr(edgeForm)" disabled /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="showEdgeDialog = false">取消</el-button><el-button type="primary" @click="saveEdgeEdit">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showReviewDialog" title="流程检查结果" width="820px">
      <el-alert v-if="!reviewFindings.length" title="检查通过，未发现问题" type="success" :closable="false" />
      <el-table v-else :data="reviewFindings" border>
        <el-table-column prop="source" label="来源" width="80" />
        <el-table-column prop="severity" label="等级" width="80" />
        <el-table-column prop="title" label="问题" width="220" />
        <el-table-column prop="description" label="说明" />
        <el-table-column prop="suggestion" label="建议" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="showNodeDialog" title="节点信息" width="860px">
      <el-tabs v-model="nodeDialogTab">
        <el-tab-pane label="基础配置" name="basic">
      <el-form label-width="110px">
        <el-form-item label="Key">
          <el-input v-model="nodeForm.nodeKey" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="nodeForm.nodeName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="nodeForm.nodeType" :disabled="lockedNodeType" style="width: 100%">
            <el-option-group v-for="group in nodeTypeGroups" :key="group.label" :label="group.label">
              <el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <template v-if="humanNode">
        <el-form-item label="处理人规则">
          <el-select v-model="nodeForm.properties.assigneeMode" style="width: 100%" @change="nodeForm.properties.assigneeValue = ''"><el-option label="指定用户" value="USER" /><el-option label="指定角色" value="ROLE" /><el-option label="发起人所在部门负责人" value="INITIATOR_MANAGER" /><el-option label="由表单字段指定" value="FORM_FIELD" /></el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.properties.assigneeMode === 'USER'" label="指定用户">
          <el-select v-model="nodeForm.properties.assigneeValue" filterable style="width: 100%" placeholder="请选择办理人"><el-option v-for="user in assigneeUsers" :key="user.username" :label="user.displayName ? `${user.displayName}（${user.username}）` : user.username" :value="user.username" /></el-select>
        </el-form-item>
        <el-form-item v-else-if="nodeForm.properties.assigneeMode === 'ROLE'" label="指定角色">
          <el-select v-model="nodeForm.properties.assigneeValue" filterable style="width: 100%" placeholder="请选择角色"><el-option v-for="role in assigneeRoles" :key="role.roleCode" :label="role.roleName ? `${role.roleName}（${role.roleCode}）` : role.roleCode" :value="role.roleCode" /></el-select>
        </el-form-item>
        <el-form-item v-else-if="nodeForm.properties.assigneeMode === 'FORM_FIELD'" label="人员字段">
          <el-select v-model="nodeForm.properties.assigneeValue" filterable allow-create style="width: 100%" placeholder="选择或输入保存用户名的字段 Key"><el-option v-for="field in assigneeFieldOptions" :key="field.fieldKey" :label="`${field.fieldLabel}（${field.fieldKey}）`" :value="field.fieldKey" /></el-select>
        </el-form-item>
        <el-alert v-else title="运行时将根据发起人的主岗位，查找所在部门负责人" type="info" :closable="false" show-icon />
        <el-form-item v-if="nodeForm.nodeType === 'APPROVAL'" label="审批方式"><el-select v-model="nodeForm.properties.approvalMode" style="width: 100%"><el-option label="单人审批" value="SINGLE" /><el-option label="任意一人通过" value="ANY" /><el-option label="全部通过" value="ALL" /></el-select></el-form-item>
        <el-form-item label="办理时限(小时)"><el-input-number v-model="nodeForm.properties.dueHours" :min="0" /></el-form-item>
        <el-form-item label="退回策略"><el-select v-model="nodeForm.properties.returnPolicy" style="width: 100%"><el-option label="退回上一节点" value="PREVIOUS" /><el-option label="退回发起人" value="STARTER" /><el-option label="允许选择节点" value="SELECTABLE" /></el-select></el-form-item>
        </template>
        <template v-if="automaticNode">
        <el-form-item label="执行器">
          <el-select v-model="nodeForm.executor" placeholder="请选择已注册执行器" style="width: 100%"><el-option v-for="item in matchingExecutors" :key="item.code" :label="`${item.name}${item.available ? '' : '（待接入）'}`" :value="item.code" :disabled="!item.available" /></el-select>
        </el-form-item>
        <el-form-item label="执行超时(秒)">
          <el-input-number v-model="nodeForm.timeoutSec" :min="0" />
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="nodeForm.retryMax" :min="0" />
        </el-form-item>
        <el-form-item label="重试间隔(秒)">
          <el-input-number v-model="nodeForm.retryIntervalSec" :min="0" />
        </el-form-item>
        <el-form-item label="输入Schema">
          <el-input type="textarea" v-model="nodeForm.inputSchema" />
        </el-form-item>
        <el-form-item label="输出Schema">
          <el-input type="textarea" v-model="nodeForm.outputSchema" />
        </el-form-item>
        </template>
        <template v-if="conditionNode"><el-alert title="分支条件请在节点的出线上配置；未填写条件的出线作为默认分支。" type="info" :closable="false" /></template>
        <el-collapse class="advanced-config"><el-collapse-item title="高级配置" name="advanced"><el-form-item label="扩展JSON"><el-input type="textarea" v-model="nodeForm.advancedPropertiesJson" /></el-form-item></el-collapse-item></el-collapse>
      </el-form>
        </el-tab-pane>
        <el-tab-pane label="附加表单" name="forms">
          <el-select v-model="nodeForm.additionalFormIds" multiple filterable clearable placeholder="选择该节点需要额外展示的表单" style="width: 100%" @change="loadPermissionFields">
            <el-option v-for="form in additionalFormOptions" :key="form.id" :label="`${form.formName} (${form.version})`" :value="form.id" />
          </el-select>
          <div class="form-tip node-tab-tip">主表单会自动参与字段权限配置，附加表单仅在当前节点展示。</div>
        </el-tab-pane>
        <el-tab-pane label="字段权限" name="permissions">
          <el-table :data="permissionFields" border max-height="360">
            <el-table-column prop="formName" label="表单" width="180" />
            <el-table-column prop="fieldLabel" label="字段" />
            <el-table-column prop="fieldKey" label="字段Key" width="160" />
            <el-table-column label="权限" width="180">
              <template #default="scope">
                <el-select v-model="nodeForm.fieldPermissions[scope.row.permissionKey]" style="width: 140px">
                  <el-option label="可编辑" value="EDIT" />
                  <el-option label="只读" value="READ" />
                  <el-option label="隐藏" value="HIDDEN" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="节点必填" width="100">
              <template #default="scope"><el-checkbox v-model="nodeForm.requiredFields[scope.row.permissionKey]" :disabled="nodeForm.fieldPermissions[scope.row.permissionKey] !== 'EDIT'" /></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!permissionFields.length" description="请先为流程选择主表单或为节点选择附加表单" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="showNodeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveNodeEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { VueFlow, addEdge, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import {
  listWorkflows,
  createWorkflow,
  updateWorkflow,
  deleteWorkflow,
  disableWorkflow,
  createWorkflowVersion,
  listWorkflowNodes,
  createWorkflowNode,
  updateWorkflowNode,
  deleteWorkflowNode,
  listWorkflowEdges,
  createWorkflowEdge,
  updateWorkflowEdge,
  deleteWorkflowEdge,
  dictListByCode,
  validateWorkflow,
  publishWorkflow,
  getWorkflowAiSetting,
  updateWorkflowAiSetting,
  listWorkflowExecutors,
  listForms,
  listFormFields,
  listUsers,
  listRoles,
  workflowMonitor,
} from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const { fitView } = useVueFlow()

const viewMode = ref('list')
const aiSetting = ref({ enabled: false, providerMode: 'LOCAL_PRIVATE', allowExternal: false, maskSensitiveData: true })
const reviewFindings = ref([])
const showReviewDialog = ref(false)

const flows = ref([])
const monitor = ref({})
const monitorCards = computed(() => [{ label: '流程实例', value: monitor.value.instances || 0 }, { label: '运行中', value: monitor.value.running || 0 }, { label: '已完成', value: monitor.value.completed || 0 }, { label: '待办任务', value: monitor.value.pendingTasks || 0 }, { label: '已超时', value: monitor.value.overdueTasks || 0 }])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const currentFlow = ref(null)
const nodes = ref([])
const edges = ref([])
const loadedNodeIds = ref([])
const loadedEdgeIds = ref([])

const flowNodes = ref([])
const flowEdges = ref([])
const selectedNodeId = ref('')
const canvasLoading = ref(false)
const canvasError = ref('')
const showEdgeDialog = ref(false)
const edgeForm = ref({ id: '', conditionExpr: '', isDefault: false, fieldKey: '', operator: '==', compareValue: '' })
const edgeConditionFields = ref([])

const newNodeName = ref('')
const newNodeType = ref('')
const newNodeCategory = ref('')
const executorOptions = ref([])
const nodeTypeGroups = [
  { label: '人工节点', value: 'HUMAN', options: [{ label: '审批节点', value: 'APPROVAL' }, { label: '办理节点', value: 'TASK' }, { label: '抄送节点', value: 'CC' }] },
  { label: '自动节点', value: 'AUTOMATIC', options: [{ label: '内部服务', value: 'SERVICE_TASK' }, { label: 'HTTP API', value: 'HTTP_TASK' }, { label: 'AI Agent', value: 'AGENT_TASK' }, { label: '消息发送', value: 'MESSAGE_TASK' }] },
  { label: '控制节点', value: 'CONTROL', options: [{ label: '开始节点', value: 'START' }, { label: '条件节点', value: 'CONDITION' }, { label: '结束节点', value: 'END' }] },
]
const addableNodeGroups = computed(() => nodeTypeGroups.map((group) => ({ ...group, options: group.options.filter((item) => !['START', 'END'].includes(item.value)) })))
const addableNodeTypes = computed(() => addableNodeGroups.value.find((group) => group.value === newNodeCategory.value)?.options || [])

const flowStatusOptions = ref([])
const flowCategoryOptions = ref([])
const flowForm = ref({
  id: '',
  flowCode: '',
  flowName: '',
  category: '',
  version: 'v1',
  description: '',
  entryNodeKey: '',
  status: '',
  tags: '',
  configJson: '',
  mainFormId: '',
  managerUser: '',
  starterScopeJson: '{"type":"ALL"}',
  aiAssistEnabled: false,
})

const showFlowDialog = ref(false)

const showNodeDialog = ref(false)
const nodeDialogTab = ref('basic')
const formOptions = ref([])
const permissionFields = ref([])
const assigneeUsers = ref([])
const assigneeRoles = ref([])
const nodeForm = ref({
  id: '',
  flowId: '',
  nodeKey: '',
  nodeName: '',
  nodeType: '',
  executor: '',
  timeoutSec: 0,
  retryMax: 0,
  retryIntervalSec: 0,
  inputSchema: '',
  outputSchema: '',
  propertiesJson: '',
  advancedPropertiesJson: '{}',
  properties: {},
  additionalFormIds: [],
  fieldPermissions: {},
  requiredFields: {},
})

const additionalFormOptions = computed(() => formOptions.value.filter((form) => form.id !== flowForm.value.mainFormId))
const humanNode = computed(() => ['APPROVAL', 'TASK', 'CC'].includes(nodeForm.value.nodeType))
const automaticNode = computed(() => ['SERVICE_TASK', 'HTTP_TASK', 'AGENT_TASK', 'MESSAGE_TASK'].includes(nodeForm.value.nodeType))
const conditionNode = computed(() => nodeForm.value.nodeType === 'CONDITION')
const lockedNodeType = computed(() => ['START', 'END'].includes(nodeForm.value.nodeType))
const matchingExecutors = computed(() => executorOptions.value.filter((item) => item.nodeType === nodeForm.value.nodeType))
const entryNodeOptions = computed(() => flowNodes.value.filter((node) => node._nodeType === 'START'))
const assigneeFieldOptions = computed(() => permissionFields.value.filter((field) => ['USER', 'USER_SELECT', 'TEXT', 'SELECT'].includes(String(field.fieldType || '').toUpperCase())))

const parseJson = (value, fallback) => {
  try { return value ? JSON.parse(value) : fallback } catch { return fallback }
}

const loadForms = async () => {
  const res = await listForms({ page: 0, size: 200 })
  formOptions.value = (res?.data?.content || []).filter((form) => form.status === 'PUBLISHED')
}
const loadExecutors = async () => { const res = await listWorkflowExecutors(); executorOptions.value = res?.data || [] }
const loadAssigneeOptions = async () => {
  const [usersRes, rolesRes] = await Promise.all([listUsers({ page: 0, size: 500 }), listRoles({ page: 0, size: 500 })])
  assigneeUsers.value = usersRes?.data?.content || []
  assigneeRoles.value = rolesRes?.data?.content || rolesRes?.data || []
}

const loadPermissionFields = async () => {
  const formIds = [flowForm.value.mainFormId, ...nodeForm.value.additionalFormIds].filter(Boolean)
  const results = await Promise.all(formIds.map(async (formId) => {
    const res = await listFormFields(formId)
    const form = formOptions.value.find((item) => item.id === formId)
    return (res?.data || []).map((field) => ({ ...field, formName: form?.formName || formId, permissionKey: `${formId}.${field.fieldKey}` }))
  }))
  permissionFields.value = results.flat()
  permissionFields.value.forEach((field) => {
    if (!nodeForm.value.fieldPermissions[field.permissionKey]) nodeForm.value.fieldPermissions[field.permissionKey] = 'EDIT'
    if (nodeForm.value.requiredFields[field.permissionKey] == null) nodeForm.value.requiredFields[field.permissionKey] = !!field.required
  })
}

const loadFlows = async () => {
  const res = await listWorkflows({ page: page.value - 1, size: size.value })
  flows.value = res?.data?.content || []
  total.value = res?.data?.totalElements || 0
}
const loadMonitor = async () => { const res = await workflowMonitor(); monitor.value = res?.data || {} }

const loadAiSetting = async () => {
  const res = await getWorkflowAiSetting()
  if (res?.data) aiSetting.value = res.data
}

const saveAiSetting = async () => {
  aiSetting.value.allowExternal = false
  const res = await updateWorkflowAiSetting(aiSetting.value)
  if (res?.data) aiSetting.value = res.data
  ElMessage.success(aiSetting.value.enabled ? 'AI辅助已开启，仅允许配置的私有化Provider' : 'AI辅助已关闭，流程中心进入全人工模式')
}

const loadFlowStatusOptions = async () => {
  const res = await dictListByCode('DICT_WORKFLOW_STATUS')
  flowStatusOptions.value = res?.data || []
}

const loadFlowCategoryOptions = async () => {
  const fallback = [
    { label: '人事管理', value: 'HR' }, { label: '行政办公', value: 'ADMIN' },
    { label: '财务管理', value: 'FINANCE' }, { label: '采购管理', value: 'PURCHASE' },
    { label: '合同管理', value: 'CONTRACT' }, { label: '医疗业务', value: 'MEDICAL' },
    { label: '信息化管理', value: 'IT' }, { label: '其他', value: 'OTHER' },
  ]
  try {
    const res = await dictListByCode('DICT_WORKFLOW_CATEGORY')
    const values = res?.data || []
    flowCategoryOptions.value = values.length ? values.map((item) => ({ label: item.dictName, value: item.dictValue })) : fallback
  } catch {
    flowCategoryOptions.value = fallback
  }
}

const toCanvasNodes = (list) => {
  return list.map((n, idx) => {
    let pos = { x: 80 + idx * 40, y: 80 + idx * 20 }
    try {
      if (n.propertiesJson) {
        const p = JSON.parse(n.propertiesJson)
        if (p?.position) pos = p.position
      }
    } catch (e) {}
    return {
      id: n.nodeKey,
      data: { label: n.nodeName || n.nodeKey },
      position: pos,
      sourcePosition: 'right',
      targetPosition: 'left',
      type: 'default',
      _nodeKey: n.nodeKey,
      _id: n.id,
      _nodeType: n.nodeType,
    }
  })
}

const toCanvasEdges = (list) => {
  return list.map((e) => ({
    id: e.id || `edge-${e.fromNodeKey}-${e.toNodeKey}`,
    source: e.fromNodeKey,
    target: e.toNodeKey,
    label: e.conditionExpr || '',
    _edgeId: e.id || '',
    _isDefault: !!e.isDefault,
  }))
}

const loadNodes = async (flowId) => {
  const res = await listWorkflowNodes({ flowId })
  nodes.value = res?.data || []
  loadedNodeIds.value = nodes.value.map((n) => n.id)
  flowNodes.value = toCanvasNodes(nodes.value)
}

const loadEdges = async (flowId) => {
  const res = await listWorkflowEdges({ flowId })
  edges.value = res?.data || []
  loadedEdgeIds.value = edges.value.map((e) => e.id)
  flowEdges.value = toCanvasEdges(edges.value)
}

const selectFlow = async (row) => {
  canvasLoading.value = true
  canvasError.value = ''
  currentFlow.value = row
  flowForm.value = { ...row }
  selectedNodeId.value = ''
  try {
    await Promise.all([loadNodes(row.id), loadEdges(row.id)])
    await nextTick()
    requestAnimationFrame(() => fitView({ padding: 0.2, duration: 250 }))
  } catch (error) {
    nodes.value = []
    edges.value = []
    flowNodes.value = []
    flowEdges.value = []
    canvasError.value = `画布加载失败：${error?.message || '节点或连线数据请求失败'}`
    ElMessage.error(canvasError.value)
  } finally {
    canvasLoading.value = false
  }
  loadFlowStatusOptions().catch(() => { flowStatusOptions.value = [] })
}

const onPageChange = (val) => {
  page.value = val
  loadFlows()
}
const onSizeChange = (val) => {
  size.value = val
  page.value = 1
  loadFlows()
}

const createDefaultCanvasNodes = () => ([
  {
    id: 'start',
    data: { label: '开始' },
    position: { x: 120, y: 220 },
    sourcePosition: 'right',
    targetPosition: 'left',
    type: 'default',
    _nodeKey: 'start',
    _nodeType: 'START',
  },
  {
    id: 'end',
    data: { label: '结束' },
    position: { x: 560, y: 220 },
    sourcePosition: 'right',
    targetPosition: 'left',
    type: 'default',
    _nodeKey: 'end',
    _nodeType: 'END',
  },
])

const openCreateFlow = async () => {
  viewMode.value = 'editor'
  currentFlow.value = null
  flowForm.value = { id: '', flowCode: '', flowName: '', category: '', version: 'v1', description: '', entryNodeKey: 'start', status: 'DRAFT', tags: '', configJson: '', mainFormId: '', managerUser: '', starterScopeJson: '{"type":"ALL"}', aiAssistEnabled: false }
  nodes.value = []
  edges.value = []
  flowNodes.value = createDefaultCanvasNodes()
  flowEdges.value = []
  loadedNodeIds.value = []
  loadedEdgeIds.value = []
  await loadFlowStatusOptions()
}

const openEditFlow = async (row) => {
  viewMode.value = 'editor'
  await selectFlow(row)
}

const backToList = () => {
  viewMode.value = 'list'
}

const openFlowDialog = async () => {
  if (!flowStatusOptions.value.length) await loadFlowStatusOptions()
  showFlowDialog.value = true
}

const submitFlow = async () => {
  const isCreate = !flowForm.value.id
  if (isCreate) {
    const draftNodes = flowNodes.value.map((node) => ({ ...node, data: { ...node.data }, position: { ...node.position } }))
    const draftEdges = flowEdges.value.map((edge) => ({ ...edge }))
    const res = await createWorkflow(flowForm.value)
    const created = res?.data
    if (created?.id) {
      await selectFlow(created)
      flowNodes.value = draftNodes
      flowEdges.value = draftEdges
      nodes.value = []
      edges.value = []
      loadedNodeIds.value = []
      loadedEdgeIds.value = []
      await saveCanvas()
    }
  } else {
    await updateWorkflow(flowForm.value)
  }
  showFlowDialog.value = false
  await loadFlows()
  if (isCreate) {
    if (!currentFlow.value?.id) {
      const match = flows.value.find((f) => f.flowName === flowForm.value.flowName && f.version === flowForm.value.version)
      if (match) await selectFlow(match)
    }
  } else if (flowForm.value.id) {
    const match = flows.value.find((f) => f.id === flowForm.value.id)
    if (match) {
      currentFlow.value = match
      flowForm.value = { ...match }
      await saveCanvas()
    }
  }
}

const runCheck = async (ai) => {
  if (!currentFlow.value?.id) return ElMessage.warning('请先保存流程定义')
  await saveCanvas()
  const res = await validateWorkflow(currentFlow.value.id, ai)
  reviewFindings.value = res?.data || []
  showReviewDialog.value = true
}

const publishCurrent = async () => {
  if (!currentFlow.value?.id) return
  await saveCanvas()
  await ElMessageBox.confirm('发布后当前版本将不可修改，后续调整需要创建新版本。确认发布？', '发布流程', { type: 'warning' })
  await publishWorkflow(currentFlow.value.id)
  ElMessage.success('流程发布成功')
  await loadFlows()
  backToList()
}

const removeFlow = async (row) => {
  await deleteWorkflow(row.id)
  if (currentFlow.value?.id === row.id) {
    currentFlow.value = null
  }
  loadFlows()
}

const disableCurrent = async (row) => {
  await ElMessageBox.confirm(`确认停用流程“${row.flowName}” ${row.version}？停用后不能再发起新实例。`, '停用流程', { type: 'warning' })
  await disableWorkflow(row.id)
  ElMessage.success('流程已停用')
  await loadFlows()
}

const newVersion = async (row) => {
  const result = await ElMessageBox.prompt('请输入新版本号', `基于 ${row.version} 创建新版本`, { inputValue: nextVersion(row.version), inputPattern: /^\S+$/, inputErrorMessage: '版本号不能为空' })
  const res = await createWorkflowVersion(row.id, result.value)
  ElMessage.success('新版本已创建，节点和连线已复制')
  await loadFlows()
  if (res?.data) await openEditFlow(res.data)
}

const nextVersion = (version) => {
  const match = String(version || '').match(/^(.*?)(\d+)$/)
  return match ? `${match[1]}${Number(match[2]) + 1}` : `${version || 'v1'}.1`
}

const onConnect = (params) => {
  const source = flowNodes.value.find((node) => node.id === params.source)
  const target = flowNodes.value.find((node) => node.id === params.target)
  if (source?._nodeType === 'END') return ElMessage.warning('结束节点不能连接后续节点')
  if (target?._nodeType === 'START') return ElMessage.warning('开始节点不能连接前置节点')
  flowEdges.value = addEdge({ ...params }, flowEdges.value)
}

const onNodeClick = (payload) => { selectedNodeId.value = (payload?.node || payload)?.id || '' }

const removeSelectedNode = async () => {
  const node = flowNodes.value.find((item) => item.id === selectedNodeId.value)
  if (!node) return
  if (['START', 'END'].includes(node._nodeType)) return ElMessage.warning('开始和结束节点不能删除')
  await ElMessageBox.confirm(`确认删除节点“${node.data?.label || node.id}”及其关联连线？`, '删除节点', { type: 'warning' })
  flowNodes.value = flowNodes.value.filter((item) => item.id !== node.id)
  flowEdges.value = flowEdges.value.filter((edge) => edge.source !== node.id && edge.target !== node.id)
  selectedNodeId.value = ''
}

const parseConditionExpr = (expr = '') => {
  const match = String(expr).trim().match(/^([A-Za-z_][\w.]*)\s*(==|!=|>=|<=|>|<)\s*(.+)$/)
  return match ? { fieldKey: match[1], operator: match[2], compareValue: match[3].replace(/^['\"]|['\"]$/g, '') } : { fieldKey: '', operator: '==', compareValue: '' }
}

const buildConditionExpr = (form) => {
  if (!form.fieldKey || form.compareValue === '') return ''
  const field = edgeConditionFields.value.find((item) => item.fieldKey === form.fieldKey)
  const numeric = ['NUMBER', 'INTEGER', 'DECIMAL'].includes(String(field?.fieldType || '').toUpperCase())
  const value = numeric || /^(true|false|null)$/i.test(String(form.compareValue)) ? form.compareValue : JSON.stringify(String(form.compareValue))
  return `${form.fieldKey} ${form.operator} ${value}`
}

const onEdgeDoubleClick = async (payload) => {
  const edge = payload?.edge || payload
  if (!edge) return
  edgeConditionFields.value = []
  if (flowForm.value.mainFormId) {
    const res = await listFormFields(flowForm.value.mainFormId)
    edgeConditionFields.value = res?.data || []
  }
  const parsed = parseConditionExpr(edge.label || '')
  edgeForm.value = { id: edge.id, conditionExpr: edge.label || '', isDefault: !!edge._isDefault, ...parsed }
  showEdgeDialog.value = true
}

const nextFreeNodePosition = () => {
  const startX = 100
  const startY = 80
  const columnGap = 180
  const rowGap = 105
  const columns = 5
  for (let index = 0; index < 100; index += 1) {
    const position = {
      x: startX + (index % columns) * columnGap,
      y: startY + Math.floor(index / columns) * rowGap,
    }
    const occupied = flowNodes.value.some((node) => (
      Math.abs((node.position?.x || 0) - position.x) < 135
      && Math.abs((node.position?.y || 0) - position.y) < 65
    ))
    if (!occupied) return position
  }
  return { x: startX, y: startY + flowNodes.value.length * rowGap }
}

const saveEdgeEdit = () => {
  if (edgeForm.value.isDefault) edgeForm.value.conditionExpr = ''
  else {
    edgeForm.value.conditionExpr = buildConditionExpr(edgeForm.value)
    if (!edgeForm.value.conditionExpr) return ElMessage.warning('请完整配置分支条件')
  }
  flowEdges.value = flowEdges.value.map((edge) => edge.id === edgeForm.value.id ? { ...edge, label: edgeForm.value.conditionExpr, _isDefault: edgeForm.value.isDefault } : edge)
  showEdgeDialog.value = false
}

const addCanvasNode = () => {
  if (!newNodeType.value) return ElMessage.warning('请先选择节点类型')
  const requestedType = newNodeType.value.trim().toUpperCase()
  if (requestedType === 'START' && flowNodes.value.some((node) => node._nodeType === 'START')) return ElMessage.warning('一个流程只能有一个开始节点')
  const key = `node_${Date.now()}`
  const label = newNodeName.value || key
  flowNodes.value = [
    ...flowNodes.value,
    {
      id: key,
      data: { label },
      position: nextFreeNodePosition(),
      sourcePosition: 'right',
      targetPosition: 'left',
      type: 'default',
      _nodeKey: key,
      _nodeType: requestedType,
    },
  ]
  newNodeName.value = ''
  newNodeType.value = ''
  newNodeCategory.value = ''
}

const onNodeDblClick = async (payload) => {
  const n = payload?.node || payload
  if (!n) return
  const nodeKey = n.id || n._nodeKey
  const existing = nodes.value.find((x) => x.nodeKey === nodeKey)
  const properties = parseJson(existing?.propertiesJson, {})
  nodeForm.value = {
    id: existing?.id || n._id || '',
    flowId: currentFlow.value?.id || '',
    nodeKey: existing?.nodeKey || n._nodeKey || n.id,
    nodeName: existing?.nodeName || n.data?.label || '',
    nodeType: existing?.nodeType || n._nodeType || '',
    executor: existing?.executor || '',
    timeoutSec: existing?.timeoutSec || 0,
    retryMax: existing?.retryMax || 0,
    retryIntervalSec: existing?.retryIntervalSec || 0,
    inputSchema: existing?.inputSchema || '',
    outputSchema: existing?.outputSchema || '',
    propertiesJson: existing?.propertiesJson || '',
    advancedPropertiesJson: JSON.stringify(properties, null, 2),
    properties: {
      assigneeMode: properties.assigneeMode || 'USER', assigneeValue: properties.assigneeValue || properties.assignee || '', approvalMode: properties.approvalMode || 'SINGLE', dueHours: properties.dueHours || 0, returnPolicy: properties.returnPolicy || 'PREVIOUS',
    },
    additionalFormIds: parseJson(existing?.additionalFormIds, []),
    fieldPermissions: parseJson(existing?.fieldPermissionsJson, {}).permissions || {},
    requiredFields: parseJson(existing?.fieldPermissionsJson, {}).required || {},
  }
  nodeDialogTab.value = 'basic'
  await loadPermissionFields()
  showNodeDialog.value = true
}

const saveNodeEdit = () => {
  if (humanNode.value && nodeForm.value.properties.assigneeMode !== 'INITIATOR_MANAGER' && !nodeForm.value.properties.assigneeValue) return ElMessage.warning('请选择或填写处理人参数')
  const advanced = parseJson(nodeForm.value.advancedPropertiesJson, null)
  if (!advanced || Array.isArray(advanced)) return ElMessage.warning('高级配置必须是合法的 JSON 对象')
  const propertiesJson = JSON.stringify({ ...advanced, ...nodeForm.value.properties })
  const key = nodeForm.value.nodeKey
  flowNodes.value = flowNodes.value.map((n) => {
    if (n.id === key || n._nodeKey === key) {
      return {
        ...n,
        id: key,
        data: { label: nodeForm.value.nodeName || key },
        _nodeKey: key,
        _nodeType: nodeForm.value.nodeType || n._nodeType,
      }
    }
    return n
  })
  const index = nodes.value.findIndex((x) => x.nodeKey === key)
  if (index >= 0) {
    nodes.value[index] = {
      ...nodes.value[index],
      ...nodeForm.value,
      propertiesJson,
      additionalFormIds: JSON.stringify(nodeForm.value.additionalFormIds),
      fieldPermissionsJson: JSON.stringify({ permissions: nodeForm.value.fieldPermissions, required: nodeForm.value.requiredFields }),
    }
  } else {
    nodes.value.push({
      ...nodeForm.value,
      propertiesJson,
      additionalFormIds: JSON.stringify(nodeForm.value.additionalFormIds),
      fieldPermissionsJson: JSON.stringify({ permissions: nodeForm.value.fieldPermissions, required: nodeForm.value.requiredFields }),
    })
  }
  showNodeDialog.value = false
}

const saveCanvas = async () => {
  if (!currentFlow.value?.id) return
  const flowId = currentFlow.value.id

  const toSaveNodes = flowNodes.value.map((n) => {
    const existing = nodes.value.find((x) => x.nodeKey === n.id || x.nodeKey === n._nodeKey)
    const nodeKey = existing?.nodeKey || n._nodeKey || n.id
    let properties = {}
    try { properties = JSON.parse(existing?.propertiesJson || '{}') } catch { properties = {} }
    properties.position = n.position
    return {
      id: existing?.id,
      flowId,
      nodeKey,
      nodeName: n.data?.label || nodeKey,
      nodeType: existing?.nodeType || n._nodeType || 'TASK',
      executor: existing?.executor || '',
      timeoutSec: existing?.timeoutSec || 0,
      retryMax: existing?.retryMax || 0,
      retryIntervalSec: existing?.retryIntervalSec || 0,
      inputSchema: existing?.inputSchema || '',
      outputSchema: existing?.outputSchema || '',
      propertiesJson: JSON.stringify(properties),
      additionalFormIds: existing?.additionalFormIds || '[]',
      fieldPermissionsJson: existing?.fieldPermissionsJson || '{"permissions":{},"required":{}}',
    }
  })

  for (const payload of toSaveNodes) {
    if (payload.id) await updateWorkflowNode(payload)
    else await createWorkflowNode(payload)
  }

  const currentIds = toSaveNodes.map((n) => n.id).filter(Boolean)
  const toDeleteNodes = loadedNodeIds.value.filter((id) => !currentIds.includes(id))
  for (const id of toDeleteNodes) await deleteWorkflowNode(id)

  await loadNodes(flowId)

  const toSaveEdges = flowEdges.value.map((e) => {
    const fromKey = e.source
    const toKey = e.target
    const existing = edges.value.find((x) => x.id === e.id || (x.fromNodeKey === fromKey && x.toNodeKey === toKey))
    return {
      id: existing?.id,
      flowId,
      fromNodeKey: fromKey,
      toNodeKey: toKey,
      conditionExpr: e.label || '',
      isDefault: e._isDefault ?? existing?.isDefault ?? false,
    }
  })

  for (const payload of toSaveEdges) {
    if (payload.id) await updateWorkflowEdge(payload)
    else await createWorkflowEdge(payload)
  }

  const edgeIds = toSaveEdges.map((e) => e.id).filter(Boolean)
  const toDeleteEdges = loadedEdgeIds.value.filter((id) => !edgeIds.includes(id))
  for (const id of toDeleteEdges) await deleteWorkflowEdge(id)

  await loadEdges(flowId)
}

const enableConnect = () => {
  // 预留：可扩展为切换连线模式
}

const resetView = () => {
  fitView({ padding: 0.2 })
}

Promise.all([loadFlows(), loadMonitor(), loadAiSetting(), loadForms(), loadExecutors(), loadAssigneeOptions(), loadFlowCategoryOptions()])
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.monitor-cards{display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));gap:12px;margin:16px 0}.monitor-value{font-size:24px;font-weight:700;color:#1f2937}.monitor-label{margin-top:5px;color:#8492a6;font-size:13px}
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.canvas-wrapper {
  height: clamp(520px, 68vh, 900px);
  min-height: 520px;
  border: 1px solid #eee;
  border-radius: 6px;
  overflow: hidden;
}
.toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.ai-switch-label { color: #64748b; font-size: 13px; }
.form-tip { margin-left: 10px; color: #94a3b8; font-size: 12px; }
.node-tab-tip { margin: 10px 0 0; }
.advanced-config { margin-top: 16px; }
.canvas-error { margin-bottom: 8px; }
:deep(.vue-flow__node-default) {
  min-width: 108px;
  max-width: 150px;
  min-height: 32px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 18px;
  border-radius: 5px;
}
:deep(.vue-flow__handle) {
  width: 7px;
  height: 7px;
}
</style>
