<template>
  <section class="page">
    <header>
      <div><h2>住宿分配</h2><p>维护宿舍楼、房间、床位，并按学期人工分配学生；智能体只生成候选，不绕过本页确认。</p></div>
      <div class="header-actions"><el-button @click="downloadTemplate">下载导入模板</el-button><el-upload :show-file-list="false" accept=".xlsx,.XLSX" :http-request="uploadResources"><el-button>批量导入</el-button></el-upload><el-button type="primary" @click="buildingDialog = true">新增宿舍楼</el-button></div>
    </header>
    <el-alert type="info" :closable="false" title="住宿分配属于新生入学的独立阶段。先维护床位，再按学期逐人确认；同一学生或床位不能重复分配。" />
    <div class="toolbar">
      <el-select v-model="semesterCode" placeholder="选择学期" filterable style="width:280px" @change="loadAssignments">
        <el-option v-for="term in terms" :key="term.id || term.termCode" :label="`${term.termName || term.termCode}（${term.termCode}）`" :value="term.termCode" />
      </el-select>
      <el-button type="primary" @click="loadAssignments">查询住宿分配</el-button>
      <el-button @click="loadBeds">刷新可用床位</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="宿舍资源" name="resources">
        <el-empty v-if="!buildings.length" description="暂无宿舍楼，请先新增宿舍楼" />
        <el-card v-for="building in buildings" :key="building.id" class="building-card" shadow="never">
          <template #header><div class="card-head"><strong>{{ building.buildingName }}（{{ building.buildingCode }}）</strong><span>可用床位 {{ building.availableBedCount || 0 }} / {{ building.bedCount || 0 }}</span><el-button link type="primary" @click="openRoom(building)">新增房间</el-button></div></template>
          <el-table :data="building.rooms || []" size="small" border>
            <el-table-column prop="roomNo" label="房间" width="120" /><el-table-column prop="floorNo" label="楼层" width="80" /><el-table-column prop="capacity" label="容量" width="80" /><el-table-column prop="bedCount" label="床位数" width="90" /><el-table-column prop="availableBedCount" label="可用床位" width="100" /><el-table-column label="操作" width="260"><template #default="scope"><el-button link type="primary" @click="editRoom(building, scope.row)">编辑</el-button><el-button v-if="scope.row.bedCount < scope.row.capacity" link type="primary" @click="openBed(building, scope.row)">补充床位</el-button><span v-else class="muted">已满</span><el-button link type="danger" @click="removeRoom(scope.row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="人工分配" name="assign">
        <div class="assign-grid">
          <el-card shadow="never"><template #header>新增住宿分配</template><el-form label-width="90px"><el-form-item label="学生"><el-select v-model="assignForm.studentId" filterable remote :remote-method="loadStudents" placeholder="姓名或学号" style="width:100%"><el-option v-for="item in students" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="可用床位"><el-select v-model="assignForm.bedId" filterable placeholder="选择床位" style="width:100%"><el-option v-for="item in beds" :key="item.id" :label="`${item.buildingName} / ${item.roomNo} / ${item.bedNo}`" :value="item.id" /></el-select></el-form-item><el-button type="primary" :disabled="!assignForm.studentId || !assignForm.bedId || !semesterCode" @click="saveAssignment">确认分配</el-button></el-form></el-card>
          <el-card shadow="never"><template #header>本学期已分配</template><el-table :data="assignments" border><el-table-column prop="studentNo" label="学号" width="140" /><el-table-column prop="studentName" label="学生" width="110" /><el-table-column prop="buildingName" label="楼栋" /><el-table-column prop="roomNo" label="房间" /><el-table-column prop="bedNo" label="床位" /><el-table-column label="操作" width="90"><template #default="scope"><el-button link type="danger" @click="cancelAssignment(scope.row)">撤销</el-button></template></el-table-column></el-table></el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="buildingDialog" title="新增宿舍楼" width="520px"><el-form label-width="90px"><el-form-item label="楼栋编码"><el-input v-model="buildingForm.buildingCode" /></el-form-item><el-form-item label="楼栋名称"><el-input v-model="buildingForm.buildingName" /></el-form-item><el-form-item label="性别规则"><el-select v-model="buildingForm.genderPolicy"><el-option label="混合" value="MIXED" /><el-option label="男生" value="MALE" /><el-option label="女生" value="FEMALE" /></el-select></el-form-item></el-form><template #footer><el-button @click="buildingDialog=false">取消</el-button><el-button type="primary" @click="saveBuilding">保存</el-button></template></el-dialog>
    <el-dialog v-model="roomDialog" :title="editingRoom ? '编辑房间' : '新增房间'" width="520px"><el-form label-width="90px"><el-form-item label="楼栋">{{ activeBuilding?.buildingName }}</el-form-item><el-form-item label="房间号"><el-input v-model="roomForm.roomNo" /></el-form-item><el-form-item label="楼层"><el-input-number v-model="roomForm.floorNo" :min="1" /></el-form-item><el-form-item label="床位数"><el-input-number v-model="roomForm.capacity" :min="1" /></el-form-item></el-form><template #footer><el-button @click="roomDialog=false">取消</el-button><el-button type="primary" @click="saveRoom">保存</el-button></template></el-dialog>
    <el-dialog v-model="bedDialog" title="新增床位" width="420px"><el-form label-width="90px"><el-form-item label="房间">{{ activeRoom?.roomNo }}</el-form-item><el-form-item label="床位号"><el-input v-model="bedForm.bedNo" placeholder="如 01" /></el-form-item></el-form><template #footer><el-button @click="bedDialog=false">取消</el-button><el-button type="primary" @click="saveBed">保存</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { accommodationAssignments, accommodationBeds, accommodationBuildings, accommodationRooms, accommodationStudents, assignAccommodation, cancelAccommodation, createAccommodationBed, createAccommodationBuilding, createAccommodationRoom, deleteAccommodationRoom, downloadAccommodationImportTemplate, importAccommodationResources, updateAccommodationRoom } from '../api/accommodation'
import { listAcademicTerms } from '../../../api/admin'

const tab = ref('resources'); const semesterCode = ref(''); const terms = ref([]); const buildings = ref([]); const beds = ref([]); const assignments = ref([]); const students = ref([])
const buildingDialog = ref(false); const roomDialog = ref(false); const bedDialog = ref(false); const activeBuilding = ref(null); const activeRoom = ref(null); const editingRoom = ref(null)
const buildingForm = reactive({ buildingCode: '', buildingName: '', genderPolicy: 'MIXED' }); const roomForm = reactive({ roomNo: '', floorNo: 1, capacity: 6 }); const bedForm = reactive({ bedNo: '' }); const assignForm = reactive({ studentId: '', bedId: '' })
const unwrap = response => response.data?.content || response.data || []
const normalizeBuilding = item => ({ ...item, buildingCode: item.buildingCode ?? item.building_code, buildingName: item.buildingName ?? item.building_name, roomCount: item.roomCount ?? item.room_count ?? 0, bedCount: item.bedCount ?? item.bed_count ?? 0, availableBedCount: item.availableBedCount ?? item.available_bed_count ?? 0 })
const normalizeRoom = item => ({ ...item, roomNo: item.roomNo ?? item.room_no, floorNo: item.floorNo ?? item.floor_no, bedCount: Number(item.bedCount ?? item.bed_count ?? 0), capacity: Number(item.capacity ?? 0), availableBedCount: Number(item.availableBedCount ?? item.available_bed_count ?? 0) })
const normalizeStudent = item => ({ ...item, studentNo: item.studentNo ?? item.student_no, studentName: item.studentName ?? item.student_name })
const normalizeBed = item => ({ ...item, bedNo: item.bedNo ?? item.bed_no, roomNo: item.roomNo ?? item.room_no, buildingName: item.buildingName ?? item.building_name })
const normalizeAssignment = item => ({ ...item, studentNo: item.studentNo ?? item.student_no, studentName: item.studentName ?? item.student_name, buildingName: item.buildingName ?? item.building_name, roomNo: item.roomNo ?? item.room_no, bedNo: item.bedNo ?? item.bed_no })
const loadTerms = async () => {
  const result = await listAcademicTerms({ page: 0, size: 100 })
  terms.value = unwrap(result)
  const current = terms.value.find(item => item.currentTerm) || terms.value[0]
  if (current) {
    semesterCode.value = current.termCode
    await loadAssignments()
  }
}
const loadResources = async () => { const result = unwrap(await accommodationBuildings()); buildings.value = await Promise.all(result.map(async raw => { const item = normalizeBuilding(raw); return { ...item, rooms: unwrap(await accommodationRooms(item.id)).map(normalizeRoom) } })) }
const loadBeds = async () => { if (!semesterCode.value) return; beds.value = unwrap(await accommodationBeds(semesterCode.value, true)).map(normalizeBed) }
const loadAssignments = async () => { if (!semesterCode.value) return ElMessage.warning('请先选择学期'); assignments.value = unwrap(await accommodationAssignments(semesterCode.value)).map(normalizeAssignment); await loadBeds() }
const loadStudents = async keyword => { students.value = unwrap(await accommodationStudents(keyword)).map(normalizeStudent) }
const downloadTemplate = async () => { const blob = await downloadAccommodationImportTemplate(); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'Chronos-宿舍资源批量导入模板.xlsx'; anchor.click(); URL.revokeObjectURL(url) }
const uploadResources = async ({ file }) => {
  try {
    const preview = await importAccommodationResources(file, true)
    const result = preview?.data || {}
    if (result.errors?.length) return ElMessage.error(`校验失败：${result.errors[0]}`)
    await ElMessageBox.confirm(`校验通过，将处理 ${result.successCount || 0} 行宿舍资源并自动补齐床位，确认导入？`, '导入确认', { type: 'warning' })
    const imported = await importAccommodationResources(file, false)
    ElMessage.success(`宿舍资源导入成功，共处理 ${imported?.data?.successCount || 0} 行`)
    await loadResources(); await loadBeds()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '宿舍资源导入失败')
  }
}
const saveBuilding = async () => { await createAccommodationBuilding(buildingForm); buildingDialog.value = false; Object.assign(buildingForm, { buildingCode: '', buildingName: '', genderPolicy: 'MIXED' }); await loadResources(); ElMessage.success('宿舍楼已保存') }
const openRoom = building => { activeBuilding.value = building; editingRoom.value = null; Object.assign(roomForm, { roomNo: '', floorNo: 1, capacity: 6 }); roomDialog.value = true }
const editRoom = (building, room) => { activeBuilding.value = building; activeRoom.value = room; editingRoom.value = room; Object.assign(roomForm, { roomNo: room.roomNo, floorNo: room.floorNo, capacity: room.capacity }); roomDialog.value = true }
const saveRoom = async () => { if (editingRoom.value) await updateAccommodationRoom(editingRoom.value.id, roomForm); else await createAccommodationRoom(activeBuilding.value.id, roomForm); roomDialog.value = false; await loadResources(); ElMessage.success(editingRoom.value ? '房间已更新' : '房间已保存'); editingRoom.value = null }
const removeRoom = async room => { try { await ElMessageBox.confirm(`确认删除 ${room.roomNo} 房间及其未分配床位？已有住宿分配的房间不能删除。`, '删除确认', { type: 'warning' }); await deleteAccommodationRoom(room.id); ElMessage.success('房间已删除'); await loadResources() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '房间删除失败') } }
const openBed = (building, room) => { activeBuilding.value = building; activeRoom.value = room; Object.assign(bedForm, { bedNo: '' }); bedDialog.value = true }
const saveBed = async () => { await createAccommodationBed(activeRoom.value.id, bedForm); bedDialog.value = false; await loadResources(); ElMessage.success('床位已保存') }
const saveAssignment = async () => { await assignAccommodation({ semesterCode: semesterCode.value, ...assignForm }); Object.assign(assignForm, { studentId: '', bedId: '' }); ElMessage.success('住宿分配已保存'); await loadAssignments() }
const cancelAssignment = async row => { await ElMessageBox.confirm(`确认撤销 ${row.studentName} 的住宿分配？`, '撤销确认', { type: 'warning' }); await cancelAccommodation(row.id); ElMessage.success('住宿分配已撤销'); await loadAssignments() }
onMounted(async () => { await Promise.all([loadTerms(), loadResources()]) })
</script>

<style scoped>
.page { padding: 24px; } header, .toolbar, .card-head { display: flex; align-items: center; gap: 12px; } header { justify-content: space-between; margin-bottom: 18px; } .header-actions { display: flex; align-items: center; gap: 8px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; } .toolbar { margin: 14px 0; } .building-card { margin-top: 14px; } .card-head { justify-content: space-between; } .card-head span { color: #84909a; margin-left: auto; } .assign-grid { display: grid; grid-template-columns: minmax(300px, 380px) 1fr; gap: 16px; margin-top: 14px; }
</style>
