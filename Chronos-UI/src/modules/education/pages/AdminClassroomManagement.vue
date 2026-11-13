<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../../api/http'
import { deleteClassroom, dictionaryOptions } from '../../../api/admin'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import AcademicCatalogImport from '../components/AcademicCatalogImport.vue'

const endpoint = '/admin/education/classroom-management'
const createPermissions = ['education:resource:venue:create', 'education:resource:venue:manage']
const updatePermissions = ['education:resource:venue:update', 'education:resource:venue:manage']
const deletePermissions = ['education:resource:venue:delete', 'education:resource:venue:manage']
const route = useRoute()
const organizationId = ref(typeof route.query.organizationId === 'string' ? route.query.organizationId : '')
const emptyCatalog = () => ({ buildings: [], floors: [], rooms: [], total: 0 })
const catalog = ref(emptyCatalog())
const selection = ref({ kind: 'all' })
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const roomTypes = ref([])
const dialog = ref(false)
const busy = ref(false)
const kind = ref('')
const form = reactive({})
const titles = { buildings: '教学楼', floors: '楼层', rooms: '教室' }
const url = (path = '', id = organizationId.value) => endpoint + path + '?' + new URLSearchParams({ organizationId: id })

const tree = computed(() => [
  { key: 'all', kind: 'all', label: '全部教室' },
  { key: 'unclassified', kind: 'unclassified', label: '待归类' },
  ...catalog.value.buildings.map(building => ({
    ...building, key: building.id, kind: 'buildings', label: building.buildingName,
    children: catalog.value.floors.filter(floor => floor.buildingId === building.id).map(floor => ({
      ...floor, key: floor.id, kind: 'floors', label: `${floor.floorNo} 层`,
    })),
  })),
])
const floorsById = computed(() => new Map(catalog.value.floors.map(floor => [floor.id, floor])))
const floorOptions = computed(() => catalog.value.floors.filter(floor => floor.buildingId === form.buildingId))
const typeName = code => roomTypes.value.find(item => item.dictValue === code)?.dictName || code
const selectedBuilding = computed(() => selection.value.kind === 'buildings' ? selection.value.id : selection.value.buildingId)

let version = 0
async function reload() {
  const current = ++version
  const id = organizationId.value
  if (!id) return
  const selected = selection.value
  const params = new URLSearchParams({ page: page.value - 1, size: pageSize.value })
  if (selected.kind === 'buildings') params.set('buildingId', selected.id)
  if (selected.kind === 'floors') params.set('floorId', selected.id)
  if (selected.kind === 'unclassified') params.set('unclassified', 'true')
  loading.value = true
  try {
    const response = await http.get(url('', id) + '&' + params)
    if (version !== current || id !== organizationId.value) return
    catalog.value = response.data || emptyCatalog()
    if (selected.id) {
      const existing = catalog.value[selected.kind].find(item => item.id === selected.id)
      if (!existing) return selectLocation({ kind: 'all' })
      selection.value = { ...selected, ...existing }
    }
    const lastPage = Math.max(1, Math.ceil(catalog.value.total / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return reload()
    }
  } finally {
    if (current === version) loading.value = false
  }
}
function selectLocation(node) {
  selection.value = node
  page.value = 1
  catalog.value = { ...catalog.value, rooms: [], total: 0 }
  return reload()
}
watch(organizationId, () => {
  catalog.value = emptyCatalog()
  dialog.value = false
  loading.value = false
  selectLocation({ kind: 'all' })
}, { immediate: true })
dictionaryOptions('EDU_ROOM_TYPE').then(response => { roomTypes.value = response.data || [] })

function open(entity, row = null) {
  for (const key of Object.keys(form)) delete form[key]
  kind.value = entity
  if (entity === 'rooms') {
    const floorId = row?.floorId || (selection.value.kind === 'floors' ? selection.value.id : '')
    Object.assign(form, { capacity: 40, roomType: 'STANDARD', enabled: true }, row,
      { floorId, buildingId: floorsById.value.get(floorId)?.buildingId || selectedBuilding.value || '' })
  } else {
    Object.assign(form, row || { buildingId: selectedBuilding.value, maxFloorNo: 1 })
  }
  dialog.value = true
}

async function save() {
  if (kind.value === 'rooms' && !form.floorId) return ElMessage.warning('请选择教室所在楼层')
  const id = organizationId.value
  const path = '/' + kind.value + (form.id ? '/' + encodeURIComponent(form.id) : '')
  busy.value = true
  try {
    const response = await (form.id ? http.put(url(path, id), form) : http.post(url(path, id), form))
    if (id !== organizationId.value) return
    dialog.value = false
    if (kind.value === 'floors' && !form.id) {
      ElMessage.success(response.data ? `已新增 ${response.data} 个楼层` : '该范围内的楼层已齐全')
    } else {
      ElMessage.success('保存成功')
    }
    await reload()
  } finally {
    busy.value = false
  }
}

async function remove(entity, id) {
  const campus = organizationId.value
  try {
    await ElMessageBox.confirm(`确认删除该${titles[entity]}？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  if (campus !== organizationId.value) return
  if (entity === 'rooms') await deleteClassroom(id)
  else await http.delete(url('/' + entity + '/' + encodeURIComponent(id), campus))
  ElMessage.success('删除成功')
  await reload()
}
</script>

<template>
  <section class="page">
    <header><h2>教室管理</h2><p>按校区、教学楼和楼层维护排课、考试与预约使用的教室资源。</p></header>
    <div class="toolbar">
      <OrganizationSelect v-model="organizationId" school-or-campus-only style="width: 260px" />
      <el-button v-permission="createPermissions" :disabled="!organizationId" @click="open('buildings')">新增教学楼</el-button>
      <el-button v-permission="createPermissions" :disabled="!selectedBuilding" @click="open('floors')">新增楼层</el-button>
      <el-button v-permission="createPermissions" :disabled="!catalog.floors.length" type="primary" @click="open('rooms')">新增教室</el-button>
      <span v-permission="createPermissions">
        <AcademicCatalogImport kind="CLASSROOMS" label="教室" :endpoint="endpoint + '/import'" :organization-id="organizationId" @imported="reload" />
      </span>
    </div>
    <div v-if="organizationId" class="layout">
      <aside>
        <el-tree class="classroom-tree" :data="tree" node-key="key" :current-node-key="selection.id || selection.kind" highlight-current default-expand-all :expand-on-click-node="false" @node-click="selectLocation" />
        <div v-if="selection.id" class="tree-actions">
          <el-button v-permission="updatePermissions" link @click="open(selection.kind, selection)">编辑{{ titles[selection.kind] }}</el-button>
          <el-button v-permission="deletePermissions" link type="danger" @click="remove(selection.kind, selection.id)">删除</el-button>
        </div>
      </aside>
      <div class="room-list">
      <el-table v-loading="loading" :data="catalog.rooms" border>
        <el-table-column prop="roomCode" label="教室编码" min-width="130" />
        <el-table-column prop="roomName" label="教室名称" min-width="130" />
        <el-table-column prop="buildingName" label="教学楼" min-width="110" />
        <el-table-column label="楼层" width="90"><template #default="{ row }">{{ floorsById.get(row.floorId)?.floorNo ?? '待归类' }}</template></el-table-column>
        <el-table-column prop="roomNumber" label="房间号" width="100" />
        <el-table-column prop="capacity" label="容量" width="75" />
        <el-table-column label="类型" min-width="100"><template #default="{ row }">{{ typeName(row.roomType) }}</template></el-table-column>
        <el-table-column prop="equipmentCodes" label="设备" min-width="105" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button v-permission="updatePermissions" link type="primary" @click="open('rooms', row)">{{ row.floorId ? '编辑' : '归类' }}</el-button>
            <el-button v-permission="deletePermissions" link type="danger" @click="remove('rooms', row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="room-pagination"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="catalog.total"
        layout="total, sizes, prev, pager, next"
        @current-change="reload"
        @size-change="page = 1; reload()"
      />
      </div>
    </div>
    <el-empty v-else description="请先选择校区" />

    <el-dialog v-model="dialog" :title="(form.id ? '编辑' : '新增') + (titles[kind] || '')" width="560px">
      <el-form label-width="95px">
        <template v-if="kind === 'buildings'">
          <el-form-item label="楼栋编码"><el-input v-model="form.buildingCode" maxlength="64" /></el-form-item>
          <el-form-item label="楼栋名称"><el-input v-model="form.buildingName" maxlength="128" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="教学楼">
            <el-select v-model="form.buildingId" filterable :disabled="kind === 'floors' && !!form.id" @change="form.floorId = ''">
              <el-option v-for="item in catalog.buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
            </el-select>
          </el-form-item>
          <template v-if="kind === 'floors'">
            <el-form-item v-if="form.id" label="楼层"><el-input-number v-model="form.floorNo" :precision="0" /></el-form-item>
            <el-form-item v-else label="最大楼层">
              <el-input-number v-model="form.maxFloorNo" :min="1" :max="200" :precision="0" />
              <div class="floor-hint">自动补齐 1 层到最大楼层，已有楼层会保留。</div>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="楼层">
              <el-select v-model="form.floorId">
                <el-option v-for="item in floorOptions" :key="item.id" :label="`${item.floorNo} 层`" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="房间号"><el-input v-model="form.roomNumber" maxlength="64" /></el-form-item>
            <el-form-item label="教室编码"><el-input v-model="form.roomCode" maxlength="64" /></el-form-item>
            <el-form-item label="教室名称"><el-input v-model="form.roomName" maxlength="128" /></el-form-item>
            <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" :precision="0" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="form.roomType">
                <el-option v-for="item in roomTypes" :key="item.dictValue" :label="item.dictName" :value="item.dictValue" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备编码"><el-input v-model="form.equipmentCodes" maxlength="1000" /></el-form-item>
            <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header h2 { margin: 0 0 6px; }
header p { margin: 0 0 16px; color: #64748b; }
.toolbar, .tree-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.toolbar :deep(.el-button + .el-button), .tree-actions :deep(.el-button + .el-button) { margin-left: 0; }
.layout { display: grid; grid-template-columns: 200px minmax(0, 1fr); gap: 10px; }
aside { border: 1px solid #e3e8ed; padding: 10px; }
.classroom-tree :deep(.el-tree-node.is-current > .el-tree-node__content),
.classroom-tree :deep(.el-tree-node.is-current > .el-tree-node__content:hover) {
  background-color: var(--chronos-admin-primary);
  color: #fff;
  border-radius: 6px;
}
.classroom-tree :deep(.el-tree-node.is-current > .el-tree-node__content > .el-tree-node__expand-icon:not(.is-leaf)) {
  color: inherit;
}
.tree-actions { margin-top: 10px; }
.room-list { min-width: 0; }
.room-pagination { justify-content: flex-end; margin-top: 10px; }
.floor-hint { width: 100%; color: #64748b; }
</style>
