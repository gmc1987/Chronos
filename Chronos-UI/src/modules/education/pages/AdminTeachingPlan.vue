<template>
  <section class="plan-page">
    <header class="page-head">
      <div><h1>教学计划</h1><p>以教学班为边界编排章节、周次和课时，版本与审核由服务端管理。</p></div>
      <el-button type="primary" @click="startCreate">新建教学计划</el-button>
    </header>
    <el-form inline @submit.prevent="load">
      <el-form-item label="教学班"><el-select v-model="filters.offeringId" clearable filterable style="width:320px" placeholder="选择教学班">
        <el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="offeringLabel(item)" />
      </el-select></el-form-item>
      <el-button @click="load">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="name" label="计划名称" min-width="220" />
      <el-table-column label="教学班" min-width="260"><template #default="{ row }">{{ offeringLabel(findOffering(row.offeringId)) }}</template></el-table-column>
      <el-table-column prop="totalHours" label="总课时" width="90" />
      <el-table-column prop="currentVersionNo" label="版本" width="80" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="250" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="openWorkbench(row)">工作台</el-button>
        <el-button link @click="editPlan(row)">编辑</el-button>
        <el-button link type="danger" @click="archive(row)">归档</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />

    <el-drawer v-model="workbench" size="78%" title="教学计划工作台">
      <template v-if="selected">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="计划名称">{{ selected.name }}</el-descriptions-item>
          <el-descriptions-item label="教学班">{{ offeringLabel(findOffering(selected.offeringId)) }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag>{{ selected.status }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="总课时">{{ selected.totalHours }}（章节合计 {{ chapterHours }}）</el-descriptions-item>
          <el-descriptions-item label="当前版本">v{{ selected.currentVersionNo || 1 }}</el-descriptions-item>
          <el-descriptions-item label="审核记录">{{ reviewText }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs v-model="activeTab" class="workbench-tabs">
          <el-tab-pane label="章节编排" name="chapters">
            <div class="toolbar"><el-button type="primary" @click="addChapter">新增章节</el-button><span :class="{ danger: chapterError }">{{ chapterError || '周次 1–60，起始不得晚于结束，课时必须大于 0' }}</span></div>
            <el-table :data="chapters" border>
              <el-table-column label="#" width="55"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column>
              <el-table-column label="章节名称" min-width="190"><template #default="{ row }"><el-input v-model="row.chapterName" /></template></el-table-column>
              <el-table-column label="周次" width="190"><template #default="{ row }"><div class="week-input"><el-input-number v-model="row.weekStart" :min="1" :max="60" /><span>至</span><el-input-number v-model="row.weekEnd" :min="1" :max="60" /></div></template></el-table-column>
              <el-table-column label="课时" width="105"><template #default="{ row }"><el-input-number v-model="row.lessonHours" :min="1" /></template></el-table-column>
              <el-table-column label="目标/重点" min-width="260"><template #default="{ row }"><el-input v-model="row.objectives" type="textarea" :rows="2" /></template></el-table-column>
              <el-table-column label="操作" width="170"><template #default="{ $index }">
                <el-button link @click="moveChapter($index, -1)" :disabled="$index === 0">上移</el-button><el-button link @click="moveChapter($index, 1)" :disabled="$index === chapters.length - 1">下移</el-button><el-button link @click="duplicateChapter($index)">复制</el-button><el-button link type="danger" @click="removeChapter($index)">删除</el-button>
              </template></el-table-column>
            </el-table>
            <div class="chapter-footer">章节合计 <strong>{{ chapterHours }}</strong> 课时 <el-button type="primary" :disabled="!!chapterError" @click="saveChapters">保存章节</el-button></div>
          </el-tab-pane>
          <el-tab-pane label="版本" name="versions"><el-table v-loading="versionsLoading" :data="versions" border><el-table-column prop="versionNo" label="版本" width="90" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="createTime" label="生成时间" /><el-table-column prop="publishedAt" label="发布时间" /></el-table></el-tab-pane>
          <el-tab-pane label="审核" name="review"><el-empty v-if="!reviewStatus" description="暂无审核记录" /><el-descriptions v-else border :column="2"><el-descriptions-item label="状态">{{ reviewStatus.status }}</el-descriptions-item><el-descriptions-item label="处理人">{{ reviewStatus.reviewerName || '—' }}</el-descriptions-item><el-descriptions-item label="意见" :span="2">{{ reviewStatus.comment || '—' }}</el-descriptions-item></el-descriptions></el-tab-pane>
        </el-tabs>
        <div class="drawer-actions"><el-button @click="workbench=false">关闭</el-button><el-button type="primary" :disabled="selected.status !== 'DRAFT'" @click="submitPlan">提交审核</el-button></div>
      </template>
    </el-drawer>

    <el-dialog v-model="editor" :title="editing ? '编辑教学计划' : '新建教学计划'" width="720px" destroy-on-close>
      <el-steps :active="wizardStep" finish-status="success" simple><el-step title="选择教学班" /><el-step title="填写目标" /><el-step title="检查并保存" /></el-steps>
      <el-form v-if="wizardStep === 0" ref="formRef" :model="form" label-width="110px" class="editor-form"><el-form-item label="教学班" required><el-select v-model="form.offeringId" filterable style="width:100%" placeholder="选择可见教学班"><el-option v-for="item in offerings" :key="item.id" :value="item.id" :label="offeringLabel(item)" /></el-select></el-form-item><el-alert title="学期与课程由教学班带入，不能在此修改。" type="info" :closable="false" /></el-form>
      <el-form v-else-if="wizardStep === 1" :model="form" label-width="110px" class="editor-form"><el-form-item label="计划名称" required><el-input v-model="form.name" maxlength="200" show-word-limit /></el-form-item><el-form-item label="计划类型" required><el-select v-model="form.planType" style="width:100%" placeholder="从字典选择"><el-option v-for="o in planTypes" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item><el-form-item label="总课时" required><el-input-number v-model="form.totalHours" :min="1" /></el-form-item><el-form-item label="教学目标" required><el-input v-model="form.objective" type="textarea" :rows="4" /></el-form-item><el-form-item label="考核方式" required><el-input v-model="form.assessmentMethod" type="textarea" :rows="3" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remarks" type="textarea" :rows="2" /></el-form-item></el-form>
      <el-alert v-else :title="checkText" :type="checkError ? 'error' : 'success'" show-icon />
      <template #footer><el-button @click="editor=false">取消</el-button><el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button><el-button v-if="wizardStep < 2" type="primary" @click="nextStep">下一步</el-button><el-button v-else type="primary" :disabled="!!checkError" @click="savePlan">保存草稿</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictionaryOptions } from '../../../api/admin'
import { archiveTeachingDomainApi, createTeachingChild, createTeachingDomainApi, teachingCenterOfferings, teachingDomainChildrenApi, teachingDomainDetailApi, teachingDomainPageApi, updateTeachingChild, updateTeachingDomainApi } from '../api/teachingCenter'
const offerings = ref([]); const rows = ref([]); const total = ref(0); const page = ref(1); const size = ref(20); const loading = ref(false)
const filters = reactive({ offeringId: '' }); const planTypes = ref([])
const editor = ref(false); const editing = ref(''); const wizardStep = ref(0); const formRef = ref()
const form = reactive({ offeringId: '', name: '', planType: '', totalHours: 1, objective: '', assessmentMethod: '', remarks: '' })
const workbench = ref(false); const selected = ref(null); const activeTab = ref('chapters'); const chapters = ref([]); const versions = ref([]); const versionsLoading = ref(false); const reviewStatus = ref(null)
const offeringLabel = (x) => x ? `${x.semesterCode || x.semesterName || '当前学期'} · ${x.courseName || x.courseId || ''} · ${x.teachingClassName || x.name || ''}` : '—'
const findOffering = (id) => offerings.value.find(x => x.id === id)
const load = async () => { loading.value = true; try { const r = await teachingDomainPageApi('plans', { ...filters, page: page.value - 1, size: size.value }); rows.value = r.data?.content || []; total.value = r.data?.totalElements || 0 } catch (e) { ElMessage.error(e.message) } finally { loading.value = false } }
const startCreate = () => { editing.value = ''; Object.assign(form, { offeringId: filters.offeringId, name: '', planType: planTypes.value[0]?.value || '', totalHours: 1, objective: '', assessmentMethod: '', remarks: '' }); wizardStep.value = 0; editor.value = true }
const editPlan = (row) => { editing.value = row.id; Object.assign(form, { ...form, ...row, objective: row.objective || row.objectives || '' }); wizardStep.value = 1; editor.value = true }
const nextStep = () => { if (wizardStep.value === 0 && !form.offeringId) return ElMessage.warning('请选择教学班'); if (wizardStep.value === 1 && (!form.name || !form.planType || !form.objective || !form.assessmentMethod)) return ElMessage.warning('请完成必填字段'); wizardStep.value++ }
const checkError = computed(() => !form.offeringId || !form.name || !form.planType || !form.objective || !form.assessmentMethod || form.totalHours < 1 ? '请完成教学班、目标、考核方式和总课时。' : '')
const checkText = computed(() => checkError.value || `已选择 ${offeringLabel(findOffering(form.offeringId))}，将保存为草稿。章节可在工作台继续编排。`)
const savePlan = async () => { try { const payload = { offeringId: form.offeringId, name: form.name, attributes: { planType: form.planType, totalHours: form.totalHours, objective: form.objective, assessmentMethod: form.assessmentMethod, remarks: form.remarks } }; editing.value ? await updateTeachingDomainApi('plans', editing.value, payload) : await createTeachingDomainApi('plans', payload); editor.value = false; ElMessage.success('草稿已保存'); await load() } catch (e) { ElMessage.error(e.message) } }
const openWorkbench = async (row) => { try { const r = await teachingDomainDetailApi('plans', row.id); selected.value = r.data || row; const child = await teachingDomainChildrenApi('plans', row.id, 'items', { page: 0, size: 100 }); chapters.value = child.data?.content || []; versionsLoading.value = true; const vr = await teachingDomainChildrenApi('plans', row.id, 'versions', { page: 0, size: 100 }); versions.value = vr.data?.content || []; workbench.value = true } catch (e) { ElMessage.error(e.message) } finally { versionsLoading.value = false } }
const chapterHours = computed(() => chapters.value.reduce((sum, x) => sum + (Number(x.lessonHours) || 0), 0))
const chapterError = computed(() => chapters.value.some(x => !x.chapterName || !(x.lessonHours > 0) || !(x.weekStart >= 1 && x.weekStart <= 60) || !(x.weekEnd >= 1 && x.weekEnd <= 60) || x.weekStart > x.weekEnd) ? '请修正章节名称、周次和课时' : '')
const addChapter = () => chapters.value.push({ chapterNo: chapters.value.length + 1, chapterName: '', lessonHours: 1, weekStart: 1, weekEnd: 1, objectives: '', keyPoints: '', difficultPoints: '', sortOrder: chapters.value.length })
const removeChapter = (i) => chapters.value.splice(i, 1)
const duplicateChapter = (i) => chapters.value.splice(i + 1, 0, { ...chapters.value[i], id: undefined, chapterName: `${chapters.value[i].chapterName}（副本）` })
const moveChapter = (i, delta) => { const target = i + delta; if (target < 0 || target >= chapters.value.length) return; [chapters.value[i], chapters.value[target]] = [chapters.value[target], chapters.value[i]] }
const saveChapters = async () => { if (chapterError.value) return ElMessage.warning(chapterError.value); try { await Promise.all(chapters.value.map((item, i) => { const payload = { chapterNo: i + 1, chapterName: item.chapterName, lessonHours: item.lessonHours, weekStart: item.weekStart, weekEnd: item.weekEnd, objectives: item.objectives, keyPoints: item.keyPoints, difficultPoints: item.difficultPoints, sortOrder: i }; return item.id ? updateTeachingChild('PLAN_ITEM', item.id, payload) : createTeachingChild('PLAN_ITEM', { ...payload, planId: selected.value.id }) })); ElMessage.success('章节已保存'); await openWorkbench(selected.value) } catch (e) { ElMessage.error(e.message) } }
const submitPlan = async () => { try { await transitionTeachingDomainApi('plans', selected.value.id, 'SUBMITTED'); ElMessage.success('已提交审核'); workbench.value = false; await load() } catch (e) { ElMessage.error(e.message) } }
const reviewText = computed(() => reviewStatus.value?.status || '待提交')
const archive = async (row) => { await ElMessageBox.confirm('归档后将从默认列表隐藏，是否继续？', '确认'); try { await archiveTeachingDomainApi('plans', row.id); ElMessage.success('已归档'); await load() } catch (e) { ElMessage.error(e.message) } }
onMounted(async () => { try { const [o, d] = await Promise.all([teachingCenterOfferings(), dictionaryOptions('EDU_TEACHING_PLAN_TYPE')]); offerings.value = o.data || []; planTypes.value = (d.data || []).map(x => ({ label: x.dictName || x.itemName || x.name || x.label, value: x.dictValue || x.itemValue || x.value || x.code })); await load() } catch (e) { ElMessage.error(e.message) } })
</script>

<style scoped>
.plan-page{padding:24px}.page-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.page-head h1{margin:0 0 6px}.page-head p{margin:0;color:#7b8794}.el-pagination{margin-top:20px;justify-content:flex-end}.workbench-tabs{margin-top:22px}.toolbar,.chapter-footer,.drawer-actions{display:flex;align-items:center;gap:14px;margin:16px 0}.toolbar span{color:#7b8794}.toolbar .danger,.danger{color:#d93025}.week-input{display:flex;align-items:center;gap:5px}.week-input .el-input-number{width:78px}.chapter-footer{justify-content:flex-end}.chapter-footer strong{color:#2563eb}.drawer-actions{justify-content:flex-end;border-top:1px solid #ebeef5;padding-top:16px}.editor-form{padding:26px 10px 10px}
</style>
