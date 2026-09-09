<template>
  <article v-loading="loading" class="detail">
    <button class="back" @click="router.back()">← 返回通知公告</button>
    <template v-if="item">
      <header>
        <div class="badges">
          <span>{{ item.publicationType === 'NOTICE' ? '通知' : '公告' }}</span>
          <span v-if="item.mustRead" class="important">必读</span>
          <span v-if="item.importance !== 'NORMAL'" class="important">{{ item.importance }}</span>
        </div>
        <h1>{{ item.title }}</h1>
        <p>发布于 {{ formatDate(item.publishedAt) }} · {{ item.createBy }}</p>
      </header>
      <p v-if="item.summary" class="summary">{{ item.summary }}</p>
      <!-- 后端已执行严格白名单清洗，门户只渲染清洗后的富文本。 -->
      <section v-if="item.content" class="content" v-html="item.content"></section>
      <section v-else class="content content--empty">正文请查看主文档</section>
      <div v-if="primaryDocument" class="document-preview">
        <el-button type="primary" plain @click="preview(primaryDocument)">
          {{ primaryDocument.contentType === 'application/pdf' ? '在线预览 PDF' : '下载原始 Word 文档' }}
        </el-button>
      </div>
      <div v-if="item.mustRead && !item.read" class="read-confirm">
        <el-button type="primary" @click="confirmRead">我已阅读并确认</el-button>
      </div>
      <section v-if="item.attachments?.length" class="attachments">
        <h3>附件</h3>
        <button v-for="attachment in item.attachments" :key="attachment.id" @click="download(attachment)">
          <span>{{ attachment.originalName }}</span>
          <small>{{ formatSize(attachment.fileSize) }} · 下载</small>
        </button>
      </section>
    </template>
  </article>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  downloadPortalPublicationAttachment,
  portalPublicationDetail,
  readPortalPublication,
} from '../../../api/portal'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const primaryDocument = computed(() => item.value?.attachments?.find(attachment => attachment.primaryContent))
const loading = ref(false)
const load = async () => {
  loading.value = true
  try {
    const response = await portalPublicationDetail(route.params.id)
    item.value = response.data
  } finally {
    loading.value = false
  }
}
const download = async attachment => {
  const blob = await downloadPortalPublicationAttachment(attachment.id)
  const url = URL.createObjectURL(blob)
  if (attachment.contentType === 'application/pdf') {
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    return
  }
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = attachment.originalName
  anchor.click()
  URL.revokeObjectURL(url)
}
const preview = attachment => download(attachment)
const confirmRead = async () => {
  await readPortalPublication(item.value.id)
  item.value.read = true
  ElMessage.success('阅读回执已提交')
}
const formatDate = value => value ? value.replace('T', ' ').slice(0, 16) : ''
const formatSize = bytes => bytes > 1024 * 1024
  ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
  : `${Math.ceil(bytes / 1024)} KB`
onMounted(load)
</script>

<style scoped>
.detail { max-width: 900px; min-height: 500px; margin: 0 auto; padding: 32px 28px 70px; }
.back { margin-bottom: 28px; border: 0; background: transparent; color: #287c72; cursor: pointer; }
header { padding-bottom: 24px; border-bottom: 1px solid #e7ebed; text-align: center; }
.badges { display: flex; justify-content: center; gap: 8px; }
.badges span { padding: 4px 9px; border-radius: 12px; background: #eaf6f3; color: #217a6e; font-size: 11px; }
.badges span.important { background: #fff0ed; color: #bd503b; }
header h1 { margin: 15px 0 10px; color: #19313e; font-size: 28px; }
header p { margin: 0; color: #8a969d; font-size: 12px; }
.summary { margin: 24px 0; padding: 16px 20px; border-left: 3px solid #3b9487; background: #f4f9f8; color: #5c6d75; }
.content { min-height: 160px; padding: 8px 4px 28px; overflow-wrap: anywhere; color: #344a55; line-height: 1.9; }
.content :deep(table) { width: 100%; border-collapse: collapse; }
.content :deep(td), .content :deep(th) { padding: 7px; border: 1px solid #dfe5e8; }
.content :deep(img) { max-width: 100%; height: auto; }
.content--empty { color: #8a969d; text-align: center; }
.document-preview { display: flex; justify-content: center; padding: 0 0 24px; }
.read-confirm { display: flex; justify-content: center; padding: 20px; border-top: 1px solid #e7ebed; }
.attachments { margin-top: 20px; padding-top: 20px; border-top: 1px solid #e7ebed; }
.attachments > button { display: flex; width: 100%; justify-content: space-between; padding: 12px 14px; border: 1px solid #e6ebed; border-radius: 8px; background: #fff; color: #344a55; cursor: pointer; }
.attachments small { color: #2c8176; }
</style>
