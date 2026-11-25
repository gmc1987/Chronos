<template>
  <el-button type="primary" @click="open">教学内容审核</el-button>
  <el-dialog v-model="visible" title="教学内容审核" width="min(840px, 95vw)" @closed="selected = null">
    <el-table v-loading="loading" :data="rows" row-key="id" max-height="340" empty-text="暂无待审核内容">
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ types[row.type] }}</template>
      </el-table-column>
      <el-table-column prop="title" label="名称" min-width="160" />
      <el-table-column prop="submitter" label="提交人" min-width="130" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }"><el-button link type="primary" @click="select(row)">核对内容</el-button></template>
      </el-table-column>
    </el-table>
    <div v-if="selected" class="review-detail">
      <h3>{{ selected.title }}</h3>
      <dl v-if="details.length">
        <template v-for="[label, value] in details" :key="label">
          <dt>{{ label }}</dt><dd>{{ value }}</dd>
        </template>
      </dl>
      <el-button v-if="selected.fileId" link type="primary" @click="viewFile">查看附件</el-button>
      <el-input v-model="comment" type="textarea" maxlength="2000" show-word-limit
        placeholder="审核意见，退回时必填" :rows="3" aria-label="审核意见" />
      <div class="review-actions">
        <el-button type="danger" :loading="saving" :disabled="!comment.trim()" @click="decide(false)">退回修改</el-button>
        <el-button type="primary" :loading="saving" @click="decide(true)">审核通过并发布</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { decideTeachingProductReview, downloadTeachingAttachment, pendingTeachingProductReviews } from '../api/teachingCenter'

const types = { PLAN: '教学计划', LESSON_PLAN: '教案', PREPARATION: '备课', COURSEWARE: '课件',
  MATERIAL: '教学材料', QUESTION: '题目', RESEARCH_RESULT: '教研成果' }
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const selected = ref(null)
const comment = ref('')
const details = computed(() => {
  const content = selected.value?.content
  if (!content) return []
  if (!['PLAN', 'LESSON_PLAN', 'QUESTION'].includes(selected.value.type)) return [['内容', content]]
  try {
    const snapshot = JSON.parse(content)
    if (selected.value.type === 'PLAN') {
      return [['教学目标', snapshot.plan?.objective], ['考核方式', snapshot.plan?.assessmentMethod],
        ['章节', snapshot.items?.map(item => item.chapterName).join('、')]].filter(([, value]) => value)
    }
    return [['内容', snapshot.content || snapshot.stem || snapshot.objectives],
      ['选项', snapshot.options?.map(option => option.optionKey + '. ' + option.optionText).join('\n')],
      ['参考答案', snapshot.answer], ['教学重点', snapshot.keyPoints], ['解析', snapshot.analysis]]
      .filter(([, value]) => value)
  } catch {
    return [['内容', content]]
  }
})

async function open() {
  visible.value = true
  await load()
}

async function load() {
  loading.value = true
  try {
    rows.value = (await pendingTeachingProductReviews()).data || []
  } finally {
    loading.value = false
  }
}

function select(row) {
  selected.value = row
  comment.value = ''
}

async function decide(approved) {
  saving.value = true
  try {
    await decideTeachingProductReview(selected.value.id, { approved, comment: comment.value.trim() })
    ElMessage.success(approved ? '已审核并发布' : '已退回修改')
    selected.value = null
    await load()
  } finally {
    saving.value = false
  }
}

async function viewFile() {
  const blob = await downloadTeachingAttachment(selected.value.fileId)
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}
</script>

<style scoped>
.review-detail { border-top: 1px solid var(--el-border-color); margin: 16px 0 0; padding-top: 12px; }
.review-detail dl { display: grid; grid-template-columns: 100px 1fr; gap: 6px 12px; }
.review-detail dd { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
.review-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
</style>
