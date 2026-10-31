<template>
  <section class="page">
    <header><div><h2>实习实训管理</h2><p>管理单位准入、岗位、三方资料、学生安排、过程巡查、考核与安全事件。</p></div></header>
    <el-alert type="warning" :closable="false" title="单位、学生安排和考核均须异人审批；协议、知情同意与保险凭证缺一不可。" />
    <div class="query-bar">
      <el-select v-model="schoolId" placeholder="选择学校" filterable style="width:300px" @change="reload">
        <el-option v-for="item in schools" :key="item.id" :label="item.organizationName || item.orgName" :value="item.id" />
      </el-select>
      <el-select v-model="termCode" placeholder="选择学期" filterable style="width:280px" @change="reloadPlacements">
        <el-option v-for="item in terms" :key="item.id" :label="`${item.termName}（${item.termCode}）`" :value="item.termCode" />
      </el-select>
      <el-button @click="reload">刷新</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="实习单位" name="companies">
        <div class="tab-actions"><el-button type="primary" @click="companyDialog = true">新增单位</el-button></div>
        <el-table :data="companies" border>
          <el-table-column prop="companyCode" label="单位编码" width="150" /><el-table-column prop="companyName" label="单位名称" min-width="210" />
          <el-table-column prop="creditCode" label="统一信用代码" width="180" /><el-table-column prop="contactName" label="联系人" width="100" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" /><el-table-column prop="riskLevel" label="风险" width="90" /><el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="操作" min-width="210"><template #default="scope">
            <el-button v-if="['DRAFT','REJECTED'].includes(scope.row.status)" link type="primary" @click="submitCompany(scope.row)">提交准入</el-button>
            <template v-if="scope.row.status === 'SUBMITTED'"><el-button link type="success" @click="decideCompany(scope.row, true)">通过</el-button><el-button link type="danger" @click="decideCompany(scope.row, false)">驳回</el-button></template>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="实习岗位" name="positions">
        <div class="tab-actions"><el-button type="primary" :disabled="approvedCompanies.length === 0" @click="positionDialog = true">新增岗位</el-button></div>
        <el-table :data="positions" border><el-table-column prop="positionName" label="岗位名称" min-width="180" /><el-table-column label="实习单位" min-width="200"><template #default="scope">{{ companyName(scope.row.companyId) }}</template></el-table-column><el-table-column prop="capacity" label="容量" width="90" /><el-table-column prop="mentorName" label="企业导师" width="120" /><el-table-column prop="mentorPhone" label="导师电话" width="140" /><el-table-column prop="safetyRequirements" label="安全要求" min-width="200" /><el-table-column prop="status" label="状态" width="100" /></el-table>
      </el-tab-pane>

      <el-tab-pane label="学生实习安排" name="placements">
        <div class="tab-actions"><el-button type="primary" :disabled="!termCode || activePositions.length === 0" @click="openPlacement">新增安排</el-button></div>
        <el-table :data="placements" border highlight-current-row @current-change="selectPlacement">
          <el-table-column label="学生" min-width="180"><template #default="scope">{{ studentName(scope.row.studentId) }}</template></el-table-column><el-table-column label="单位 / 岗位" min-width="240"><template #default="scope">{{ companyName(scope.row.companyId) }} / {{ positionName(scope.row.positionId) }}</template></el-table-column>
          <el-table-column prop="startDate" label="开始日期" width="120" /><el-table-column prop="endDate" label="结束日期" width="120" /><el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="材料" width="180"><template #default="scope"><el-tag size="small">协议</el-tag> <el-tag size="small">知情同意</el-tag> <el-tag size="small">保险</el-tag></template></el-table-column>
          <el-table-column label="操作" min-width="250"><template #default="scope">
            <el-button v-if="['DRAFT','REJECTED'].includes(scope.row.status)" link type="primary" @click="submitPlacement(scope.row)">提交</el-button>
            <template v-if="scope.row.status === 'SUBMITTED'"><el-button link type="success" @click="decidePlacement(scope.row, true)">批准</el-button><el-button link type="danger" @click="decidePlacement(scope.row, false)">驳回</el-button></template>
            <el-button v-if="scope.row.status === 'APPROVED'" link type="success" @click="activatePlacement(scope.row)">开始实习</el-button>
            <el-button v-if="['APPROVED','ACTIVE'].includes(scope.row.status)" link @click="openProcess(scope.row)">过程管理</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="过程与安全" name="process">
        <el-empty v-if="!selectedPlacement" description="请先在“学生实习安排”中选择一条记录" />
        <template v-else>
          <el-descriptions border :column="3" class="selection"><el-descriptions-item label="学生">{{ studentName(selectedPlacement.studentId) }}</el-descriptions-item><el-descriptions-item label="实习状态">{{ selectedPlacement.status }}</el-descriptions-item><el-descriptions-item label="快照摘要">{{ selectedPlacement.snapshotHash || '尚未提交' }}</el-descriptions-item></el-descriptions>
          <div class="section-title"><h3>日志与巡查</h3><el-button type="primary" @click="activityDialog = true">新增过程记录</el-button></div>
          <el-table :data="activities" border><el-table-column prop="activityType" label="类型" width="150" /><el-table-column prop="activityDate" label="日期" width="120" /><el-table-column prop="content" label="内容" min-width="260" /><el-table-column prop="result" label="结果" /><el-table-column prop="riskLevel" label="风险" width="90" /><el-table-column prop="status" label="状态" width="110" /></el-table>
          <div class="section-title"><h3>实习考核</h3><el-button v-if="!assessment" type="primary" @click="assessmentDialog = true">提交考核</el-button></div>
          <el-descriptions v-if="assessment" border :column="4"><el-descriptions-item label="企业评分">{{ assessment.companyScore }}</el-descriptions-item><el-descriptions-item label="学校评分">{{ assessment.schoolScore }}</el-descriptions-item><el-descriptions-item label="最终成绩">{{ assessment.finalScore }}</el-descriptions-item><el-descriptions-item label="状态">{{ assessment.status }}</el-descriptions-item></el-descriptions>
          <div v-if="assessment?.status === 'SUBMITTED'" class="decision"><el-button type="success" @click="decideAssessmentRow(true)">批准考核</el-button><el-button type="danger" @click="decideAssessmentRow(false)">驳回考核</el-button></div>
          <div class="section-title"><h3>安全事件</h3><el-button type="danger" @click="incidentDialog = true">上报事件</el-button></div>
          <el-table :data="incidents" border><el-table-column label="发生时间" width="180"><template #default="scope">{{ formatDateTime(scope.row.occurredAt) }}</template></el-table-column><el-table-column prop="severity" label="等级" width="100" /><el-table-column prop="description" label="事件描述" min-width="230" /><el-table-column prop="emergencyAction" label="应急处置" min-width="200" /><el-table-column prop="status" label="状态" width="110" /><el-table-column label="操作" width="100"><template #default="scope"><el-button v-if="scope.row.status !== 'CLOSED'" link type="success" @click="closeIncidentRow(scope.row)">结案</el-button></template></el-table-column></el-table>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="companyDialog" title="新增实习单位" width="640px"><el-form label-width="110px"><el-form-item label="单位编码"><el-input v-model="companyForm.companyCode" /></el-form-item><el-form-item label="单位名称"><el-input v-model="companyForm.companyName" /></el-form-item><el-form-item label="信用代码"><el-input v-model="companyForm.creditCode" /></el-form-item><el-form-item label="联系人"><el-input v-model="companyForm.contactName" /></el-form-item><el-form-item label="联系电话"><el-input v-model="companyForm.contactPhone" /></el-form-item><el-form-item label="地址"><el-input v-model="companyForm.address" /></el-form-item><el-form-item label="风险等级"><el-select v-model="companyForm.riskLevel"><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" /></el-select></el-form-item></el-form><template #footer><el-button @click="companyDialog=false">取消</el-button><el-button type="primary" @click="saveCompany">保存</el-button></template></el-dialog>
    <el-dialog v-model="positionDialog" title="新增实习岗位" width="640px"><el-form label-width="110px"><el-form-item label="实习单位"><el-select v-model="positionForm.companyId" style="width:100%"><el-option v-for="item in approvedCompanies" :key="item.id" :label="item.companyName" :value="item.id" /></el-select></el-form-item><el-form-item label="岗位名称"><el-input v-model="positionForm.positionName" /></el-form-item><el-form-item label="岗位容量"><el-input-number v-model="positionForm.capacity" :min="1" /></el-form-item><el-form-item label="企业导师"><el-input v-model="positionForm.mentorName" /></el-form-item><el-form-item label="导师电话"><el-input v-model="positionForm.mentorPhone" /></el-form-item><el-form-item label="安全要求"><el-input v-model="positionForm.safetyRequirements" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="positionDialog=false">取消</el-button><el-button type="primary" @click="savePosition">保存</el-button></template></el-dialog>
    <el-dialog v-model="placementDialog" title="新增学生实习安排" width="680px"><el-form label-width="120px"><el-form-item label="学生"><el-select v-model="placementForm.studentId" filterable remote :remote-method="searchStudents" style="width:100%"><el-option v-for="item in students" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="实习岗位"><el-select v-model="placementForm.positionId" style="width:100%"><el-option v-for="item in activePositions" :key="item.id" :label="`${companyName(item.companyId)} / ${item.positionName}`" :value="item.id" /></el-select></el-form-item><el-form-item label="实习日期"><el-date-picker v-model="placementDates" type="daterange" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="实习协议文件ID"><el-input v-model="placementForm.agreementFileId" /></el-form-item><el-form-item label="知情同意文件ID"><el-input v-model="placementForm.consentFileId" /></el-form-item><el-form-item label="保险凭证文件ID"><el-input v-model="placementForm.insuranceFileId" /></el-form-item></el-form><template #footer><el-button @click="placementDialog=false">取消</el-button><el-button type="primary" @click="savePlacement">保存草稿</el-button></template></el-dialog>
    <el-dialog v-model="activityDialog" title="新增过程记录" width="620px"><el-form label-width="100px"><el-form-item label="记录类型"><el-select v-model="activityForm.activityType"><el-option label="学生日志" value="STUDENT_LOG" /><el-option label="学校巡查" value="SCHOOL_INSPECTION" /></el-select></el-form-item><el-form-item label="记录日期"><el-date-picker v-model="activityForm.activityDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="内容"><el-input v-model="activityForm.content" type="textarea" /></el-form-item><el-form-item label="结果"><el-input v-model="activityForm.result" /></el-form-item><el-form-item label="风险等级"><el-select v-model="activityForm.riskLevel"><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" /></el-select></el-form-item></el-form><template #footer><el-button @click="activityDialog=false">取消</el-button><el-button type="primary" @click="saveActivity">保存</el-button></template></el-dialog>
    <el-dialog v-model="assessmentDialog" title="提交实习考核" width="600px"><el-form label-width="100px"><el-form-item label="企业评分"><el-input-number v-model="assessmentForm.companyScore" :min="0" :max="100" /></el-form-item><el-form-item label="学校评分"><el-input-number v-model="assessmentForm.schoolScore" :min="0" :max="100" /></el-form-item><el-form-item label="最终成绩"><el-input-number v-model="assessmentForm.finalScore" :min="0" :max="100" /></el-form-item><el-form-item label="评语"><el-input v-model="assessmentForm.comment" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="assessmentDialog=false">取消</el-button><el-button type="primary" @click="saveAssessment">提交审批</el-button></template></el-dialog>
    <el-dialog v-model="incidentDialog" title="上报实习安全事件" width="640px"><el-form label-width="100px"><el-form-item label="事件等级"><el-select v-model="incidentForm.severity"><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" /><el-option label="重大" value="CRITICAL" /></el-select></el-form-item><el-form-item label="事件描述"><el-input v-model="incidentForm.description" type="textarea" /></el-form-item><el-form-item label="应急处置"><el-input v-model="incidentForm.emergencyAction" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="incidentDialog=false">取消</el-button><el-button type="danger" @click="saveIncident">确认上报</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAcademicTerms, listEducationStudents, lookupEducationStudents, orgList } from '../../../api/admin'
import { activateInternshipPlacement, closeInternshipIncident, createInternshipActivity, createInternshipCompany, createInternshipPlacement, createInternshipPosition, decideInternshipAssessment, decideInternshipCompany, decideInternshipPlacement, internshipActivities, internshipAssessment, internshipCompanies, internshipIncidents, internshipPlacements, internshipPositions, reportInternshipIncident, submitInternshipAssessment, submitInternshipCompany, submitInternshipPlacement } from '../api/internships'

const unwrap = response => response?.data?.content || response?.data || []
const errorMessage = error => error?.response?.data?.msg || error?.message || '操作失败'
const tab = ref('companies'); const schoolId = ref(''); const termCode = ref('')
const schools = ref([]); const terms = ref([]); const students = ref([]); const companies = ref([]); const positions = ref([]); const placements = ref([])
const studentLabels = ref(new Map())
const selectedPlacement = ref(null); const activities = ref([]); const assessment = ref(null); const incidents = ref([])
const companyDialog = ref(false); const positionDialog = ref(false); const placementDialog = ref(false); const activityDialog = ref(false); const assessmentDialog = ref(false); const incidentDialog = ref(false)
const companyForm = reactive({ companyCode: '', companyName: '', creditCode: '', contactName: '', contactPhone: '', address: '', riskLevel: 'LOW' })
const positionForm = reactive({ companyId: '', positionName: '', capacity: 1, mentorName: '', mentorPhone: '', safetyRequirements: '' })
const placementForm = reactive({ studentId: '', positionId: '', agreementFileId: '', consentFileId: '', insuranceFileId: '' }); const placementDates = ref([])
const activityForm = reactive({ activityType: 'STUDENT_LOG', activityDate: '', content: '', result: '', riskLevel: 'LOW' })
const assessmentForm = reactive({ companyScore: 0, schoolScore: 0, finalScore: 0, comment: '' })
const incidentForm = reactive({ severity: 'LOW', description: '', emergencyAction: '' })
const approvedCompanies = computed(() => companies.value.filter(item => item.status === 'APPROVED'))
const activePositions = computed(() => positions.value.filter(item => item.status === 'ACTIVE' && approvedCompanies.value.some(company => company.id === item.companyId)))
const companyName = id => companies.value.find(item => item.id === id)?.companyName || id
const positionName = id => positions.value.find(item => item.id === id)?.positionName || id
const studentName = id => studentLabels.value.get(id) || '学生信息不可见'
const formatDateTime = value => value ? String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/Z$/, '') : '-'
const reloadPlacements = async () => {
  if (!schoolId.value || !termCode.value) return
  placements.value = unwrap(await internshipPlacements(schoolId.value, termCode.value))
  const ids = [...new Set(placements.value.map(item => item.studentId).filter(Boolean))]
  const batches = Array.from({ length: Math.ceil(ids.length / 100) }, (_, index) => ids.slice(index * 100, index * 100 + 100))
  const results = batches.length ? (await Promise.all(batches.map(batch => lookupEducationStudents(batch)))).flatMap(unwrap) : []
  studentLabels.value = new Map(results.map(item => [item.id, `${item.studentName}（${item.studentNo}）`]))
}
const reload = async () => { if (!schoolId.value) return; try { [companies.value, positions.value] = await Promise.all([internshipCompanies(schoolId.value).then(unwrap), internshipPositions(schoolId.value).then(unwrap)]); await reloadPlacements() } catch (error) { ElMessage.error(errorMessage(error)) } }
const searchStudents = async keyword => { students.value = unwrap(await listEducationStudents({ page: 0, size: 10, keyword, schoolId: schoolId.value })) }
const saveCompany = async () => { try { await createInternshipCompany({ schoolId: schoolId.value, ...companyForm }); companyDialog.value = false; ElMessage.success('实习单位草稿已创建'); await reload() } catch (error) { ElMessage.error(errorMessage(error)) } }
const submitCompany = async row => { try { await submitInternshipCompany(row.id); ElMessage.success('准入申请已提交'); await reload() } catch (error) { ElMessage.error(errorMessage(error)) } }
const decideCompany = async (row, approved) => { try { let comment = ''; if (!approved) comment = (await ElMessageBox.prompt('填写驳回原因', '单位准入审核')).value; await decideInternshipCompany(row.id, { approved, comment }); ElMessage.success(approved ? '单位准入已批准' : '单位准入已驳回'); await reload() } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(errorMessage(error)) } }
const savePosition = async () => { try { await createInternshipPosition({ schoolId: schoolId.value, ...positionForm }); positionDialog.value = false; ElMessage.success('实习岗位已创建'); await reload() } catch (error) { ElMessage.error(errorMessage(error)) } }
const openPlacement = async () => { await searchStudents(''); placementDialog.value = true }
const savePlacement = async () => { try { const position = positions.value.find(item => item.id === placementForm.positionId); await createInternshipPlacement({ schoolId: schoolId.value, termCode: termCode.value, ...placementForm, companyId: position?.companyId, startDate: placementDates.value?.[0], endDate: placementDates.value?.[1] }); placementDialog.value = false; ElMessage.success('学生实习安排草稿已创建'); await reloadPlacements() } catch (error) { ElMessage.error(errorMessage(error)) } }
const submitPlacement = async row => { try { await submitInternshipPlacement(row.id); ElMessage.success('实习安排已提交'); await reloadPlacements() } catch (error) { ElMessage.error(errorMessage(error)) } }
const decidePlacement = async (row, approved) => { try { let comment = ''; if (!approved) comment = (await ElMessageBox.prompt('填写驳回原因', '实习安排审核')).value; await decideInternshipPlacement(row.id, { approved, comment }); ElMessage.success(approved ? '实习安排已批准' : '实习安排已驳回'); await reloadPlacements() } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(errorMessage(error)) } }
const activatePlacement = async row => { try { await activateInternshipPlacement(row.id); ElMessage.success('实习已开始'); await reloadPlacements() } catch (error) { ElMessage.error(errorMessage(error)) } }
const selectPlacement = row => { if (row) selectedPlacement.value = row }
const loadProcess = async () => { if (!selectedPlacement.value) return; const [activityResult, assessmentResult, incidentResult] = await Promise.all([internshipActivities(selectedPlacement.value.id), internshipAssessment(selectedPlacement.value.id), internshipIncidents(selectedPlacement.value.id)]); activities.value = unwrap(activityResult); assessment.value = assessmentResult?.data || null; incidents.value = unwrap(incidentResult) }
const openProcess = async row => { selectedPlacement.value = row; tab.value = 'process'; try { await loadProcess() } catch (error) { ElMessage.error(errorMessage(error)) } }
const saveActivity = async () => { try { await createInternshipActivity(selectedPlacement.value.id, activityForm); activityDialog.value = false; ElMessage.success('过程记录已保存'); await loadProcess() } catch (error) { ElMessage.error(errorMessage(error)) } }
const saveAssessment = async () => { try { await submitInternshipAssessment(selectedPlacement.value.id, assessmentForm); assessmentDialog.value = false; ElMessage.success('实习考核已提交审批'); await loadProcess() } catch (error) { ElMessage.error(errorMessage(error)) } }
const decideAssessmentRow = async approved => { try { await decideInternshipAssessment(assessment.value.id, { approved, comment: '' }); ElMessage.success(approved ? '实习考核已批准' : '实习考核已驳回'); await loadProcess(); await reloadPlacements() } catch (error) { ElMessage.error(errorMessage(error)) } }
const saveIncident = async () => { try { await reportInternshipIncident(selectedPlacement.value.id, incidentForm); incidentDialog.value = false; ElMessage.success('安全事件已上报'); await loadProcess() } catch (error) { ElMessage.error(errorMessage(error)) } }
const closeIncidentRow = async row => { try { const comment = (await ElMessageBox.prompt('填写结案说明', '安全事件结案')).value; await closeInternshipIncident(row.id, comment); ElMessage.success('安全事件已结案'); await loadProcess() } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(errorMessage(error)) } }
onMounted(async () => { try { const [orgs, termResult] = await Promise.all([orgList({ page: 0, size: 500 }), listAcademicTerms({ page: 0, size: 100 })]); schools.value = unwrap(orgs).filter(item => (item.organizationType || item.orgType) === 'SCHOOL'); terms.value = unwrap(termResult); schoolId.value = schools.value[0]?.id || ''; termCode.value = (terms.value.find(item => item.currentTerm) || terms.value[0])?.termCode || ''; await reload() } catch (error) { ElMessage.error(errorMessage(error)) } })
</script>

<style scoped>
.page { padding: 24px; } header { margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #7c8793; } .query-bar { display: flex; gap: 10px; align-items: center; margin: 16px 0 10px; } .tab-actions, .section-title { display: flex; justify-content: space-between; align-items: center; margin: 0 0 10px; } .section-title { margin-top: 20px; } .section-title h3 { margin: 0; } .selection { margin-bottom: 16px; } .decision { margin-top: 10px; text-align: right; }
</style>
