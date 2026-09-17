<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMeetingRoom,
  deleteMeetingRoom,
  listMeetingRooms,
  listMeetingParticipantOptions,
  updateMeetingRoom,
} from '../../../api/admin'

const rooms = ref([])
const users = ref([])
const dialogVisible = ref(false)
const busy = ref(false)
const form = reactive({})
const unwrap = response => response?.data?.content || response?.data || []

function resetForm(row) {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, row ? { ...row } : {
    capacity: 20,
    approvalMode: 'AUTO',
    enabled: true,
  })
}

function openDialog(row) {
  resetForm(row)
  dialogVisible.value = true
}

async function load() {
  busy.value = true
  try {
    const [roomResult, userResult] = await Promise.all([
      listMeetingRooms(),
      listMeetingParticipantOptions(),
    ])
    rooms.value = unwrap(roomResult)
    users.value = unwrap(userResult)
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!form.roomCode || !form.roomName || !form.capacity) {
    return ElMessage.warning('请填写会议室编码、名称和容量')
  }
  if (form.approvalMode === 'MANUAL' && !form.approverUsername) {
    return ElMessage.warning('人工审批模式必须选择审批人')
  }
  busy.value = true
  try {
    const payload = { ...form }
    await (form.id
      ? updateMeetingRoom(form.id, payload)
      : createMeetingRoom(payload))
    dialogVisible.value = false
    ElMessage.success('会议室已保存')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '保存失败')
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除会议室“${row.roomName}”？已有预约历史的会议室只能停用。`,
      '删除确认',
      { type: 'warning' },
    )
    await deleteMeetingRoom(row.id)
    ElMessage.success('会议室已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.msg || error?.message || '删除失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header>
      <div>
        <h2>会议室维护</h2>
        <p>维护场地容量、设备与预约审批规则。</p>
      </div>
      <el-button
        v-permission="['education:meeting:room:create', 'education:meeting:room:manage']"
        type="primary"
        @click="openDialog()"
      >新增会议室</el-button>
    </header>

    <el-table v-loading="busy" :data="rooms" border>
      <el-table-column prop="roomCode" label="编码" width="140" />
      <el-table-column prop="roomName" label="会议室" min-width="160" />
      <el-table-column prop="buildingName" label="楼宇" min-width="120" />
      <el-table-column prop="location" label="位置" min-width="160" />
      <el-table-column prop="capacity" label="容量" width="90" />
      <el-table-column label="预约规则" width="130">
        <template #default="scope">
          <el-tag :type="scope.row.approvalMode === 'AUTO' ? 'success' : 'warning'">
            {{ scope.row.approvalMode === 'AUTO' ? '自动确认' : '人工审批' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approverUsername" label="审批人" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button
            v-permission="['education:meeting:room:update', 'education:meeting:room:manage']"
            link type="primary" @click="openDialog(scope.row)"
          >编辑</el-button>
          <el-button
            v-permission="['education:meeting:room:delete', 'education:meeting:room:manage']"
            link type="danger" @click="remove(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑会议室' : '新增会议室'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="会议室编码"><el-input v-model="form.roomCode" maxlength="64" /></el-form-item>
        <el-form-item label="会议室名称"><el-input v-model="form.roomName" maxlength="128" /></el-form-item>
        <el-form-item label="楼宇"><el-input v-model="form.buildingName" maxlength="128" /></el-form-item>
        <el-form-item label="具体位置"><el-input v-model="form.location" maxlength="255" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" :max="10000" /></el-form-item>
        <el-form-item label="设备说明"><el-input v-model="form.equipmentJson" type="textarea" :rows="3" placeholder="例如：投影、视频会议终端、白板" /></el-form-item>
        <el-form-item label="预约规则">
          <el-radio-group v-model="form.approvalMode">
            <el-radio value="AUTO">空闲时自动确认</el-radio>
            <el-radio value="MANUAL">人工审批</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.approvalMode === 'MANUAL'" label="审批人">
          <el-select v-model="form.approverUsername" filterable clearable>
            <el-option
              v-for="item in users" :key="item.username"
              :label="`${item.displayName}（${item.username}）`"
              :value="item.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.el-select { width: 100%; }
</style>
