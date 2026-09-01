<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">分镜 Prompt 模板管理</div>
        <div class="subtitle">维护Agent tool提示词模版</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="编码/名称" class="search-input" @keyup.enter="load" />
        <el-select v-model="status" placeholder="状态" style="width: 120px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select v-model="defaultFlag" placeholder="默认" style="width: 120px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="默认" :value="1" />
          <el-option label="非默认" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增模板</el-button>
      </div>
    </div>

    <el-table :data="items" border style="width: 100%">
      <el-table-column prop="profileCode" label="模板编码" width="180" />
      <el-table-column prop="profileName" label="模板名称" width="180" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="默认" width="100">
        <template #default="scope">
          <span class="status-tag">{{ scope.row.defaultFlag === 1 ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <span class="status-tag">{{ scope.row.status === 1 ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastUpdateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-popconfirm
            title="确定删除该 Prompt 模板吗？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="remove(scope.row)"
          >
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'create' ? '新增 Prompt 模板' : '编辑 Prompt 模板'"
      width="1100px"
      class="dark-dialog"
    >
      <el-form label-width="130px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="模板编码">
              <el-input v-model="form.profileCode" :disabled="dialogMode === 'edit'" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模板名称">
              <el-input v-model="form.profileName" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="默认">
              <el-select v-model="form.defaultFlag">
                <el-option :value="1" label="是" />
                <el-option :value="0" label="否" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="禁用" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>

        <el-collapse v-model="activePanels" class="prompt-panels">
          <el-collapse-item name="image" title="图像模板">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="imageTemplateCn">
                  <el-input type="textarea" :rows="5" v-model="form.imageTemplateCn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="imageTemplateEn">
                  <el-input type="textarea" :rows="5" v-model="form.imageTemplateEn" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <el-collapse-item name="video" title="视频模板">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="videoTemplateCn">
                  <el-input type="textarea" :rows="5" v-model="form.videoTemplateCn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="videoTemplateEn">
                  <el-input type="textarea" :rows="5" v-model="form.videoTemplateEn" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <el-collapse-item name="audio" title="音频模板">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="audioTemplateCn">
                  <el-input type="textarea" :rows="5" v-model="form.audioTemplateCn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="audioTemplateEn">
                  <el-input type="textarea" :rows="5" v-model="form.audioTemplateEn" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <el-collapse-item name="tts" title="TTS 模板">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="ttsTemplateCn">
                  <el-input type="textarea" :rows="5" v-model="form.ttsTemplateCn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="ttsTemplateEn">
                  <el-input type="textarea" :rows="5" v-model="form.ttsTemplateEn" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <el-collapse-item name="motion" title="运动模板">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="motionTemplateCn">
                  <el-input type="textarea" :rows="5" v-model="form.motionTemplateCn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="motionTemplateEn">
                  <el-input type="textarea" :rows="5" v-model="form.motionTemplateEn" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <el-collapse-item name="negative" title="负向模板">
            <el-form-item label="negativeTemplate">
              <el-input type="textarea" :rows="4" v-model="form.negativeTemplate" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  listPromptTemplateProfiles,
  createPromptTemplateProfile,
  updatePromptTemplateProfile,
  deletePromptTemplateProfile,
} from '../api'

const items = ref([])
const keyword = ref('')
const status = ref('')
const defaultFlag = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const activePanels = ref(['image'])

const emptyForm = () => ({
  id: '',
  profileCode: '',
  profileName: '',
  description: '',
  imageTemplateCn: '',
  imageTemplateEn: '',
  videoTemplateCn: '',
  videoTemplateEn: '',
  audioTemplateCn: '',
  audioTemplateEn: '',
  ttsTemplateCn: '',
  ttsTemplateEn: '',
  motionTemplateCn: '',
  motionTemplateEn: '',
  negativeTemplate: '',
  defaultFlag: 0,
  status: 1,
})

const form = ref(emptyForm())

const load = async () => {
  const res = await listPromptTemplateProfiles({
    searchKey: keyword.value,
    status: status.value,
    defaultFlag: defaultFlag.value,
  })
  items.value = res?.data || []
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = emptyForm()
  activePanels.value = ['image']
  showDialog.value = true
}

const openEdit = (row) => {
  dialogMode.value = 'edit'
  form.value = { ...emptyForm(), ...row }
  activePanels.value = ['image']
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createPromptTemplateProfile(form.value)
  } else {
    await updatePromptTemplateProfile(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deletePromptTemplateProfile(row.id)
  load()
}

load()
</script>
