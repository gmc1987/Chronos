<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  askEducationAssistant,
  createKnowledgeBase,
  createKnowledgeTextDocument,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  importKnowledgeDocument,
  listKnowledgeBases,
  listKnowledgeDocuments,
  rebuildKnowledgeDocument,
  searchKnowledge,
  updateKnowledgeBase,
} from '../../../api/admin'

const bases = ref([])
const documents = ref([])
const searchResults = ref([])
const activeBaseId = ref('')
const keyword = ref('')
const baseDialog = ref(false)
const textDialog = ref(false)
const importDialog = ref(false)
const uploadFile = ref(null)
const baseForm = reactive({})
const documentForm = reactive({ title: '', content: '' })
const importTitle = ref('')
const assistantQuestion = ref('')
const assistantLoading = ref(false)
const assistantAnswer = ref(null)
const basePage = ref(1)
const basePageSize = ref(10)
const baseTotal = ref(0)
const documentPage = ref(1)
const documentPageSize = ref(10)
const documentTotal = ref(0)
const activeBase = computed(() => bases.value.find(item => item.id === activeBaseId.value))

const resetObject = (target, value) => {
  Object.keys(target).forEach(key => delete target[key])
  Object.assign(target, value)
}

const loadBases = async () => {
  const response = await listKnowledgeBases({ page: basePage.value - 1, size: basePageSize.value })
  bases.value = response.data?.content || response.data || []
  baseTotal.value = response.data?.totalElements ?? bases.value.length
  if (!bases.value.some(item => item.id === activeBaseId.value)) {
    activeBaseId.value = bases.value[0]?.id || ''
  }
  await loadDocuments()
}

const loadDocuments = async () => {
  searchResults.value = []
  if (!activeBaseId.value) {
    documents.value = []
    return
  }
  const response = await listKnowledgeDocuments(activeBaseId.value, {
    page: documentPage.value - 1,
    size: documentPageSize.value,
  })
  documents.value = response.data?.content || response.data || []
  documentTotal.value = response.data?.totalElements ?? documents.value.length
}
const changeDocumentPageSize = () => { documentPage.value = 1; loadDocuments() }

const openCreateBase = () => {
  resetObject(baseForm, { enabled: true })
  baseDialog.value = true
}

const openEditBase = () => {
  resetObject(baseForm, { ...activeBase.value })
  baseDialog.value = true
}

const saveBase = async () => {
  if (baseForm.id) {
    await updateKnowledgeBase(baseForm.id, baseForm)
  } else {
    await createKnowledgeBase(baseForm)
  }
  baseDialog.value = false
  ElMessage.success('知识库保存成功')
  await loadBases()
}

const removeBase = async () => {
  await ElMessageBox.confirm('删除前必须先清空知识库内的文档，确认继续？', '删除知识库', { type: 'warning' })
  await deleteKnowledgeBase(activeBaseId.value)
  ElMessage.success('知识库已删除')
  await loadBases()
}

const openTextDocument = () => {
  resetObject(documentForm, { title: '', content: '' })
  textDialog.value = true
}

const saveTextDocument = async () => {
  await createKnowledgeTextDocument({
    knowledgeBaseId: activeBaseId.value,
    title: documentForm.title,
    content: documentForm.content,
  })
  textDialog.value = false
  ElMessage.success('文本已入库并完成分段')
  await loadDocuments()
}

const chooseFile = file => {
  uploadFile.value = file.raw
  if (!importTitle.value) {
    importTitle.value = file.name.replace(/\.[^.]+$/, '')
  }
}

const upload = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文档')
    return
  }
  await importKnowledgeDocument(activeBaseId.value, importTitle.value, uploadFile.value)
  importDialog.value = false
  uploadFile.value = null
  importTitle.value = ''
  ElMessage.success('文档已导入并完成分段')
  await loadDocuments()
}

const removeDocument = async row => {
  await ElMessageBox.confirm(`确认删除“${row.title}”及全部检索分段？`, '删除文档', { type: 'warning' })
  await deleteKnowledgeDocument(row.id)
  ElMessage.success('文档已删除')
  await loadDocuments()
}

const rebuildDocument = async row => {
  await rebuildKnowledgeDocument(row.id)
  ElMessage.success('文档分段索引已重建')
  await loadDocuments()
}

const search = async () => {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入检索关键词')
    return
  }
  const response = await searchKnowledge(activeBaseId.value, keyword.value.trim())
  searchResults.value = response.data || []
}

const askAssistant = async () => {
  if (!assistantQuestion.value.trim()) {
    ElMessage.warning('请输入需要咨询的问题')
    return
  }
  assistantLoading.value = true
  try {
    const response = await askEducationAssistant({
      knowledgeBaseId: activeBaseId.value,
      question: assistantQuestion.value.trim(),
    })
    assistantAnswer.value = response.data
  } finally {
    assistantLoading.value = false
  }
}

onMounted(loadBases)
</script>

<template>
  <div class="knowledge-page">
    <header>
      <div>
        <h2>AI 知识库</h2>
        <p>管理校园制度与教务知识，检索结果保留文档及分段来源。</p>
      </div>
      <el-button type="primary" @click="openCreateBase">新建知识库</el-button>
    </header>

    <el-row :gutter="18">
      <el-col :span="6">
        <el-card shadow="never">
          <template #header>知识库</template>
          <el-empty v-if="!bases.length" description="暂无知识库" />
          <div
            v-for="item in bases"
            :key="item.id"
            class="base-item"
            :class="{ active: activeBaseId === item.id }"
            @click="activeBaseId = item.id; documentPage = 1; loadDocuments()"
          >
            <strong>{{ item.baseName }}</strong>
            <small>{{ item.baseCode }}</small>
          </div>
          <el-pagination
            v-model:current-page="basePage"
            v-model:page-size="basePageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            :total="baseTotal"
            @current-change="loadBases"
          />
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card v-if="activeBase" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ activeBase.baseName }}</span>
              <div>
                <el-button @click="openEditBase">编辑</el-button>
                <el-button type="danger" plain @click="removeBase">删除</el-button>
              </div>
            </div>
          </template>

          <el-tabs>
            <el-tab-pane label="知识文档">
              <div class="toolbar">
                <el-button type="primary" @click="openTextDocument">录入文本</el-button>
                <el-button @click="importDialog = true">导入文档</el-button>
                <span>支持 TXT、Markdown、DOC、DOCX、文本型 PDF，单文件最大 10MB</span>
              </div>
              <el-table :data="documents" border>
                <el-table-column prop="title" label="标题" min-width="180" />
                <el-table-column prop="originalFilename" label="来源文件" min-width="180" />
                <el-table-column prop="sourceType" label="来源" width="90" />
                <el-table-column prop="chunkCount" label="分段数" width="90" />
                <el-table-column prop="status" label="状态" width="90" />
                <el-table-column label="操作" width="150">
                  <template #default="scope">
                    <el-button link type="primary" @click="rebuildDocument(scope.row)">重建索引</el-button>
                    <el-button link type="danger" @click="removeDocument(scope.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                v-model:current-page="documentPage"
                v-model:page-size="documentPageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="documentTotal"
                @size-change="changeDocumentPageSize"
                @current-change="loadDocuments"
              />
            </el-tab-pane>

            <el-tab-pane label="检索验证">
              <div class="search-bar">
                <el-input v-model="keyword" clearable placeholder="输入制度、课程或业务关键词" @keyup.enter="search" />
                <el-button type="primary" @click="search">检索</el-button>
              </div>
              <el-empty v-if="!searchResults.length" description="暂无检索结果" />
              <el-card v-for="item in searchResults" :key="`${item.documentId}-${item.chunkIndex}`" class="result" shadow="never">
                <strong>{{ item.documentTitle }} · 第 {{ item.chunkIndex }} 段</strong>
                <p>{{ item.content }}</p>
                <small>引用：{{ item.originalFilename || item.documentTitle }}</small>
              </el-card>
            </el-tab-pane>

            <el-tab-pane label="教务助手">
              <el-alert
                title="助手只依据当前知识库回答，所有结论应附带下方来源；业务操作仍需人工确认。"
                type="info"
                :closable="false"
                show-icon
              />
              <div class="search-bar assistant-bar">
                <el-input
                  v-model="assistantQuestion"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：学生因病不能上课时，应当如何请假？"
                />
                <el-button type="primary" :loading="assistantLoading" @click="askAssistant">提问</el-button>
              </div>
              <el-card v-if="assistantAnswer" shadow="never">
                <div class="answer">{{ assistantAnswer.answer }}</div>
                <el-divider content-position="left">回答依据</el-divider>
                <div
                  v-for="(item, index) in assistantAnswer.references"
                  :key="`${item.documentId}-${item.chunkIndex}`"
                  class="citation"
                >
                  [资料{{ index + 1 }}] {{ item.documentTitle }} · 第 {{ item.chunkIndex }} 段
                </div>
                <el-tag :type="assistantAnswer.modelGenerated ? 'success' : 'warning'">
                  {{ assistantAnswer.modelGenerated ? '模型生成' : '未调用模型' }}
                </el-tag>
              </el-card>
            </el-tab-pane>
          </el-tabs>
        </el-card>
        <el-empty v-else description="请先创建知识库" />
      </el-col>
    </el-row>

    <el-dialog v-model="baseDialog" :title="baseForm.id ? '编辑知识库' : '新建知识库'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="知识库编码"><el-input v-model="baseForm.baseCode" /></el-form-item>
        <el-form-item label="知识库名称"><el-input v-model="baseForm.baseName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="baseForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="baseForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="baseDialog = false">取消</el-button><el-button type="primary" @click="saveBase">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="textDialog" title="录入知识文本" width="720px">
      <el-form label-width="80px">
        <el-form-item label="标题"><el-input v-model="documentForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="documentForm.content" type="textarea" :rows="14" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="textDialog = false">取消</el-button><el-button type="primary" @click="saveTextDocument">入库</el-button></template>
    </el-dialog>

    <el-dialog v-model="importDialog" title="导入知识文档" width="560px">
      <el-form label-width="80px">
        <el-form-item label="标题"><el-input v-model="importTitle" placeholder="不填则使用文件名" /></el-form-item>
        <el-form-item label="文件">
          <el-upload :auto-upload="false" :limit="1" accept=".txt,.md,.doc,.docx,.pdf" :on-change="chooseFile">
            <el-button>选择文件</el-button>
          </el-upload>
          <div class="upload-tip">支持 TXT、Markdown、Word 和文本型 PDF；扫描版 PDF 需先完成 OCR。</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="importDialog = false">取消</el-button><el-button type="primary" @click="upload">导入</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-page { padding: 24px; }
header, .card-header, .toolbar, .search-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
header { margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
header p, .toolbar span, .result small { color: #84909a; }
header p, .result p { margin: 0; }
.base-item { padding: 12px; border-radius: 6px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
.base-item:hover, .base-item.active { background: #ecf5ff; color: #409eff; }
.base-item small { color: #909399; }
.toolbar { justify-content: flex-start; margin-bottom: 14px; }
.search-bar { justify-content: flex-start; margin-bottom: 16px; }
.search-bar .el-input { max-width: 520px; }
.assistant-bar { align-items: flex-start; margin-top: 16px; }
.assistant-bar .el-textarea { max-width: 720px; }
.answer { white-space: pre-wrap; line-height: 1.8; }
.citation { margin-bottom: 8px; color: #606266; }
.result { margin-bottom: 12px; }
.result p { margin: 10px 0; white-space: pre-wrap; line-height: 1.7; }
.upload-tip { margin-top: 8px; color: #909399; font-size: 12px; line-height: 1.5; }
</style>
