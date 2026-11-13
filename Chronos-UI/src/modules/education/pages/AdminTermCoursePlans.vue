<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import AcademicCatalogImport from '../components/AcademicCatalogImport.vue'
import { http } from '../../../api/http'

const base = '/admin/education/term-course-plans'
const manage = ['education:term-course-plan:manage']
const route = useRoute()
const organizationId = ref(typeof route.query.organizationId === 'string' ? route.query.organizationId : '')
const termCode = ref('')
const terms = ref([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const rows = ref([])
const total = ref(0)
const options = ref([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editing = ref(null)
const generation = ref(null)
const generationOpen = ref(false)
const form = reactive({ classId: '', programId: '', courseId: '', weeklyLessons: 2 })
const canMaintainTerm = computed(() => terms.value.some(term => term.termCode === termCode.value && term.status === 'ACTIVE'))
const context = computed(() => ({ organizationId: organizationId.value, termCode: termCode.value }))
const query = (params = context.value) => new URLSearchParams(params).toString()
const url = (path = '', params) => base + path + '?' + query(params)
const classOptions = computed(() => [...new Map(options.value.map(row => [row.classId, row])).values()])
const programOptions = computed(() => [...new Map(options.value
  .filter(row => row.classId === form.classId).map(row => [row.programId, row])).values()])
const courseOptions = computed(() => options.value.filter(row =>
  row.classId === form.classId && row.programId === form.programId))
const selectedTermId = computed(() => terms.value.find(term => term.termCode === termCode.value)?.id)
const candidates = computed(() => (generation.value?.rows || []).filter(row => row.status === '可生成'))

let requestVersion = 0
const reload = async () => {
  const version = ++requestVersion
  if (!organizationId.value || !termCode.value) {
    rows.value = []
    total.value = 0
    loading.value = false
    return
  }
  loading.value = true
  try {
    const { data } = await http.get(url('', { ...context.value, keyword: keyword.value, page: page.value - 1, size: size.value }))
    if (version !== requestVersion) return
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

const search = () => {
  page.value = 1
  return reload()
}

let termVersion = 0
watch(organizationId, async id => {
  const version = ++termVersion
  terms.value = []
  termCode.value = ''
  if (!id) return
  const { data } = await http.get(url('/terms', { organizationId: id }))
  if (version !== termVersion) return
  terms.value = data
  termCode.value = (data.find(term => term.currentTerm) || data[0])?.termCode || ''
}, { immediate: true })

watch([organizationId, termCode], () => {
  dialog.value = false
  generationOpen.value = false
  generation.value = null
  rows.value = []
  total.value = 0
  search()
})

const open = async row => {
  const target = query()
  editing.value = row || null
  Object.assign(form, { classId: '', programId: '', courseId: '', weeklyLessons: row?.weeklyLessons ?? 2 })
  if (!row) {
    const { data } = await http.get(url('/options'))
    if (target !== query()) return
    options.value = data
  }
  dialog.value = true
}

const save = async () => {
  const target = query()
  saving.value = true
  try {
    if (editing.value) {
      await http.put(url('/' + editing.value.id), { weeklyLessons: form.weeklyLessons })
    } else {
      await http.post(url(), form)
    }
    if (target !== query()) return
    ElMessage.success('已保存开课计划')
    dialog.value = false
    await search()
  } finally {
    saving.value = false
  }
}

const remove = async row => {
  const target = query()
  try {
    await ElMessageBox.confirm('确认删除这条开课计划？', '删除开课计划', { type: 'warning' })
  } catch (error) {
    if (['cancel', 'close'].includes(error)) return
    throw error
  }
  if (target !== query()) return
  await http.delete(url('/' + row.id))
  ElMessage.success('已删除开课计划')
  await search()
}

const generate = async dryRun => {
  const target = query()
  const revisions = dryRun ? {} : Object.fromEntries(candidates.value.map(row => [row.planId, row.revision]))
  saving.value = true
  try {
    const { data } = await http.post(url('/generation'), { dryRun, revisions })
    if (target !== query()) return
    generation.value = data
    generationOpen.value = true
    if (!dryRun) {
      ElMessage.success('本次已生成 ' + data.created + ' 条教学任务')
      await reload()
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="page">
    <h2>学期开课计划</h2>
    <p>从已发布的培养方案选定本学期各行政班的课程。确认任教关系后，生成走班排课所需的教学任务。</p>
    <div class="toolbar">
      <OrganizationSelect v-model="organizationId" school-or-campus-only style="width:260px" />
      <el-select v-model="termCode" placeholder="请选择学期" filterable style="width:240px">
        <el-option v-for="term in terms" :key="term.id" :value="term.termCode" :label="term.termName" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="班级、方案、课程或学科" style="width:240px" @keyup.enter="search" @clear="search" />
      <el-button :disabled="!termCode" @click="search">查询</el-button>
      <span v-permission="manage" class="actions">
        <el-button type="primary" :disabled="!canMaintainTerm" @click="open()">新增开课计划</el-button>
        <AcademicCatalogImport kind="TERM_COURSE_PLANS" label="学期开课计划" :endpoint="base + '/import'"
          :organization-id="canMaintainTerm ? organizationId : ''" :params="{ termCode }" @imported="search" />
        <el-button :disabled="!canMaintainTerm || !total" :loading="saving" @click="generate(true)">预览教学任务</el-button>
      </span>
      <el-button v-permission="['education:scheduling:view','education:scheduling:manage']" :disabled="!termCode"
        @click="$router.push({ name: 'admin-education-class-scheduling', query: { organizationId, semesterCode: termCode, schedulingTab: 'offerings' } })">
        查看排课教学任务
      </el-button>
    </div>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="className" label="行政班" min-width="150" />
      <el-table-column prop="programName" label="培养方案" min-width="170" />
      <el-table-column prop="courseCode" label="课程编码" min-width="110" />
      <el-table-column prop="courseName" label="课程" min-width="150" />
      <el-table-column prop="subjectName" label="学科" min-width="120" />
      <el-table-column prop="weeklyLessons" label="周课时" width="90" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">{{ row.offeringId ? '已生成任务' : '待生成任务' }}</template>
      </el-table-column>
      <AdaptiveActionColumn label="操作" width="130">
        <template #default="{ row }">
          <span v-permission="manage">
            <el-button link type="primary" :disabled="!canMaintainTerm || !!row.offeringId" @click="open(row)">编辑课时</el-button>
            <el-button link type="danger" :disabled="!canMaintainTerm || !!row.offeringId" @click="remove(row)">删除</el-button>
          </span>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next" :total="total" @current-change="reload" @size-change="search" />

    <el-dialog v-model="dialog" :title="editing ? '编辑开课计划' : '新增开课计划'" width="560px">
      <el-empty v-if="!editing && !classOptions.length" description="请先维护行政班并发布适用的培养方案，方案课程需关联学科。" />
      <el-form v-else label-width="100px">
        <template v-if="!editing">
          <el-form-item label="行政班">
            <el-select v-model="form.classId" filterable @change="form.programId = ''; form.courseId = ''">
              <el-option v-for="row in classOptions" :key="row.classId" :value="row.classId" :label="row.className" />
            </el-select>
          </el-form-item>
          <el-form-item label="培养方案">
            <el-select v-model="form.programId" filterable @change="form.courseId = ''">
              <el-option v-for="row in programOptions" :key="row.programId" :value="row.programId" :label="row.programName" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="form.courseId" filterable>
              <el-option v-for="row in courseOptions" :key="row.courseId" :value="row.courseId"
                :label="row.courseName + '（建议第 ' + row.suggestedTermNo + ' 学期）'" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="周课时"><el-input-number v-model="form.weeklyLessons" :min="1" :max="56" :precision="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!editing && !form.courseId" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generationOpen" title="教学任务候选结果" width="860px">
      <el-table :data="generation?.rows || []" border max-height="450">
        <el-table-column prop="className" label="行政班" min-width="150" />
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column label="任课教师" min-width="100">
          <template #default="{ row }">{{ row.teacherName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="studentCount" label="人数" width="75" />
        <el-table-column prop="weeklyLessons" label="周课时" width="80" />
        <el-table-column prop="status" label="结果" min-width="200" />
      </el-table>
      <p>教师来自同学期、同行政班的启用任教关系。人数来自该班学籍状态为在籍的学生。</p>
      <template #footer>
        <el-button v-permission="['education:teaching-assignment:view', 'education:teaching-assignment:manage']"
          @click="$router.push({ name: 'admin-education-teaching-assignments', query: { organizationId, academicTermId: selectedTermId } })">维护任教关系</el-button>
        <el-button @click="generationOpen = false">关闭</el-button>
        <el-button type="primary" :disabled="!candidates.length" :loading="saving" @click="generate(false)">
          确认生成 {{ candidates.length }} 条教学任务
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page { padding: 24px; }
h2 { margin: 0 0 8px; }
p { color: var(--el-text-color-secondary); }
.toolbar, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.toolbar { margin: 16px 0 10px; }
.el-pagination { justify-content: flex-end; margin-top: 10px; }
.el-form .el-select { width: 100%; }
</style>
