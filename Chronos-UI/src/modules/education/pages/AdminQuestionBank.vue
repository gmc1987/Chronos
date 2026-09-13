<template>
  <section class="slice-page">
    <header class="page-head"><div><h1>题库与题目</h1><p>按课程、题库、题目三级管理，发布后保留版本快照。</p></div>
      <div><el-button @click="downloadTemplate">下载 CSV 模板</el-button><el-button type="primary" @click="openBank()">新建题库</el-button></div>
    </header>
    <div class="crumbs">
      <el-select v-model="courseId" clearable filterable placeholder="选择课程" @change="selectCourse">
        <el-option v-for="course in courses" :key="course.id" :label="course.courseName" :value="course.id" />
      </el-select>
      <el-select v-model="bankId" clearable filterable placeholder="选择题库" :disabled="!courseId" @change="loadQuestions">
        <el-option v-for="bank in banks" :key="bank.id" :label="bank.name" :value="bank.id" />
      </el-select>
      <el-button type="primary" :disabled="!bankId" @click="openQuestion()">新增题目</el-button>
      <el-upload :show-file-list="false" accept=".csv" :before-upload="previewImport"><el-button :disabled="!bankId">导入 CSV</el-button></el-upload>
    </div>
    <el-alert v-if="!courseId" title="请选择课程开始维护题库" type="info" :closable="false" />
    <el-table v-else v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="stem" label="题目" min-width="300" show-overflow-tooltip />
      <el-table-column label="题型" width="110"><template #default="{ row }">{{ dictLabel(typeOptions, row.questionType) }}</template></el-table-column>
      <el-table-column label="难度" width="100"><template #default="{ row }">{{ dictLabel(difficultyOptions, row.difficulty) }}</template></el-table-column>
      <el-table-column prop="score" label="分值" width="80" />
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ dictLabel(statusOptions, row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="210" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="openQuestion(row)">编辑</el-button>
        <el-button link type="success" :disabled="!canSubmit(row)" @click="publish(row)">发布</el-button>
        <el-button link @click="showVersions(row)">版本</el-button>
      </template></el-table-column>
    </el-table>

    <el-dialog v-model="bankDialog" title="题库" width="560px"><el-form :model="bankForm" label-width="100px">
      <el-form-item label="课程" required><el-select v-model="bankForm.courseId" filterable style="width:100%"><el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" /></el-select></el-form-item>
      <el-form-item label="教学班"><el-select v-model="bankForm.offeringId" clearable filterable style="width:100%" placeholder="课程公共题库可不选"><el-option v-for="item in offerings" :key="item.id" :label="offeringLabel(item)" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="名称" required><el-input v-model="bankForm.name" /></el-form-item>
      <el-form-item label="可见范围"><el-select v-model="bankForm.visibility" style="width:100%"><el-option v-for="o in visibilityOptions" :key="o.value" v-bind="o" /></el-select></el-form-item>
      <el-form-item label="描述"><el-input v-model="bankForm.description" type="textarea" /></el-form-item>
    </el-form><template #footer><el-button @click="bankDialog=false">取消</el-button><el-button type="primary" @click="saveBank">保存</el-button></template></el-dialog>

    <el-dialog v-model="questionDialog" :title="questionForm.id ? '编辑题目（保存为草稿修订）' : '新增题目'" width="820px">
      <el-form :model="questionForm" label-width="100px">
        <el-form-item label="题型" required><el-select v-model="questionForm.questionType" style="width:220px"><el-option v-for="o in typeOptions" :key="o.value" v-bind="o" /></el-select></el-form-item>
        <el-form-item label="题干" required><el-input v-model="questionForm.stem" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="难度" required><el-select v-model="questionForm.difficulty" style="width:220px"><el-option v-for="o in difficultyOptions" :key="o.value" v-bind="o" /></el-select></el-form-item>
        <el-form-item label="分值" required><el-input-number v-model="questionForm.score" :min="0.01" :precision="2" /></el-form-item>
        <template v-if="isChoice">
          <el-form-item label="选项"><div class="options"><div v-for="(option, index) in questionForm.options" :key="option.optionKey" class="option-row">
            <el-input v-model="option.content" :placeholder="`选项 ${option.optionKey}`" /><el-checkbox v-model="option.correct">正确答案</el-checkbox>
            <el-button link type="danger" @click="questionForm.options.splice(index,1)">删除</el-button>
          </div><el-button link type="primary" @click="addOption">添加选项</el-button></div></el-form-item>
        </template>
        <el-form-item label="答案" required><el-input v-model="questionForm.answer" type="textarea" :rows="2" placeholder="按题型填写标准答案" /></el-form-item>
        <el-form-item label="解析"><el-input v-model="questionForm.analysis" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="知识点" required><el-select v-model="questionForm.knowledgePointIds" multiple filterable style="width:100%"><el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="questionDialog=false">取消</el-button><el-button type="primary" @click="saveQuestion">保存草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="importDialog" title="CSV 导入预检" width="760px"><el-alert :title="importSummary" :type="importErrors.length ? 'warning' : 'success'" :closable="false" /><el-table :data="importErrors" size="small" v-if="importErrors.length"><el-table-column prop="row" label="行号" width="80" /><el-table-column prop="message" label="错误" /></el-table><template #footer><el-button @click="importDialog=false">取消</el-button><el-button type="primary" :disabled="!!importErrors.length" @click="commitImport">确认导入</el-button></template></el-dialog>
    <el-dialog v-model="versionsDialog" title="题目发布版本" width="600px"><el-table :data="versions"><el-table-column prop="versionNo" label="版本" /><el-table-column prop="status" label="状态" /><el-table-column prop="createdAt" label="创建时间" /></el-table></el-dialog>
  </section>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictionaryOptions } from '../../../api/admin'
import { listCourseCatalog } from '../../../api/admin'
import { teachingCenterOfferings, questionBanks, createQuestionBank, updateQuestionBank, questions, createQuestion, updateQuestion, submitQuestion, questionVersions, questionImportTemplate, validateQuestionImport, commitQuestionImport, knowledgePointTree } from '../api/teachingCenter'
const courses = ref([]); const banks = ref([]); const rows = ref([]); const points = ref([]); const loading = ref(false)
const courseId = ref(''); const bankId = ref(''); const offerings = ref([])
const typeOptions = ref([]); const difficultyOptions = ref([]); const statusOptions = ref([]); const visibilityOptions = ref([])
const bankDialog = ref(false); const questionDialog = ref(false); const importDialog = ref(false); const versionsDialog = ref(false)
const bankForm = reactive({}); const questionForm = reactive({ options: [], knowledgePointIds: [] }); const versions = ref([]); const importErrors = ref([]); const importBody = ref(''); const importSummary = ref('')
const dict = async (code) => (await dictionaryOptions(code))?.data?.map(item => ({ label: item.dictName || item.itemName, value: item.dictValue || item.itemValue })) || []
const dictLabel = (list, value) => list.find(x => x.value === value)?.label || value || '-'
const isChoice = computed(() => typeOptions.value.find(o => o.value === questionForm.questionType)?.label?.match(/选择/))
const offeringLabel = item => [item.semesterCode, item.courseName, item.teachingClassName].filter(Boolean).join(' · ')
const reset = (target, data) => { Object.keys(target).forEach(k => delete target[k]); Object.assign(target, data) }
const selectCourse = async () => { bankId.value = ''; rows.value = []; banks.value = []; if (!courseId.value) return; const res = await questionBanks({ courseId: courseId.value, page: 0, size: 200 }); banks.value = res.data?.content || res.data || []; const p = await knowledgePointTree(courseId.value); points.value = flatten(p.data || []) }
const flatten = (nodes, out = []) => { (Array.isArray(nodes) ? nodes : nodes.content || []).forEach(n => { out.push(n); if (n.children) flatten(n.children, out) }); return out }
const loadQuestions = async () => { if (!bankId.value) return; loading.value = true; try { const res = await questions({ bankId: bankId.value, page: 0, size: 200 }); rows.value = res.data?.content || res.data || [] } finally { loading.value = false } }
const openBank = (row) => { reset(bankForm, row ? { ...row } : { courseId: courseId.value, offeringId: '', name: '', visibility: visibilityOptions.value[0]?.value || '', description: '' }); bankDialog.value = true }
const saveBank = async () => { try { bankForm.id ? await updateQuestionBank(bankForm.id, { ...bankForm }) : await createQuestionBank({ ...bankForm }); bankDialog.value = false; await selectCourse(); ElMessage.success('题库已保存') } catch (e) { ElMessage.error(e.message) } }
const openQuestion = (row) => { reset(questionForm, row ? { ...row, options: row.options || [], knowledgePointIds: row.knowledgePointIds || [] } : { bankId: bankId.value, questionType: typeOptions.value[0]?.value || '', difficulty: difficultyOptions.value[0]?.value || '', stem: '', score: 1, answer: '', analysis: '', options: [], knowledgePointIds: [] }); questionDialog.value = true }
const addOption = () => questionForm.options.push({ optionKey: String.fromCharCode(65 + questionForm.options.length), content: '', correct: false })
const saveQuestion = async () => { if (!questionForm.stem || !questionForm.answer || !questionForm.knowledgePointIds?.length) return ElMessage.warning('请填写题干、答案并选择知识点'); try { const body = { ...questionForm, options: isChoice.value ? questionForm.options : [] }; questionForm.id ? await updateQuestion(questionForm.id, body) : await createQuestion(body); questionDialog.value = false; await loadQuestions(); ElMessage.success('草稿已保存') } catch (e) { ElMessage.error(e.message) } }
const canSubmit = row => row.capabilities?.submit !== false
const publish = async row => { await ElMessageBox.confirm('发布后编辑将生成新的草稿版本，是否继续？', '确认发布'); try { await submitQuestion(row.id); await loadQuestions(); ElMessage.success('已提交发布') } catch (e) { ElMessage.error(e.message) } }
const showVersions = async row => { try { versions.value = (await questionVersions(row.id)).data || []; versionsDialog.value = true } catch (e) { ElMessage.error(e.message) } }
const downloadTemplate = async () => { const blob = await questionImportTemplate(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'question-import-template.csv'; a.click(); URL.revokeObjectURL(url) }
const previewImport = async file => { importBody.value = await file.text(); try { const res = await validateQuestionImport(importBody.value); const data = res.data || {}; importErrors.value = data.errors || []; importSummary.value = `预检 ${data.total || 0} 行，${data.valid || 0} 行可导入`; importDialog.value = true } catch (e) { ElMessage.error(e.message) } return false }
const commitImport = async () => { try { await commitQuestionImport(importBody.value); importDialog.value = false; await loadQuestions(); ElMessage.success('导入成功') } catch (e) { ElMessage.error(e.message) } }
onMounted(async () => { try { const [catalog, offering, types, difficulty, status, visibility] = await Promise.all([listCourseCatalog({ page: 0, size: 500 }), teachingCenterOfferings(), dict('EDU_QUESTION_TYPE'), dict('EDU_QUESTION_DIFFICULTY'), dict('COMMON_STATUS'), dict('EDU_RESOURCE_VISIBILITY')]); courses.value = catalog.data?.content || catalog.data || []; offerings.value = offering.data || []; typeOptions.value = types; difficultyOptions.value = difficulty; statusOptions.value = status; visibilityOptions.value = visibility } catch (e) { ElMessage.error(e.message) } })
</script>
<style scoped>
.slice-page{padding:24px}.page-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.page-head h1{margin:0 0 6px}.page-head p{margin:0;color:#7b8794}.crumbs{display:flex;gap:12px;margin-bottom:18px}.crumbs .el-select{width:250px}.options{width:100%}.option-row{display:flex;gap:10px;align-items:center;margin-bottom:8px}.option-row .el-input{max-width:480px}
</style>
