<template>
  <div class="delegation-page">
    <div class="title">
      <div>
        <h2>流程委托</h2>
        <p>在指定时间内，将全部流程或指定流程的新增任务交给受托人办理</p>
      </div>
      <el-button type="primary" @click="openCreate">新增委托</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="changeTab">
      <el-tab-pane label="我发出的委托">
        <el-table :data="data.outgoing" v-loading="loading">
          <el-table-column prop="delegatee" label="受托人" />
          <el-table-column label="流程范围">
            <template #default="{ row }">{{ row.definitionId || '全部流程' }}</template>
          </el-table-column>
          <el-table-column prop="startAt" label="开始时间" />
          <el-table-column prop="endAt" label="结束时间" />
          <el-table-column prop="reason" label="原因" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="委托给我的">
        <el-table :data="data.incoming" v-loading="loading">
          <el-table-column prop="delegator" label="委托人" />
          <el-table-column label="流程范围">
            <template #default="{ row }">{{ row.definitionId || '全部流程' }}</template>
          </el-table-column>
          <el-table-column prop="startAt" label="开始时间" />
          <el-table-column prop="endAt" label="结束时间" />
          <el-table-column prop="reason" label="原因" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="load"
    />

    <el-dialog v-model="dialog" title="新增流程委托" width="520px">
      <el-form label-width="90px">
        <el-form-item label="受托人" required>
          <el-select v-model="form.delegatee" filterable style="width: 100%">
            <el-option
              v-for="user in users"
              :key="user.username"
              :label="`${user.displayName}（${user.username}）`"
              :value="user.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="流程定义">
          <el-input v-model="form.definitionId" placeholder="留空表示全部流程" />
        </el-form-item>
        <el-form-item label="有效时间" required>
          <el-date-picker
            v-model="range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item label="委托原因">
          <el-input v-model="form.reason" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createWorkflowDelegation,
  deleteWorkflowDelegation,
  listWorkflowDelegations,
  workflowDirectoryUsers
} from '../../../api/admin'

const loading = ref(false)
const dialog = ref(false)
const data = ref({ outgoing: [], incoming: [] })
const users = ref([])
const range = ref([])
const form = ref({ delegatee: '', definitionId: '', reason: '' })
const activeTab = ref('outgoing')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const load = async () => {
  loading.value = true
  try {
    const response = await listWorkflowDelegations({ page: page.value - 1, size: pageSize.value })
    const result = response?.data || { outgoing: [], incoming: [] }
    data.value = {
      outgoing: result.outgoing?.content || result.outgoing || [],
      incoming: result.incoming?.content || result.incoming || [],
    }
    const current = activeTab.value === 'outgoing' ? result.outgoing : result.incoming
    total.value = current?.totalElements ?? (activeTab.value === 'outgoing' ? data.value.outgoing : data.value.incoming).length
  } finally {
    loading.value = false
  }
}

const changeTab = () => {
  page.value = 1
  load()
}

const changePageSize = () => {
  page.value = 1
  load()
}

const openCreate = () => {
  form.value = { delegatee: '', definitionId: '', reason: '' }
  range.value = []
  dialog.value = true
}

const save = async () => {
  if (!form.value.delegatee || range.value.length !== 2) {
    return ElMessage.warning('请选择受托人和有效时间')
  }
  await createWorkflowDelegation({
    ...form.value,
    startAt: range.value[0],
    endAt: range.value[1]
  })
  dialog.value = false
  ElMessage.success('委托规则已保存')
  await load()
}

const remove = async row => {
  await ElMessageBox.confirm('确认删除该委托规则？', '删除委托', { type: 'warning' })
  await deleteWorkflowDelegation(row.id)
  ElMessage.success('委托规则已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([
    load(),
    workflowDirectoryUsers().then(response => { users.value = response?.data || [] })
  ])
})
</script>

<style scoped>
.delegation-page { background: #fff; border-radius: 12px; padding: 24px; }
.title { display: flex; justify-content: space-between; align-items: center; }
.title h2 { margin: 0; }
.title p { color: #8492a6; margin: 8px 0 20px; }
</style>
