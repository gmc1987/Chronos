<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdministrativeClasses, pageClassGroups, createClassGroup, changeClassGroupStatus, syncClassGroup, pageClassGroupMembers, pageClassGroupAudit } from '../../../api/admin'

const groups = ref([]), classes = ref([]), members = ref([]), audits = ref([])
const selected = ref(null), loading = ref(false), dialog = ref(false)
const form = ref({ classId: '', name: '' }), page = ref(0), total = ref(0)
const unwrap = r => r?.data?.content || r?.data || []
async function load() {
  loading.value = true
  try {
    const [groupResponse, classResponse] = await Promise.all([pageClassGroups({ page: page.value, size: 20 }), listAdministrativeClasses({ page: 0, size: 200 })])
    groups.value = unwrap(groupResponse); total.value = groupResponse?.data?.totalElements || groups.value.length
    classes.value = unwrap(classResponse)
  } finally { loading.value = false }
}
async function select(row) {
  selected.value = row
  const [memberResponse, auditResponse] = await Promise.all([pageClassGroupMembers(row.id), pageClassGroupAudit(row.id)])
  members.value = unwrap(memberResponse); audits.value = unwrap(auditResponse)
}
async function save() {
  await createClassGroup(form.value, `class-group:${form.value.classId}`)
  dialog.value = false; ElMessage.success('班级群已创建'); await load()
}
async function sync(row) { await syncClassGroup(row.id, { idempotencyKey: `sync:${row.id}:${Date.now()}` }); ElMessage.success('成员已按有效班级和监护关系同步'); await select(row); await load() }
async function toggle(row) {
  const status = row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  await changeClassGroupStatus(row.id, { status, rowVersion: row.rowVersion }); ElMessage.success('群状态已更新'); await load()
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>班级群管理</h2><p>按真实行政班维护班主任、在籍学生和有效监护人群成员。</p></div><el-button type="primary" @click="dialog = true">创建班级群</el-button></header>
    <el-table v-loading="loading" :data="groups" border @row-click="select">
      <el-table-column prop="className" label="班级" /><el-table-column prop="name" label="群名称" />
      <el-table-column prop="status" label="状态" /><el-table-column prop="memberCount" label="有效成员数" />
      <AdaptiveActionColumn label="操作" width="220"><template #default="{ row }"><el-button link @click.stop="sync(row)">同步成员</el-button><el-button link @click.stop="toggle(row)">{{ row.status === 'ACTIVE' ? '暂停' : '恢复' }}</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="20" @current-change="load" />
    <el-card v-if="selected" class="detail"><template #header>{{ selected.className }} · 成员与审计</template>
      <el-tabs><el-tab-pane label="成员"><el-table :data="members" size="small"><el-table-column prop="memberType" label="类型" /><el-table-column prop="memberId" label="对象ID" /><el-table-column prop="username" label="账号" /><el-table-column prop="source" label="来源" /><el-table-column prop="status" label="状态" /></el-table></el-tab-pane>
      <el-tab-pane label="审计"><el-table :data="audits" size="small"><el-table-column prop="action" label="动作" /><el-table-column prop="memberType" label="类型" /><el-table-column prop="memberId" label="对象ID" /><el-table-column prop="actor" label="操作者" /><el-table-column prop="createTime" label="时间" /></el-table></el-tab-pane></el-tabs>
    </el-card>
    <el-dialog v-model="dialog" title="创建班级群" width="520px"><el-form label-width="90px"><el-form-item label="行政班"><el-select v-model="form.classId" filterable><el-option v-for="item in classes" :key="item.id" :label="`${item.className} (${item.classCode})`" :value="item.id" /></el-select></el-form-item><el-form-item label="群名称"><el-input v-model="form.name" maxlength="200" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; } header { display:flex; justify-content:space-between; align-items:center; margin-bottom:18px; } h2 { margin:0 0 6px; } p { margin:0; color:#84909a; } .detail { margin-top:20px; } .el-pagination { margin-top:16px; justify-content:flex-end; }
</style>
