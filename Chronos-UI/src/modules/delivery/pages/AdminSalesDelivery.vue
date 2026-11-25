<template>
  <section class="page">
    <header>
      <h2>销售与交付</h2>
      <div>
        <el-button v-if="canManageCatalog" @click="catalogOpen = true">产品目录</el-button>
        <el-button type="primary" @click="openOrder()">录入订单</el-button>
      </div>
    </header>
    <el-alert v-if="signingKeyConfigured === false" type="warning" :closable="false"
      title="签发密钥尚未配置。可以先录入订单和部署信息，签发时需要配置 CHRONOS_DELIVERY_SIGNING_KEY。" />
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="订单、客户或合同编号" clearable @keyup.enter="query" />
      <el-button @click="query">查询</el-button>
    </div>
    <el-table :data="orders" v-loading="loading" border>
      <el-table-column prop="order_no" label="订单编号" />
      <el-table-column prop="customer" label="客户" />
      <el-table-column prop="contract_no" label="合同编号" />
      <el-table-column prop="amount" label="成交金额" />
      <el-table-column label="状态"><template #default="{row}">{{ row.confirmed ? '已确认' : '草稿' }}</template></el-table-column>
      <el-table-column label="操作" width="180"><template #default="{row}">
        <el-button link type="primary" @click="openDetail(row.id)">查看交付</el-button>
        <el-button v-if="!row.confirmed" link @click="confirm(row)">确认订单</el-button>
      </template></el-table-column>
    </el-table>
    <div class="pager">
      <el-button :disabled="page === 0" @click="changePage(-1)">上一页</el-button>
      <span>第 {{ page + 1 }} 页</span>
      <el-button :disabled="orders.length < 20" @click="changePage(1)">下一页</el-button>
    </div>

    <el-dialog v-model="catalogOpen" title="产品目录" width="min(980px, 94vw)">
      <p>已上架产品可录入订单并签发。待接入产品显示当前缺口。</p>
      <el-table :data="products" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="product-coverage">
              <p v-if="row.pendingReason"><strong>单售缺口</strong> {{ row.pendingReason }}</p>
              <dl>
                <dt>已登记接口</dt><dd><code v-for="item in row.endpoints" :key="item">{{ item }}</code></dd>
                <dt>已登记菜单</dt><dd><code v-for="item in row.menus" :key="item">{{ item }}</code><span v-if="!row.menus?.length">通过业务侧边栏或入口接入，待验收</span></dd>
                <dt>查看权限</dt><dd>{{ row.readPermissions?.join('、') || '待验证业务账号权限' }}</dd>
                <dt>操作权限</dt><dd>{{ row.writePermissions?.join('、') || '待验证业务账号权限' }}</dd>
              </dl>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="产品名称" />
        <el-table-column prop="code" label="模块编码" min-width="170" />
        <el-table-column label="销售状态" width="100">
          <template #default="{ row }">{{ row.issuable === false ? '待接入' : row.enabled ? '已上架' : '已下架' }}</template>
        </el-table-column>
        <el-table-column prop="pendingReason" label="单售缺口" min-width="260" />
        <el-table-column prop="sortOrder" label="排序" width="75" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }"><el-button link @click="openCatalogEdit(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="catalogEditOpen" title="编辑销售模块" width="460px">
      <el-form label-width="85px">
        <el-form-item label="产品名称"><el-input v-model="catalogForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="上架销售"><el-switch v-model="catalogForm.enabled" :disabled="catalogForm.issuable === false" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catalogForm.sortOrder" :min="0" :max="10000" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="saveCatalog">保存目录</el-button></template>
    </el-dialog>

    <el-dialog v-model="orderOpen" :title="orderForm.parentOrderId ? '录入增购订单' : '录入买断订单'" width="560px">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="订单编号"><el-input v-model="orderForm.orderNo" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="orderForm.customer" :disabled="!!orderForm.parentOrderId" /></el-form-item>
        <el-form-item label="合同编号"><el-input v-model="orderForm.contractNo" /></el-form-item>
        <el-form-item label="成交金额"><el-input-number v-model="orderForm.amount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="购买模块"><el-select v-model="orderForm.modules" multiple
          placeholder="选择已上架模块" no-data-text="暂无已上架模块">
          <el-option v-for="item in products" :key="item.code" :value="item.code" :disabled="!item.enabled"
            :label="item.enabled ? item.name : item.name + (item.issuable === false ? '（待接入）' : '（已下架）')" />
        </el-select></el-form-item>
        <el-form-item v-if="dependencyNames.length" label="包含依赖">{{ dependencyNames.join('、') }}</el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="saveOrder">保存订单</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailOpen" size="min(1050px, 94vw)" :title="detail?.order?.order_no || '订单详情'">
      <template v-if="detail">
        <el-steps :active="deliveryStep" finish-status="success" simple class="delivery-steps">
          <el-step title="确认订单" />
          <el-step title="部署配置" />
          <el-step title="签发授权" />
          <el-step title="打包交付" />
        </el-steps>
        <div class="toolbar">
          <strong>{{ detail.order.customer }} · {{ detail.order.contract_no }}</strong>
          <span>{{ specifiedModules(detail.order.modules).join('、') }}</span>
          <el-button v-if="!detail.order.confirmed" @click="confirm(detail.order)">确认订单</el-button>
          <el-button v-if="instance && detail.order.confirmed && !detail.order.parent_order_id"
            @click="openOrder(detail.order)">录入增购</el-button>
        </div>
        <div v-if="detail.order.confirmed && !detail.deployments.length" class="deployment-empty">
          <template v-if="detail.order.parent_order_id">
            <p>请先在首购订单中创建部署实例，增购授权会更新同一实例。</p>
            <el-button type="primary" @click="openDetail(detail.order.parent_order_id)">打开首购订单</el-button>
          </template>
          <template v-else>
            <p>为客户的生产或测试环境保存部署配置，系统会生成实例编号，并在这里显示签发和打包操作。</p>
            <el-button type="primary" @click="openConfig()">创建部署实例</el-button>
          </template>
        </div>
        <div v-else-if="detail.deployments.length" class="toolbar">
          <span>部署环境</span>
          <el-select v-if="detail.deployments.length > 1" v-model="instanceId"
            aria-label="部署环境" class="environment-select" @change="loadInstance">
            <el-option v-for="item in detail.deployments" :key="item.id" :value="item.id" :label="environmentName(item.environment)" />
          </el-select>
          <strong v-else>{{ environmentName(detail.deployments[0].environment) }}</strong>
          <el-button v-if="detail.order.confirmed && !detail.order.parent_order_id" @click="openConfig()">新增部署环境</el-button>
        </div>
        <template v-if="instance">
          <p>部署实例 {{ instance.deployment.id }}</p>
          <div class="toolbar">
            <span v-if="config">第 {{ config.revision }} 版配置 · {{ parsed(config.config).publicOrigin }} · {{ parsed(config.config).version }}</span>
            <el-button @click="openConfig(config)">更新配置</el-button>
            <el-button :loading="saving" :disabled="Boolean(issueBlock) || Boolean(orderLicense)"
              @click="issue">{{ orderLicense ? '本订单已签发' : '签发当前订单授权' }}</el-button>
            <el-button @click="refreshDelivery">刷新状态</el-button>
            <el-button type="primary" :disabled="Boolean(packageBlock)" :loading="saving" @click="pack()">生成部署包</el-button>
          </div>
          <div v-if="issueBlock || packageBlock || queued" class="delivery-guidance">
            <p v-if="issueBlock">{{ issueBlock }}</p>
            <p v-if="packageBlock">{{ packageBlock }}</p>
            <p v-if="queued">任务已提交，等待本地构建开始。构建使用任务开始时的本地代码。</p>
          </div>
          <h3>授权记录</h3>
          <el-table :data="instance.licenses" border>
            <el-table-column prop="revision" label="版本" width="70" />
            <el-table-column label="开通模块"><template #default="{row}">{{ names(parsed(row.payload).modules).join('、') }}</template></el-table-column>
            <el-table-column prop="issued_by" label="签发人" width="110" />
            <el-table-column label="操作" width="130"><template #default="{row}">
              <el-button link @click="downloadFile('/deployments/' + instanceId + '/licenses/' + row.revision, 'chronos-license.json')">下载授权</el-button>
            </template></el-table-column>
          </el-table>
          <h3>打包任务</h3>
          <p v-if="packageActive" class="delivery-guidance">任务状态每 4 秒刷新。完成后可在本表下载部署包和校验文件。</p>
          <el-table :data="instance.jobs" border>
            <el-table-column label="包编号" prop="id" min-width="220" />
            <el-table-column label="部署包版本" width="140"><template #default="{row}">
              <div>{{ parsed(instance.configs.find(item => item.revision === row.config_revision)?.config)?.version }}</div>
              <small>配置 {{ row.config_revision }} / 授权 {{ row.license_revision }}</small>
            </template></el-table-column>
            <el-table-column label="进度" min-width="140"><template #default="{row}">
              {{ packageStatus(row) }}
              <small v-if="row.error" class="error">{{ row.error }}</small>
              <small v-if="row.updated_at" class="package-updated">更新于 {{ new Date(row.updated_at).toLocaleString('zh-CN', { hour12: false }) }}</small>
            </template></el-table-column>
            <el-table-column label="操作" min-width="235"><template #default="{row}">
              <el-button v-if="canPackage && row.status === 'FAILED' && row.license_revision === latest?.revision" link @click="pack(row)">重试</el-button>
              <template v-if="row.status === 'READY'">
                <el-button link @click="downloadFile('/packages/' + row.id + '/download', row.id + '.tar.gz')">下载部署包</el-button>
                <el-button link @click="downloadFile('/packages/' + row.id + '/download?checksum=true', row.id + '.tar.gz.sha256')">校验文件</el-button>
                <el-button link @click="openEvent(row)">登记结果</el-button>
              </template>
            </template></el-table-column>
          </el-table>
          <h3>交付记录</h3>
          <el-table :data="instance.events" border>
            <el-table-column prop="job_id" label="包编号" />
            <el-table-column label="状态"><template #default="{row}">{{ eventLabels[row.state] }}</template></el-table-column>
            <el-table-column prop="note" label="说明" />
            <el-table-column prop="actor" label="登记人" />
          </el-table>
        </template>
      </template>
    </el-drawer>

    <el-dialog v-model="configOpen" title="客户部署配置" width="590px">
      <el-form label-width="120px" @submit.prevent>
        <el-form-item label="部署环境">
          <el-select v-model="configForm.environment" :disabled="configEditing" filterable allow-create>
            <el-option v-for="option in environmentOptions" :key="option.value" :value="option.value"
              :label="option.label" :disabled="!configEditing && detail?.deployments?.some(item => item.environment === option.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="对外访问地址"><el-input v-model="configForm.publicOrigin" placeholder="https://chronos.example.com" /></el-form-item>
        <el-form-item label="HTTPS 配置"><el-select v-model="configForm.tlsMode">
          <el-option label="HTTP 直连" value="HTTP" /><el-option label="本机网关终止 HTTPS" value="GATEWAY" /><el-option label="Nginx 终止 HTTPS" value="NGINX" />
        </el-select></el-form-item>
        <el-form-item label="宿主机端口"><el-input-number v-model="configForm.hostPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="服务器架构"><el-select v-model="configForm.architecture"><el-option label="Linux x86_64" value="linux/amd64" /></el-select></el-form-item>
        <el-form-item label="部署包版本"><el-input v-model="configForm.version" placeholder="例如 1.0.0，用于标识交付包" /></el-form-item>
        <el-form-item label="集团管理员"><el-input v-model="configForm.groupAdmin" :disabled="configEditing" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="saveConfig">保存部署配置</el-button></template>
    </el-dialog>

    <el-dialog v-model="eventOpen" title="登记交付结果" width="500px">
      <el-form label-width="75px" @submit.prevent>
        <el-form-item label="状态"><el-select v-model="eventForm.state">
          <el-option v-for="(label,key) in eventLabels" :key="key" :label="label" :value="key" />
        </el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="eventForm.note" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="saveEvent">保存交付记录</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasAdminPermission } from '../../../store/auth'
import * as api from '../api'

const products = ref([])
const canManageCatalog = hasAdminPermission('delivery:sales:catalog:manage')
const canIssue = hasAdminPermission('delivery:sales:issue')
const canPackage = hasAdminPermission('delivery:sales:package')
const catalogOpen = ref(false)
const catalogEditOpen = ref(false)
const catalogForm = reactive({})
const signingKeyConfigured = ref(null)
const orders = ref([])
const keyword = ref('')
const page = ref(0)
const loading = ref(false)
const saving = ref(false)
const orderOpen = ref(false)
const detailOpen = ref(false)
const configOpen = ref(false)
const eventOpen = ref(false)
const configEditing = ref(false)
const detail = ref(null)
const instanceId = ref('')
const instance = ref(null)
const eventJob = ref(null)
const orderForm = reactive({})
const configForm = reactive({})
const eventForm = reactive({})
const eventLabels = { DELIVERED: '已交付', INSTALLED: '确认已安装', INSTALL_FAILED: '安装失败' }
const config = computed(() => instance.value?.configs?.[0])
const latest = computed(() => instance.value?.licenses?.[0])
const orderLicense = computed(() => instance.value?.licenses.find(row => row.order_id === detail.value?.order.id))
const issueBlock = computed(() => {
  if (orderLicense.value) return ''
  if (!detail.value?.order.confirmed) return '请先确认当前订单。'
  if (!canIssue) return '当前账号缺少签发客户授权权限。'
  if (detail.value.order.parent_order_id && !latest.value) return '请先为当前部署环境签发首购授权。'
  if (signingKeyConfigured.value === null) return '正在检查签发配置。'
  if (!signingKeyConfigured.value) return '签发不可用。请在后端启动配置中设置 CHRONOS_DELIVERY_SIGNING_KEY，指向可读取的 Ed25519 私钥文件。'
  return ''
})
const packageBlock = computed(() => {
  if (!canPackage) return '当前账号缺少执行离线打包权限。'
  if (!orderLicense.value) return '请先为当前订单和所选部署环境签发授权，再生成部署包。'
  return ''
})
const queued = computed(() => instance.value?.jobs.some(job => job.status === 'QUEUED'))
const packageActive = computed(() => instance.value?.jobs.some(job => ['QUEUED', 'RUNNING'].includes(job.status)))
const deliveryStep = computed(() => !detail.value?.order.confirmed ? 0 : !config.value ? 1
  : !orderLicense.value ? 2 : 3)
const environmentOptions = [{ value: 'production', label: '生产环境' }, { value: 'test', label: '测试环境' }]
const environmentName = code => environmentOptions.find(item => item.value === code)?.label || code
const parsed = value => typeof value === 'string' ? JSON.parse(value) : value
const names = codes => (codes || []).map(code => products.value.find(item => item.code === code)?.name || code)
const specifiedModules = value => names(parsed(value))
const dependencyNames = computed(() => {
  const selected = orderForm.modules || []
  const seen = new Set(selected)
  const dependencies = []
  function collect(code) {
    for (const dependency of products.value.find(item => item.code === code)?.dependencies || []) {
      if (seen.has(dependency)) continue
      seen.add(dependency)
      dependencies.push(dependency)
      collect(dependency)
    }
  }
  selected.forEach(collect)
  return names(dependencies)
})

async function loadOrders() {
  loading.value = true
  try { orders.value = (await api.orders(keyword.value, page.value)).data } finally { loading.value = false }
}
function query() { page.value = 0; return loadOrders() }
function changePage(offset) { page.value += offset; return loadOrders() }
function openCatalogEdit(row) {
  Object.assign(catalogForm, { code: row.code, name: row.name, enabled: row.enabled, sortOrder: row.sortOrder, issuable: row.issuable })
  catalogEditOpen.value = true
}
function saveCatalog() {
  return action(async () => {
    const { code, name, enabled, sortOrder } = catalogForm
    await api.updateCatalog(code, { name, enabled, sortOrder })
    products.value = (await api.catalog()).data
    catalogEditOpen.value = false
    ElMessage.success('产品目录已保存')
  })
}
function openOrder(parent) {
  Object.assign(orderForm, { orderNo: '', customer: parent?.customer || '', contractNo: '', amount: 0,
    modules: [], parentOrderId: parent?.id || null })
  orderOpen.value = true
}
async function action(run) {
  if (saving.value) return
  saving.value = true
  try { await run() } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败')
  } finally { saving.value = false }
}
function saveOrder() {
  return action(async () => {
    const { data: id } = await api.createOrder({ ...orderForm })
    orderOpen.value = false
    await query()
    await openDetail(id)
  })
}
function confirm(row) {
  return action(async () => {
    await ElMessageBox.confirm('确认后将保留本次订单的成交金额和模块记录。', '确认买断订单')
    await api.confirmOrder(row.id)
    await loadOrders()
    if (detail.value?.order.id === row.id) await openDetail(row.id, instanceId.value)
  })
}
async function openDetail(id, selected = '') {
  const { data } = await api.orderDetail(id)
  detail.value = data
  instanceId.value = data.deployments.find(item => item.id === selected)?.id
    || data.deployments.find(item => item.environment === 'production')?.id || data.deployments[0]?.id || ''
  instance.value = null
  detailOpen.value = true
  await loadInstance()
}
async function loadInstance() {
  const id = instanceId.value
  if (instance.value?.deployment.id !== id) instance.value = null
  if (!id) return
  const { data } = await api.deploymentDetail(id)
  if (id === instanceId.value) instance.value = data
}
async function refreshDelivery() {
  const [status] = await Promise.all([api.salesStatus(), loadInstance()])
  signingKeyConfigured.value = status.data.signingKeyConfigured
}
function openConfig(current) {
  configEditing.value = Boolean(current)
  const environment = environmentOptions.find(option =>
    !detail.value.deployments.some(item => item.environment === option.value))?.value || ''
  Object.assign(configForm, current ? parsed(current.config) : {
    environment, publicOrigin: '', hostPort: 80,
    tlsMode: 'HTTP', architecture: 'linux/amd64', version: '', groupAdmin: 'group-admin',
  })
  configOpen.value = true
}
function saveConfig() {
  return action(async () => {
    const id = detail.value.order.id
    const { data: saved } = await api.configure(id, configEditing.value ? instanceId.value : '', { ...configForm })
    configOpen.value = false
    await openDetail(id, saved)
    ElMessage.success(configEditing.value ? '部署配置已更新' : '部署实例已创建')
  })
}
function issue() {
  return action(async () => {
    await api.sign(instanceId.value, detail.value.order.id)
    await loadInstance()
    ElMessage.success('授权已签发')
  })
}
function pack(job) {
  return action(async () => {
    if (!job && !orderLicense.value) return
    const { data: id } = await api.packageJob(instanceId.value, job?.config_revision || config.value.revision, job?.license_revision || latest.value.revision)
    await loadInstance()
    const current = instance.value?.jobs.find(item => item.id === id)
    if (current?.status === 'FAILED') {
      ElMessage.error(current.error || '部署包生成失败')
    } else if (current) {
      ElMessage.success(packageStatus(current))
    }
  })
}
function packageStatus(job) {
  return {
    QUEUED: '排队 · 等待本地构建',
    RUNNING: job.phase ? `执行中 · ${job.phase}` : '执行中',
    READY: '已生成',
    FAILED: '失败',
  }[job.status]
}
function openEvent(job) {
  eventJob.value = job
  Object.assign(eventForm, { state: 'DELIVERED', note: '' })
  eventOpen.value = true
}
function saveEvent() {
  return action(async () => {
    await api.recordDelivery(eventJob.value.id, { ...eventForm })
    eventOpen.value = false
    await loadInstance()
  })
}
function downloadFile(path, name) {
  api.download(path, name).catch(error => { if (error.name !== 'AbortError') ElMessage.error(error.message) })
}
let timer
onMounted(async () => {
  const [catalog, status] = await Promise.all([api.catalog(), api.salesStatus()])
  products.value = catalog.data
  signingKeyConfigured.value = status.data.signingKeyConfigured
  await loadOrders()
  timer = setInterval(() => {
    if (detailOpen.value && packageActive.value) {
      loadInstance().catch(() => {})
    }
  }, 4000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.page { padding: 24px; }
header, .toolbar, .pager { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
header { justify-content:space-between; }
.toolbar { margin:12px 0; }
.toolbar > .el-input { width:260px; }
.pager { justify-content:flex-end; margin-top:10px; }
.error { display:block; color:var(--el-color-danger); }
.package-updated { display:block; color:var(--el-text-color-secondary); }
.product-coverage { padding: 14px 20px; }
.product-coverage dl { display: grid; grid-template-columns: 110px 1fr; gap: 8px; margin: 0; }
.product-coverage dt { font-weight: 600; }
.product-coverage dd { margin: 0; overflow-wrap: anywhere; }
.product-coverage code { display: block; }
.delivery-steps { margin-bottom:16px; }
.deployment-empty { padding:16px; background:var(--el-fill-color-light); border-radius:8px; }
.deployment-empty p { margin:0 0 12px; }
.environment-select { width:200px; }
.delivery-guidance { margin:12px 0; color:var(--el-text-color-secondary); }
.delivery-guidance p { margin:4px 0; }
</style>
