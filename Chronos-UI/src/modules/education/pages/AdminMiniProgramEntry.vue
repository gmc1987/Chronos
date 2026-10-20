<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createMiniProgramSchoolEntry,
  downloadMiniProgramSchoolEntryCode,
  listMiniProgramSchools,
} from '../../../api/admin'

const entry = ref(null)
const imageUrl = ref('')
const loading = ref(false)
const imageError = ref('')
const schools = ref([])
const organizationId = ref('')

function releaseImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}

async function load() {
  if (!organizationId.value) return
  loading.value = true
  imageError.value = ''
  try {
    const response = await createMiniProgramSchoolEntry({ organizationId: organizationId.value })
    entry.value = response?.data || null
    releaseImage()
    try {
      const blob = await downloadMiniProgramSchoolEntryCode(organizationId.value)
      imageUrl.value = URL.createObjectURL(blob)
    } catch (error) {
      imageError.value = error?.message || '小程序码生成失败'
    }
  } finally {
    loading.value = false
  }
}

async function initialize() {
  const response = await listMiniProgramSchools()
  schools.value = response?.data || []
  organizationId.value = schools.value[0]?.id || ''
  if (organizationId.value) await load()
}

async function download() {
  if (!imageUrl.value || !entry.value) return
  const link = document.createElement('a')
  link.href = imageUrl.value
  link.download = `${entry.value.school?.name || '学校'}-微信小程序入口.png`
  link.click()
  ElMessage.success('小程序码已下载')
}

onMounted(initialize)
onBeforeUnmount(releaseImage)
</script>

<template>
  <div class="mini-program-page">
    <header class="page-header">
      <div>
        <h2>微信小程序入口</h2>
        <p>生成学校长期有效的小程序入口码，教师和学生首次扫码后绑定已有平台账号。</p>
      </div>
      <div class="header-actions">
        <el-select v-model="organizationId" filterable placeholder="请选择学校" style="width: 260px" @change="load">
          <el-option v-for="school in schools" :key="school.id" :label="school.name" :value="school.id" />
        </el-select>
        <el-button type="primary" :loading="loading" :disabled="!organizationId" @click="load">重新加载</el-button>
      </div>
    </header>

    <el-card v-loading="loading" class="entry-card" shadow="never">
      <template #header><strong>{{ entry?.school?.name || '当前学校' }}</strong></template>
      <div class="entry-content">
        <div class="code-panel">
          <img v-if="imageUrl" :src="imageUrl" alt="学校微信小程序入口码" />
          <el-empty v-else :description="imageError || '正在生成小程序码'" :image-size="96" />
        </div>
        <div class="entry-info">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="学校名称">{{ entry?.school?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学校编码">{{ entry?.school?.code || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入口场景码">{{ entry?.scene || '-' }}</el-descriptions-item>
            <el-descriptions-item label="小程序页面">{{ entry?.page || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生成时间">{{ entry?.generatedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="imageError"
            :title="imageError"
            type="warning"
            :closable="false"
            show-icon
          />
          <div class="actions">
            <el-button type="primary" :disabled="!imageUrl" @click="download">下载小程序码</el-button>
          </div>
          <p class="tip">可将此码放到学校公众号、官网、班级群和入学材料中。入口码只确定学校，绑定仍需核验学校预留手机号。</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.mini-program-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.page-header h2 { margin: 0 0 6px; }
.page-header p, .tip { margin: 0; color: #84909a; }
.header-actions { display:flex; align-items:center; gap:12px; }
.entry-card { border-radius: 14px; }
.entry-content { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 28px; align-items: start; }
.code-panel { min-height: 300px; display: flex; align-items: center; justify-content: center; border-radius: 16px; background: #f4f7f8; }
.code-panel img { width: 280px; height: 280px; object-fit: contain; }
.entry-info { display: flex; flex-direction: column; gap: 16px; }
.actions { display: flex; justify-content: flex-end; }
@media (max-width: 760px) { .entry-content { grid-template-columns: 1fr; } }
</style>
