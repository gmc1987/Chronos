<template>
  <div>
    <el-button v-if="canSubmit" type="primary" :disabled="!organizationId" @click="openSubmit">发起资产申请</el-button>
    <el-table v-loading="loading" :data="rows" border class="requests">
      <el-table-column label="类型" width="150">
        <template #default="{ row }">{{ operations[row.operation] }}</template>
      </el-table-column>
      <el-table-column prop="title" label="申请标题" min-width="200" />
      <el-table-column prop="requested_by" label="发起人" width="150" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ statuses[row.status] }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">查看</el-button></template>
      </el-table-column>
    </el-table>
    <div class="pages">
      <el-button :disabled="loading || offset === 0" @click="movePage(-10)">上一页</el-button>
      <el-button :disabled="loading || !hasNext" @click="movePage(10)">下一页</el-button>
    </div>

    <el-dialog v-model="submitting" title="发起资产申请" width="min(620px, 94vw)">
      <el-form label-width="100px">
        <el-form-item label="申请类型">
          <el-select v-model="form.operation">
            <el-option v-for="(label, value) in operations" :key="value" :value="value" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请标题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="申请说明"><el-input v-model="form.reason" type="textarea" maxlength="2000" /></el-form-item>
        <template v-if="form.operation === 'ACQUIRE'">
          <el-form-item label="资产编码"><el-input v-model="form.asset.assetCode" maxlength="64" /></el-form-item>
          <el-form-item label="资产名称"><el-input v-model="form.asset.assetName" maxlength="200" /></el-form-item>
          <el-form-item label="资产分类"><el-input v-model="form.asset.category" maxlength="64" /></el-form-item>
          <el-form-item label="存放位置"><el-input v-model="form.asset.location" maxlength="300" /></el-form-item>
        </template>
        <el-form-item v-if="['TRANSFER', 'RETIRE'].includes(form.operation)" label="资产">
          <el-select v-model="form.assetId" filterable>
            <el-option v-for="asset in eligibleAssets" :key="asset.id" :value="asset.id" :label="`${asset.assetCode} · ${asset.assetName}`" />
          </el-select>
        </el-form-item>
        <template v-if="form.operation === 'TRANSFER'">
          <el-form-item label="调入机构"><OrganizationSelect v-model="form.destinationId" context-key="asset-transfer-destination" /></el-form-item>
          <el-form-item label="调入位置"><el-input v-model="form.destinationLocation" maxlength="300" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="submitting = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交审批</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showingDetail" title="资产申请详情" width="min(680px, 94vw)">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请标题">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ operations[current.operation] }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statuses[current.status] }}</el-descriptions-item>
          <el-descriptions-item label="申请说明">{{ current.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="current.details?.asset" label="入库资料">
            {{ current.details.asset.assetCode }} · {{ current.details.asset.assetName }} · {{ current.details.asset.category }} · {{ current.details.asset.location }}
          </el-descriptions-item>
          <el-descriptions-item v-if="current.details?.destinationLocation" label="调入位置">
            {{ current.details.destinationLocation }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table v-if="current.decisions.length" :data="current.decisions" class="requests">
          <el-table-column prop="review_level" label="审批级别" width="100" />
          <el-table-column prop="reviewer" label="审批人" width="145" />
          <el-table-column label="结果" width="100"><template #default="{ row }">{{ row.approved ? '通过' : '驳回' }}</template></el-table-column>
          <el-table-column prop="comment_text" label="审批意见" />
          <el-table-column prop="decided_at" label="审批时间" width="175" />
        </el-table>
        <el-input v-if="showReview" v-model="comment" class="requests" type="textarea" maxlength="2000" placeholder="请输入审批意见" />
      </template>
      <template #footer>
        <el-button @click="showingDetail = false">关闭</el-button>
        <el-button v-if="showReview" :disabled="!comment.trim()" :loading="saving" type="danger" plain @click="review(false)">驳回</el-button>
        <el-button v-if="showReview" :disabled="!comment.trim()" :loading="saving" type="primary" @click="review(true)">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'
import { assetLifecycleRequests, assetLifecycleDetail, submitAssetLifecycleRequest, decideAssetLifecycleRequest } from '../api'

const props = defineProps({ organizationId: String, assets: { type: Array, default: () => [] } })
const emit = defineEmits(['assetChanged'])
const operations = { ACQUIRE: '资产入库', TRANSFER: '资产调拨', RETIRE: '资产报废', DESIGN: '设计需求审批' }
const statuses = { FIRST_REVIEW: '待一级审批', SECOND_REVIEW: '待二级审批', COMPLETED: '已完成', REJECTED: '已驳回' }
const blankForm = () => ({
  operation: 'ACQUIRE', title: '', reason: '', assetId: '', destinationId: '', destinationLocation: '',
  asset: { assetCode: '', assetName: '', category: '', location: '' },
})
const form = reactive(blankForm())
const canSubmit = hasAdminPermission('facility:asset:manage')
const canReview = hasAdminPermission('facility:asset-lifecycle:review')
const username = getAdminUsername()
const eligibleAssets = computed(() => props.assets.filter(asset => ['IN_SERVICE', 'OUT_OF_SERVICE'].includes(asset.status)))
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const offset = ref(0)
const hasNext = ref(false)
const submitting = ref(false)
const showingDetail = ref(false)
const current = ref(null)
const comment = ref('')
let loadVersion = 0
const showReview = computed(() => canReview && current.value
  && ['FIRST_REVIEW', 'SECOND_REVIEW'].includes(current.value.status)
  && current.value.requested_by !== username
  && !current.value.decisions.some(decision => decision.reviewer === username))

async function load() {
  const version = ++loadVersion
  if (!props.organizationId) {
    rows.value = []
    hasNext.value = false
    loading.value = false
    return
  }
  loading.value = true
  try {
    const { data } = await assetLifecycleRequests({ organizationId: props.organizationId, offset: offset.value, limit: 11 })
    if (version !== loadVersion) return
    rows.value = data.slice(0, 10)
    hasNext.value = data.length > 10
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

watch(() => props.organizationId, () => {
  offset.value = 0
  showingDetail.value = false
  current.value = null
  submitting.value = false
  return load()
}, { immediate: true })

function movePage(delta) {
  offset.value += delta
  return load()
}

function openSubmit() {
  Object.assign(form, blankForm())
  submitting.value = true
}

async function submit() {
  saving.value = true
  try {
    await submitAssetLifecycleRequest({ ...form, organizationId: props.organizationId })
    ElMessage.success('资产申请已提交')
    submitting.value = false
    offset.value = 0
    await load()
  } finally {
    saving.value = false
  }
}

async function openDetail(id) {
  const organization = props.organizationId
  const result = await assetLifecycleDetail(id)
  if (props.organizationId !== organization) return
  current.value = result.data
  comment.value = ''
  showingDetail.value = true
}

async function review(approved) {
  saving.value = true
  try {
    const id = current.value.id
    await decideAssetLifecycleRequest(id, { approved, comment: comment.value })
    ElMessage.success(approved ? '已审批通过' : '已驳回')
    await openDetail(id)
    await load()
    emit('assetChanged')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.requests {
  margin-top: 12px;
}
.pages {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
