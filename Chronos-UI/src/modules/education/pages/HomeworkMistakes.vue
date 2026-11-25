<template>
  <section class="mistakes">
    <h1>{{ manage ? '教学班错题统计' : '我的错题' }}</h1>
    <el-form inline @submit.prevent="load">
      <el-form-item label="教学班">
        <el-select v-model="offeringId" filterable placeholder="选择教学班" @change="load">
          <el-option v-for="item in offerings" :key="item.id" :value="item.id"
            :label="[item.courseName, item.teachingClassName].join(' · ')" />
        </el-select>
      </el-form-item>
      <el-button @click="load">查询</el-button>
      <el-button v-if="!manage" type="primary" :disabled="!offeringId" @click="visible = true">记录错题</el-button>
    </el-form>
    <el-descriptions v-if="manage && summary" :column="2" border>
      <el-descriptions-item label="错题记录">{{ summary.total }}</el-descriptions-item>
      <el-descriptions-item label="已掌握">{{ summary.mastered }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="!manage" v-loading="loading" :data="rows" stripe border empty-text="暂无错题">
      <el-table-column label="错题内容" min-width="240">
        <template #default="{ row }">{{ row.studentNote || row.analysis || '作业错题' }}</template>
      </el-table-column>
      <el-table-column label="掌握状态" width="120">
        <template #default="{ row }">{{ row.masteryStatus === 'MASTERED' ? '已掌握' : '待练习' }}</template>
      </el-table-column>
      <AdaptiveActionColumn label="操作">
        <template #default="{ row }">
          <el-button link @click="mark(row)">{{ row.masteryStatus === 'MASTERED' ? '恢复待练习' : '标记掌握' }}</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="visible" title="记录错题" width="min(560px, 95vw)">
      <el-input v-model="note" aria-label="错题内容" type="textarea" :rows="5" maxlength="2000" show-word-limit />
      <template #footer>
        <el-button type="primary" :disabled="!note.trim()" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { hasAdminPermission } from '../../../store/auth'
import { addHomeworkMistake, homeworkMistakeSummary, homeworkOfferingOptions,
  personalHomeworkMistakes, updateHomeworkMastery } from '../api/teachingCenter'

const manage = hasAdminPermission('education:homework:manage')
const offerings = ref([])
const offeringId = ref('')
const rows = ref([])
const summary = ref(null)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const note = ref('')

async function load() {
  const id = offeringId.value
  if (!id) return
  loading.value = true
  try {
    const read = manage ? homeworkMistakeSummary : personalHomeworkMistakes
    const { data } = await read(id)
    if (id !== offeringId.value) return
    if (manage) summary.value = data
    else rows.value = data || []
  } finally {
    if (id === offeringId.value) loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await addHomeworkMistake(offeringId.value, note.value.trim())
    visible.value = false
    note.value = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function mark(row) {
  await updateHomeworkMastery(offeringId.value, row.id, row.masteryStatus !== 'MASTERED')
  await load()
}

onMounted(async () => {
  offerings.value = (await homeworkOfferingOptions()).data || []
  offeringId.value = offerings.value[0]?.id || ''
  await load()
})
</script>

<style scoped>
.mistakes { padding: 24px; }
.mistakes .el-select { width: 320px; }
</style>
