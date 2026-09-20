<template>
  <div class="class-notices">
    <el-page-header @back="$router.push('/portal')"
      ><template #content><strong>班级通知</strong></template></el-page-header
    ><el-skeleton v-if="loading" :rows="6" animated /><el-empty
      v-else-if="!rows.length"
      description="暂无班级通知"
    /><el-card v-for="row in rows" v-else :key="row.recipient.id" shadow="never"
      ><template #header
        ><div class="header">
          <strong>{{ row.notice.title }}</strong
          ><el-tag :type="row.recipient.acknowledgedAt ? 'success' : 'warning'">{{
            row.recipient.acknowledgedAt ? '已回执' : '待回执'
          }}</el-tag>
        </div></template
      >
      <p>{{ row.notice.content }}</p>
      <small>发布时间：{{ row.notice.publishedAt || '-' }}　回执截止：{{ row.notice.receiptDeadline || '不限' }}</small>
      <div v-if="row.notice.requireReceipt && !row.recipient.acknowledgedAt" class="action">
        <el-input v-model="comments[row.notice.id]" placeholder="回执说明（可选）" /><el-button
          type="primary"
          @click="ack(row.notice.id)"
          >确认收到</el-button
        >
      </div></el-card
    >
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { acknowledgePortalClassNotice, portalParentClassNotices } from '../../../api/portal'
const rows = ref([]),
  loading = ref(true),
  comments = reactive({})
const load = async () => {
  loading.value = true
  try {
    rows.value = (await portalParentClassNotices()).data || []
  } finally {
    loading.value = false
  }
}
const ack = async (id) => {
  await acknowledgePortalClassNotice(id, comments[id] || '')
  ElMessage.success('回执已提交')
  await load()
}
onMounted(load)
</script>
<style scoped>
.class-notices {
  display: grid;
  gap: 16px;
  padding: 24px;
}
.header,
.action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.class-notices p {
  white-space: pre-wrap;
  line-height: 1.7;
}
.class-notices small {
  color: #819097;
}
.action {
  margin-top: 16px;
}
.action .el-input {
  max-width: 420px;
}
</style>
