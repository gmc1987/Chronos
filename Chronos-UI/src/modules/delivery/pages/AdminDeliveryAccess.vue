<template>
  <section class="page">
    <h2>交付授权管理</h2>
    <el-alert v-if="status" :title="status.message" :type="status.active ? 'success' : 'warning'" :closable="false" />
    <el-descriptions v-if="status" :column="2" border class="details">
      <el-descriptions-item label="部署实例">{{ status.deploymentId }}</el-descriptions-item>
      <el-descriptions-item label="授权版本">{{ status.revision || '—' }}</el-descriptions-item>
      <el-descriptions-item label="授权编号">{{ status.licenseId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="开通模块">{{ status.modules.map(name).join('、') || '—' }}</el-descriptions-item>
    </el-descriptions>
    <router-link v-if="status?.routes?.includes('/portal/collaboration/files') && canReadShared"
      to="/portal/collaboration/files"><el-button>打开共享文档</el-button></router-link>
    <router-link v-if="status?.routes?.includes('/admin/education/accommodations') && canReadAccommodation"
      to="/admin/education/accommodations"><el-button>打开宿舍管理</el-button></router-link>
    <div v-if="status?.modules?.includes('EDU_TEACHING_RESOURCES') && canReadTeaching" class="toolbar">
      <router-link to="/admin/education/teaching-center"><el-button>打开教学资源与教研</el-button></router-link>
      <router-link to="/portal/education/teaching-center"><el-button>门户教学中心</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_HOMEWORK') && canReadHomework" class="toolbar">
      <router-link v-if="canManageHomework" to="/admin/education/teaching-center/homework"><el-button>打开作业管理</el-button></router-link>
      <router-link v-if="canManageHomework" to="/admin/education/teaching-center/homework-analysis"><el-button>作业统计</el-button></router-link>
      <router-link v-if="!canManageHomework" to="/portal/education/homework"><el-button>我的作业</el-button></router-link>
      <router-link :to="canManageHomework ? '/admin/education/teaching-center/error-book' : '/portal/education/teaching/mistakes'">
        <el-button>{{ canManageHomework ? '错题统计' : '我的错题' }}</el-button>
      </router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_SUPERVISION')" class="toolbar">
      <router-link v-if="canReadSupervision" to="/admin/education/supervision"><el-button>打开督导中心</el-button></router-link>
      <router-link v-if="canObserve" to="/portal/education/supervision"><el-button>我的督导任务</el-button></router-link>
      <router-link v-if="canRectify" to="/portal/education/supervision/teaching"><el-button>授课签到与整改</el-button></router-link>
    </div>
    <router-link v-if="status?.modules?.includes('THIRD_PARTY_INTEGRATIONS') && canManage"
      to="/admin/integration"><el-button>打开集成中心</el-button></router-link>
    <router-link v-if="status?.modules?.includes('EDU_ANALYTICS') && canReadAnalytics"
      to="/admin/education/data-center"><el-button>打开校务数据分析</el-button></router-link>
    <router-link v-if="canManage && status?.routes?.includes('/admin/ai-models')"
      to="/admin/ai-models"><el-button>配置部署模型</el-button></router-link>
    <router-link v-if="status?.modules?.includes('AI_KNOWLEDGE') && canReadKnowledge"
      to="/admin/education/knowledge"><el-button>打开 AI 知识库</el-button></router-link>
    <div v-if="status?.modules?.includes('COLLABORATION_OFFICE')" class="toolbar">
      <router-link v-if="canReadOffice" to="/portal/collaboration"><el-button>打开协同办公</el-button></router-link>
      <router-link v-if="canReadOffice" to="/portal/tasks"><el-button>我的流程待办</el-button></router-link>
      <router-link v-if="canManage" to="/admin/workflows"><el-button>设计办公流程</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_TEACHING_OPERATIONS')" class="toolbar">
      <template v-if="canReadTeachingRun">
        <router-link to="/admin/education/academic-lifecycle"><el-button>培养方案</el-button></router-link>
        <router-link to="/admin/education/term-course-plans"><el-button>学期开课计划</el-button></router-link>
        <router-link to="/admin/education/classroom-management"><el-button>教室管理</el-button></router-link>
        <router-link to="/admin/education/scheduling"><el-button>走班排课</el-button></router-link>
        <router-link to="/admin/education/teaching-hours"><el-button>课时台账</el-button></router-link>
      </template>
      <router-link to="/portal/education/schedule"><el-button>我的课表</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_STUDENT_LIFECYCLE') && canReadAdmissions" class="toolbar">
      <router-link to="/admin/education/admissions"><el-button>新生入学</el-button></router-link>
      <router-link to="/admin/education/academic-lifecycle"><el-button>培养方案</el-button></router-link>
      <router-link to="/admin/education/term-registrations"><el-button>学期注册</el-button></router-link>
      <router-link to="/admin/education/course-enrollments"><el-button>选退课</el-button></router-link>
      <router-link to="/admin/education/graduation-audits"><el-button>毕业审核</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_TEACHER_PERSONNEL')" class="toolbar">
      <router-link v-if="canUsePersonnel" to="/admin/education/teacher-personnel"><el-button>打开教师人事工作台</el-button></router-link>
      <router-link to="/portal/education/teacher-personnel"><el-button>我的人事申请</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_HOME_SCHOOL')" class="toolbar">
      <router-link v-if="canReadFamily" to="/admin/education/parents"><el-button>打开家长管理</el-button></router-link>
      <router-link v-if="canReadFamily" to="/admin/education/home-school/notices"><el-button>打开学校通知</el-button></router-link>
      <router-link v-if="canReadFamily" to="/admin/education/home-school/class-groups"><el-button>打开班级群</el-button></router-link>
      <router-link v-if="canReadFamily" to="/admin/education/home-school/feedback"><el-button>打开家长反馈</el-button></router-link>
      <router-link v-if="canReadFamily" to="/admin/education/home-school/communications"><el-button>打开家校沟通</el-button></router-link>
      <router-link v-if="canManageFamilyMeetings" to="/admin/education/parent-meetings"><el-button>打开家长会</el-button></router-link>
      <router-link to="/portal/education/head-teacher"><el-button>班主任工作台</el-button></router-link>
      <router-link to="/portal/education/family"><el-button>打开家长门户</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_GRADES')" class="toolbar">
      <template v-if="canReadGrades">
        <router-link to="/admin/education/score-center"><el-button>打开成绩中心</el-button></router-link>
        <router-link to="/admin/education/score-center/class-analysis"><el-button>打开班级成绩分析</el-button></router-link>
      </template>
      <router-link to="/portal/education/grades"><el-button>我的成绩</el-button></router-link>
    </div>
    <div v-if="status?.modules?.includes('EDU_EXAM')" class="toolbar">
      <template v-if="canReadExam">
        <router-link to="/admin/education/exam/plans"><el-button>打开考试计划</el-button></router-link>
        <router-link to="/admin/education/exam/rooms"><el-button>打开考场管理</el-button></router-link>
        <router-link to="/admin/education/exam/invigilation"><el-button>打开监考管理</el-button></router-link>
      </template>
      <router-link to="/portal/education/exam/my-exams"><el-button>我的考试</el-button></router-link>
      <router-link to="/portal/education/exam/invigilation"><el-button>我的监考</el-button></router-link>
    </div>
    <router-link v-if="status?.modules?.includes('EDU_INTERNSHIP') && canReadInternship"
      to="/admin/education/internships"><el-button>打开实习实训</el-button></router-link>
    <div v-if="status?.modules?.includes('CAMPUS_VENUE')" class="toolbar">
      <router-link v-if="canReadVenue" to="/admin/education/classroom-management"><el-button>打开教室管理</el-button></router-link>
      <router-link v-if="canReadVenue" to="/admin/education/classroom-reservations"><el-button>打开教室预约</el-button></router-link>
      <router-link v-if="canApplyVenue" to="/portal/education/classroom-reservations"><el-button>我的教室申请</el-button></router-link>
    </div>
    <div v-if="canManage && status?.active">
      <div class="toolbar">
        <el-button type="primary" @click="open('school')">新增学校</el-button>
        <el-button @click="open('account')">新增学校账号</el-button>
      </div>
      <el-table :data="accounts" border>
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="display_name" label="姓名" />
        <el-table-column prop="organization_name" label="所属学校" />
        <el-table-column label="授权范围"><template #default="{ row }">{{ describe(row) }}</template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{ row }">
          <el-button v-if="assignableModules.length" link type="primary" @click="open('grant', row)">分配模块权限</el-button>
        </template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="visible" :title="titles[mode]" width="530px">
      <el-form label-width="105px" @submit.prevent>
        <template v-if="mode === 'school'">
          <el-form-item label="学校编码"><el-input v-model="form.code" /></el-form-item>
          <el-form-item label="学校名称"><el-input v-model="form.name" /></el-form-item>
        </template>
        <template v-else-if="mode === 'account'">
          <el-form-item label="学校"><el-select v-model="form.organizationId">
            <el-option v-for="item in schools" :key="item.id" :value="item.id" :label="item.organizationName" />
          </el-select></el-form-item>
          <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item>
          <el-form-item label="初始密码"><el-input v-model="form.password" type="password" autocomplete="new-password" show-password /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="账号">{{ form.username }}</el-form-item>
          <el-form-item label="业务模块"><el-select v-model="form.module">
            <el-option v-for="item in assignableModules" :key="item" :value="item" :label="name(item)" />
          </el-select></el-form-item>
          <el-form-item v-if="form.module === 'EDU_SUPERVISION'" label="权限">
            <el-select v-model="form.supervisionRole">
              <el-option value="VIEW" label="查询权限" />
              <el-option value="MANAGE" label="督导管理员" />
              <el-option value="SUPERVISOR" label="督导员" />
              <el-option value="TEACHER" label="授课与整改教师" />
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="form.module === 'EDU_TEACHER_PERSONNEL'" label="权限">
            <el-select v-model="form.personnelRole">
              <el-option value="VIEW" label="本校人事查询" />
              <el-option value="MANAGE" label="本校人事经办" />
              <el-option value="PERSONNEL_REVIEWER" label="校级人事审核员" />
              <el-option value="PERSONNEL_GROUP_REVIEWER" label="集团人事审核员（可审跨校调动）" />
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="form.module === 'COLLABORATION_OFFICE'" label="权限">
            <el-select v-model="form.officeRole">
              <el-option value="VIEW" label="查看本人流程和本校公文" />
              <el-option value="MANAGE" label="发起办公申请（首次授权建立员工档案）" />
              <el-option value="OFFICE_APPROVER" label="校级办公审批和资源管理" />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="权限"><el-select v-model="form.manage">
            <el-option :value="false" :label="form.module === 'CAMPUS_VENUE' ? '查询与本人预约' : '只读权限'" /><el-option :value="true" label="管理权限" />
          </el-select></el-form-item>
          <el-form-item label="操作"><el-radio-group v-model="form.enabled">
            <el-radio :value="true">授予</el-radio><el-radio :value="false">撤销</el-radio>
          </el-radio-group></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orgOptions } from '../../../api/admin'
import { getAdminRoles, hasAdminPermission, saveDeliveryContext } from '../../../store/auth'
import * as api from '../api'

const labels = { TERM_CALENDAR: '学期与校历', FACILITY_REPAIR: '故障报修', FACILITY_GOVERNANCE: '现有资产与保养' }
const titles = { school: '新增学校', account: '新增学校账号', grant: '分配模块权限' }
const name = code => status.value?.productNames?.[code] || labels[code] || code
const canManage = computed(() => getAdminRoles().some(role => ['SUPER_ADMIN', 'DELIVERY_GROUP_ADMIN'].includes(role.roleCode)))
const canReadShared = hasAdminPermission('education:shared-document:read')
const canReadAccommodation = hasAdminPermission('education:accommodation:view', 'education:accommodation:manage')
const canManageFamilyMeetings = hasAdminPermission('education:parent-meeting:manage')
const canReadFamily = hasAdminPermission('education:home-school:parent:view')
const canUsePersonnel = hasAdminPermission('education:teacher:business:view', 'education:teacher:business:manage',
  'education:teacher:business:review')
const canReadAnalytics = hasAdminPermission('education:data-center:view')
const canReadOffice = hasAdminPermission('workflow:instance:view', 'collaboration:office:apply', 'collaboration:office:manage')
const canReadKnowledge = hasAdminPermission('education:ai:knowledge:view', 'education:ai:knowledge:manage')
const canReadTeachingRun = hasAdminPermission('education:scheduling:view')
const canReadAdmissions = hasAdminPermission('education:admission:view')
const canReadGrades = hasAdminPermission('education:score:gradebook:view')
const canReadTeaching = hasAdminPermission('education:teaching:view', 'education:teaching:manage', 'education:teaching:review')
const canManageHomework = hasAdminPermission('education:homework:manage')
const canReadHomework = canManageHomework || hasAdminPermission('education:homework:view')
const canReadSupervision = hasAdminPermission('education:supervision:plan:view', 'education:supervision:plan:create')
const canObserve = hasAdminPermission('education:supervision:assignment:accept')
const canRectify = hasAdminPermission('education:supervision:rectification:create')
const canReadExam = hasAdminPermission('education:exam:plan:view', 'education:exam:plan:manage')
const canReadInternship = hasAdminPermission('education:internship:view')
const canReadVenue = hasAdminPermission('education:resource:venue:view', 'education:resource:venue:manage')
const canApplyVenue = hasAdminPermission('education:venue-reservation:apply', 'education:resource:venue:manage')
const status = ref(null)
const assignableModules = computed(() => (status.value?.modules || []).filter(code => code !== 'THIRD_PARTY_INTEGRATIONS'))
const accounts = ref([])
const schools = ref([])
const visible = ref(false)
const saving = ref(false)
const mode = ref('school')
const form = reactive({})
function describe(row) {
  const selections = (status.value?.modules || []).flatMap(code => [
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_VIEW_') ? name(code) + (code === 'CAMPUS_VENUE' ? '（查询与本人预约）' : '（只读）') : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_MANAGE_') ? name(code) + '（管理）' : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_OFFICE_APPROVER_') ? name(code) + '（校级办公审核）' : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_SUPERVISOR_') ? name(code) + '（督导员）' : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_TEACHER_') ? name(code) + '（授课与整改）' : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_PERSONNEL_REVIEWER_') ? name(code) + '（校级审核）' : '',
    row.roles.includes('DELIVERY_SCHOOL_' + code + '_PERSONNEL_GROUP_REVIEWER_') ? name(code) + '（集团审核）' : '',
  ]).filter(Boolean)
  return selections.join('、') || '—'
}
function open(type, row) {
  Object.keys(form).forEach(key => delete form[key])
  mode.value = type
  Object.assign(form, type === 'grant'
    ? { username: row.username, module: assignableModules.value[0], manage: false, supervisionRole: 'VIEW', personnelRole: 'VIEW', officeRole: 'VIEW', enabled: true }
    : type === 'account' ? { organizationId: schools.value[0]?.id, username: '', displayName: '', password: '' }
    : { code: '', name: '' })
  visible.value = true
}
async function load() {
  status.value = (await api.deliveryStatus()).data
  saveDeliveryContext(status.value)
  if (!canManage.value || !status.value.active) return
  ;[accounts.value, schools.value] = await Promise.all([
    api.delegatedAccounts().then(result => result.data),
    orgOptions().then(result => result.data.filter(item => item.organizationType === 'SCHOOL')),
  ])
}
async function save() {
  saving.value = true
  try {
    const { supervisionRole, personnelRole, officeRole, ...body } = form
    if (mode.value === 'grant' && body.module === 'EDU_SUPERVISION') {
      body.manage = supervisionRole === 'MANAGE'
      if (['SUPERVISOR', 'TEACHER'].includes(supervisionRole)) body.duty = supervisionRole
    }
    if (mode.value === 'grant' && body.module === 'EDU_TEACHER_PERSONNEL') {
      body.manage = personnelRole === 'MANAGE'
      if (['PERSONNEL_REVIEWER', 'PERSONNEL_GROUP_REVIEWER'].includes(personnelRole)) body.duty = personnelRole
    }
    if (mode.value === 'grant' && body.module === 'COLLABORATION_OFFICE') {
      body.manage = officeRole === 'MANAGE'
      if (officeRole === 'OFFICE_APPROVER') body.duty = officeRole
    }
    await { school: api.createSchool, account: api.createAccount, grant: api.updateGrant }[mode.value](body)
    delete form.password
    visible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.page { padding:24px; }
.details { margin:16px 0; }
.toolbar { display:flex; gap:10px; margin:0 0 12px; }
</style>
