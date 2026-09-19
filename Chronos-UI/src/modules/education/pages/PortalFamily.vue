<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalFamilyChildren, portalFamilyNotices, portalFamilyGrades, portalFamilyReceipt } from '../../../api/portal'

const children = ref([])
const notices = ref([])
const grades = ref([])
const selectedChild = ref(null)
const loading = ref(false)
const unwrap = response => response?.data?.content || response?.data || []
async function load() {
  loading.value = true
  try {
    children.value = unwrap(await portalFamilyChildren())
    notices.value = unwrap(await portalFamilyNotices())
    grades.value = unwrap(await portalFamilyGrades())
    selectedChild.value = children.value[0] || null
  } finally { loading.value = false }
}
async function receipt(row) {
  await portalFamilyReceipt(row.id, { comment: row.receiptComment || '' })
  row.receiptStatus = 'RECEIVED'
  ElMessage.success('回执已提交')
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>家长门户</h1><p>仅展示当前账号绑定且仍具有效监护关系的学生。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-card class="children"><template #header>我的孩子</template><el-radio-group v-model="selectedChild"><el-radio-button v-for="child in children" :key="child.id" :label="child">{{ child.studentName }}</el-radio-button></el-radio-group><el-empty v-if="!children.length" description="暂无有效监护关系" /></el-card>
    <el-empty v-if="!loading && !notices.length" description="暂无班级通知" />
    <el-card v-for="item in notices" :key="item.id" class="notice">
      <template #header><span>{{ item.title }}</span><el-tag v-if="item.expired" type="info">已过期</el-tag></template>
      <p class="content">{{ item.content }}</p><small>发布于 {{ item.publishAt }}</small>
      <div v-if="item.receiptRequired" class="receipt"><el-tag v-if="item.receiptStatus === 'RECEIVED'" type="success">已回执</el-tag><el-button v-else :disabled="item.expired" type="primary" size="small" @click="receipt(item)">确认已读并回执</el-button></div>
    </el-card>
    <el-card class="grades"><template #header>已发布成绩</template>
      <el-table v-if="grades.length" :data="grades" size="small">
        <el-table-column prop="studentId" label="学生" />
        <el-table-column prop="totalScore" label="总评成绩" />
        <el-table-column prop="passed" label="是否通过"><template #default="{ row }">{{ row.passed ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="versionNo" label="发布版本" />
      </el-table>
      <el-empty v-else description="暂无已发布成绩" />
    </el-card>
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h1 { margin: 0 0 8px; }.children { margin-bottom: 20px; }.notice { margin-bottom: 14px; }.content { white-space: pre-wrap; line-height: 1.7; }.receipt { margin-top: 14px; }
.grades { margin-top: 20px; }
</style>
