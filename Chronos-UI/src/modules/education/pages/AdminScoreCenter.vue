<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAssessmentScheme,
  createGradebook,
  listAssessmentSchemes,
  listGradebooks,
  publishGradebook,
  submitGradebook,
  updateGradebookItems,
} from '../../../api/admin'

const schemes = ref([])
const gradebooks = ref([])
const loading = ref(false)
const schemeForm = ref({ name: '', offeringId: '', totalScore: 100, passScore: 60, components: [] })
const selected = ref(null)
const draftItems = ref([])
const activeTab = ref('gradebooks')
const statusLabel = { DRAFT: '草稿', EDITING: '录入中', SUBMITTED: '已提交', REVIEWING: '审核中', APPROVED: '已审核', PUBLISHED: '已发布' }
const selectedStatus = computed(() => statusLabel[selected.value?.status] || selected.value?.status || '')

async function refresh() {
  loading.value = true
  try {
    const [schemeResponse, gradebookResponse] = await Promise.all([
      listAssessmentSchemes(),
      listGradebooks(),
    ])
    schemes.value = schemeResponse?.data || []
    gradebooks.value = gradebookResponse?.data || []
  } finally {
    loading.value = false
  }
}

async function saveScheme() {
  if (!schemeForm.value.name || !schemeForm.value.offeringId) {
    ElMessage.warning('请填写方案名称和课程开设')
    return
  }
  await createAssessmentScheme({ ...schemeForm.value, components: schemeForm.value.components || [] })
  ElMessage.success('考核方案已保存')
  schemeForm.value = { name: '', offeringId: '', totalScore: 100, passScore: 60, components: [] }
  await refresh()
}

async function createNewGradebook() {
  if (!schemes.value.length) {
    ElMessage.warning('请先创建考核方案')
    return
  }
  await createGradebook({ schemeId: schemes.value[0].id, offeringId: schemes.value[0].offeringId })
  ElMessage.success('成绩册已创建')
  await refresh()
}

function selectGradebook(book) {
  selected.value = book
  draftItems.value = (book.items || []).map((item) => ({ ...item }))
}

async function saveItems() {
  if (!selected.value) return
  await updateGradebookItems(selected.value.id, {
    rowVersion: selected.value.rowVersion,
    items: draftItems.value,
  })
  ElMessage.success('成绩已保存')
  await refresh()
}

async function submit() {
  if (!selected.value) return
  await submitGradebook(selected.value.id, { rowVersion: selected.value.rowVersion })
  ElMessage.success('已提交审核')
  await refresh()
}

async function publish() {
  if (!selected.value) return
  await publishGradebook(selected.value.id, { rowVersion: selected.value.rowVersion })
  ElMessage.success('成绩册已发布')
  await refresh()
}

onMounted(refresh)
</script>

<template>
  <section class="score-center">
    <header class="page-header">
      <div>
        <h1>成绩中心</h1>
        <p>维护考核方案、人工录入成绩并完成提交审核与发布。</p>
      </div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="成绩册" name="gradebooks">
        <div class="toolbar">
          <el-button type="primary" @click="createNewGradebook">新建成绩册</el-button>
        </div>
        <el-table v-loading="loading" :data="gradebooks" stripe @row-click="selectGradebook">
          <el-table-column prop="name" label="成绩册" min-width="180" />
          <el-table-column prop="offeringName" label="课程开设" min-width="180" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ statusLabel[row.status] || row.status }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
        </el-table>
        <el-card v-if="selected" class="editor">
          <template #header>
            <div class="editor-header">
              <span>{{ selected.name || '成绩录入' }} · {{ selectedStatus }}</span>
              <span>
                <el-button @click="saveItems" :disabled="selected.status === 'PUBLISHED'">保存成绩</el-button>
                <el-button type="warning" @click="submit" :disabled="!['DRAFT', 'EDITING', 'REJECTED'].includes(selected.status)">提交审核</el-button>
                <el-button type="success" @click="publish" :disabled="selected.status !== 'APPROVED'">发布</el-button>
              </span>
            </div>
          </template>
          <el-table :data="draftItems" border>
            <el-table-column prop="studentName" label="学生" />
            <el-table-column prop="componentName" label="成绩项目" />
            <el-table-column label="成绩">
              <template #default="{ row }">
                <el-input-number v-model="row.rawScore" :min="0" :max="row.maxScore || 100" :disabled="selected.status === 'PUBLISHED'" />
              </template>
            </el-table-column>
            <el-table-column prop="specialStatus" label="特殊状态" />
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="考核方案" name="schemes">
        <el-form :model="schemeForm" inline>
          <el-form-item label="方案名称"><el-input v-model="schemeForm.name" placeholder="如：2026春季课程考核" /></el-form-item>
          <el-form-item label="课程开设"><el-input v-model="schemeForm.offeringId" placeholder="Offering ID" /></el-form-item>
          <el-form-item label="总分"><el-input-number v-model="schemeForm.totalScore" :min="1" /></el-form-item>
          <el-form-item label="及格分"><el-input-number v-model="schemeForm.passScore" :min="0" /></el-form-item>
          <el-form-item><el-button type="primary" @click="saveScheme">保存方案</el-button></el-form-item>
        </el-form>
        <el-table :data="schemes" stripe>
          <el-table-column prop="name" label="方案名称" />
          <el-table-column prop="offeringId" label="课程开设" />
          <el-table-column prop="totalScore" label="总分" />
          <el-table-column prop="passScore" label="及格分" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.score-center { padding: 24px; }
.page-header, .editor-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.page-header { margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; }
.page-header p { margin: 0; color: var(--el-text-color-secondary); }
.toolbar { margin-bottom: 16px; }
.editor { margin-top: 20px; }
</style>
