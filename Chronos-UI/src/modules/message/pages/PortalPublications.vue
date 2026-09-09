<template>
  <div class="publication-page">
    <header>
      <div>
        <span>INFORMATION CENTER</span>
        <h1>通知公告</h1>
        <p>查看与你所在机构、部门、岗位和角色相关的信息</p>
      </div>
      <div class="header-actions">
        <el-button @click="openPreferences">接收偏好</el-button>
        <el-segmented v-model="type" :options="typeOptions" @change="search" />
      </div>
    </header>
    <div class="publication-filter">
      <el-input v-model="keyword" clearable placeholder="搜索标题或摘要" @keyup.enter="search" />
      <el-checkbox v-model="unreadOnly" @change="search">只看未读</el-checkbox>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <section v-loading="loading" class="publication-list">
      <button v-for="item in rows" :key="item.id" @click="router.push(`/portal/publications/${item.id}`)">
        <i :class="{ unread: !item.read }"></i>
        <span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.summary || '暂无摘要' }}</small>
        </span>
        <em v-if="item.pinned">置顶</em>
        <em v-if="item.mustRead" class="must-read">必读</em>
        <time>{{ formatDate(item.publishedAt) }}</time>
      </button>
      <el-empty v-if="!loading && !rows.length" description="暂无可见内容" />
    </section>
    <el-pagination
      v-model:current-page="currentPage"
      :page-size="pageSize"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="load"
    />
    <el-dialog v-model="preferenceDialog" title="通知接收偏好" width="720px">
      <el-table :data="preferences">
        <el-table-column prop="channel" label="渠道" width="110" />
        <el-table-column label="接收" width="90"><template #default="scope"><el-switch v-model="scope.row.enabled" :disabled="scope.row.channel === 'IN_APP'" /></template></el-table-column>
        <el-table-column label="免打扰开始"><template #default="scope"><el-time-picker v-model="scope.row.quietStart" value-format="HH:mm:ss" placeholder="未设置" /></template></el-table-column>
        <el-table-column label="免打扰结束"><template #default="scope"><el-time-picker v-model="scope.row.quietEnd" value-format="HH:mm:ss" placeholder="未设置" /></template></el-table-column>
        <el-table-column label="每日上限"><template #default="scope"><el-input-number v-model="scope.row.dailyLimit" :min="1" :max="1000" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="preferenceDialog = false">取消</el-button><el-button type="primary" @click="savePreferences">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationChannelPreferences, portalPublications, saveNotificationChannelPreference } from '../../../api/portal'

const route = useRoute()
const router = useRouter()
const typeOptions = [
  { label: '全部', value: '' },
  { label: '通知', value: 'NOTICE' },
  { label: '公告', value: 'ANNOUNCEMENT' },
]
const type = ref(String(route.query.type || ''))
const keyword = ref(String(route.query.keyword || ''))
const unreadOnly = ref(route.query.unreadOnly === 'true')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const preferenceDialog = ref(false)
const preferences = ref([])
const channels = ['IN_APP', 'EMAIL', 'SMS', 'WE_COM']
const rows = ref([])
const loading = ref(false)
const load = async () => {
  loading.value = true
  try {
    const params = {
      type: type.value,
      keyword: keyword.value,
      unreadOnly: unreadOnly.value,
      page: currentPage.value - 1,
      size: pageSize,
    }
    const response = await portalPublications(params)
    rows.value = response.data?.content || []
    total.value = response.data?.totalElements || 0
    router.replace({ query: Object.fromEntries(
      Object.entries(params).filter(([key, value]) => !['page', 'size'].includes(key) && value),
    ) })
  } finally {
    loading.value = false
  }
}
const search = () => {
  currentPage.value = 1
  load()
}
const openPreferences = async () => {
  const response = await notificationChannelPreferences()
  const configured = new Map((response.data || []).map(item => [item.channel, item]))
  preferences.value = channels.map(channel => configured.get(channel) || {
    channel,
    enabled: channel === 'IN_APP',
    quietStart: null,
    quietEnd: null,
    dailyLimit: null,
  })
  preferenceDialog.value = true
}
const savePreferences = async () => {
  await Promise.all(preferences.value.map(item => saveNotificationChannelPreference(item.channel, item)))
  preferenceDialog.value = false
  ElMessage.success('接收偏好已保存')
}
const formatDate = value => value ? value.replace('T', ' ').slice(0, 16) : ''
onMounted(load)
</script>

<style scoped>
.publication-page { max-width: 1080px; margin: 0 auto; padding: 34px 26px; }
header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 24px; }
header span { color: #1d8073; font-size: 11px; letter-spacing: 2px; }
header h1 { margin: 7px 0; color: #17313e; }
header p { margin: 0; color: #7b8991; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.publication-filter { display: grid; grid-template-columns: minmax(220px, 1fr) auto auto; gap: 12px; align-items: center; margin-bottom: 16px; }
.publication-list { overflow: hidden; min-height: 240px; border: 1px solid #e8ecee; border-radius: 14px; background: #fff; }
.publication-list > button { display: flex; width: 100%; align-items: center; gap: 12px; padding: 18px 22px; border: 0; border-bottom: 1px solid #edf0f1; background: #fff; text-align: left; cursor: pointer; }
.publication-list > button:hover { background: #f8fbfa; }
.publication-list i { width: 7px; height: 7px; flex: none; border-radius: 50%; background: #d9dfe1; }
.publication-list i.unread { background: #e2674c; }
.publication-list span { min-width: 0; flex: 1; display: grid; gap: 5px; }
.publication-list strong { overflow: hidden; color: #263d49; text-overflow: ellipsis; white-space: nowrap; }
.publication-list small { overflow: hidden; color: #89959b; text-overflow: ellipsis; white-space: nowrap; }
.publication-list em { padding: 3px 7px; border-radius: 10px; background: #edf7f4; color: #24796d; font-size: 11px; font-style: normal; }
.publication-list em.must-read { background: #fff0ed; color: #bd503b; }
.publication-list time { width: 130px; color: #9aa4a9; font-size: 12px; text-align: right; }
.el-pagination { justify-content: flex-end; margin-top: 18px; }
</style>
