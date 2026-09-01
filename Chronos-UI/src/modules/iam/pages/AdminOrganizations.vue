<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">机构管理</div>
        <div class="subtitle">维护医院、院区及上级隶属关系，作为全平台组织主数据</div>
      </div>
      <el-button type="primary" @click="openCreate">新增机构</el-button>
    </div>

    <el-table :data="orgs" border style="width: 100%">
      <el-table-column prop="organizationName" label="医院/院区名称" min-width="180" />
      <el-table-column prop="shortName" label="简称" />
      <el-table-column prop="orgCode" label="机构编码" />
      <el-table-column label="机构类型">
        <template #default="scope">{{ organizationTypeLabel(scope.row.organizationType) }}</template>
      </el-table-column>
      <el-table-column prop="tel" label="联系电话" />
      <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
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

    <el-dialog v-model="showDialog" width="640px" :title="dialogMode === 'create' ? '新增医院机构' : '编辑医院机构'">
      <el-form label-width="100px">
        <el-form-item label="机构名称">
          <el-input v-model="form.organizationName" />
        </el-form-item>
        <el-form-item label="组织编码">
          <el-input v-model="form.orgCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="机构简称">
          <el-input v-model="form.shortName" />
        </el-form-item>
        <el-form-item label="机构类型">
          <el-select v-model="form.organizationType" style="width:100%">
            <el-option label="医院" value="HOSPITAL" /><el-option label="院区" value="CAMPUS" /><el-option label="医疗集团" value="MEDICAL_GROUP" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.tel" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.mailingAddress" />
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
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orgList, orgDetail, createOrg, updateOrg, deleteOrg, orgImpact } from '../api'

const orgs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({
  id: '',
  organizationName: '',
  orgCode: '',
  description: '',
  mailingAddress: '',
  tel: '',
  organizationManager: '',
  organizationType: 'HOSPITAL', shortName: '', timezone: 'Asia/Shanghai', status: 1, sortOrder: 0,
})
const enabled = ref(true)

const organizationTypeLabels = {
  HOSPITAL: '医院',
  CAMPUS: '院区',
  MEDICAL_GROUP: '医疗集团',
}

const organizationTypeLabel = (type) => organizationTypeLabels[type] || type || '-'

const load = async () => {
  const res = await orgList({ page: page.value - 1, size: size.value })
  orgs.value = res?.data?.content || []
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

const openCreate = async () => {
  dialogMode.value = 'create'
  form.value = { organizationName: '', orgCode: '', shortName: '', organizationType: 'HOSPITAL', timezone: 'Asia/Shanghai', status: 1, sortOrder: 0, description: '', mailingAddress: '', tel: '' }
  enabled.value = true
  showDialog.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  const detail = await orgDetail(row.id)
  form.value = { ...detail?.data }
  enabled.value = form.value.status !== 0
  showDialog.value = true
}

const submit = async () => {
  form.value.status = enabled.value ? 1 : 0
  if (dialogMode.value === 'create') {
    await createOrg(form.value)
  } else {
    await updateOrg(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  const impact=(await orgImpact(row.id))?.data||{}
  await ElMessageBox.confirm(`停用后将禁止新增部门和任职。当前关联：部门 ${impact.departments||0}、任职 ${impact.assignments||0}、账号 ${impact.accounts||0}。确认停用？`,'停用机构',{type:'warning'})
  await deleteOrg(row.id)
  ElMessage.success('机构已停用，历史数据已保留')
  load()
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
</style>
