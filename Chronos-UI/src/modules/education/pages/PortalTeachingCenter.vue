<template>
  <section class="teaching-center">
    <header class="page-head">
      <div>
        <h1>教学中心</h1>
        <p>按教学业务进入专用工作台，所有编辑、审核和版本操作均由对应领域服务处理。</p>
      </div>
    </header>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="作业发布、批改与成绩发布请前往作业中心；考试业务请前往考试中心。"
    />

    <div class="module-grid">
      <el-card
        v-for="module in modules"
        :key="module.path"
        class="module-card"
        shadow="hover"
        @click="open(module.path)"
      >
        <div class="module-icon">{{ module.icon }}</div>
        <div>
          <h2>{{ module.title }}</h2>
          <p>{{ module.description }}</p>
        </div>
        <el-button link type="primary">进入工作台</el-button>
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

// 门户只负责业务导航，不再暴露已经下线的通用 Map CRUD 写入口。
const modules = [
  { icon: '计', title: '教学计划', description: '维护学期计划、章节与审核版本。', path: '/portal/education/teaching/plans' },
  { icon: '案', title: '教案', description: '编写课次教案、上传附件并提交审核。', path: '/portal/education/teaching/lessons' },
  { icon: '备', title: '集体备课', description: '发起备课、邀请成员、沉淀资料与结论。', path: '/portal/education/teaching/preparations' },
  { icon: '课', title: '课件', description: '管理课件版本、共享范围和发布状态。', path: '/portal/education/teaching/courseware' },
  { icon: '材', title: '教学材料', description: '维护校本材料、附件版本与授权范围。', path: '/portal/education/teaching/materials' },
  { icon: '题', title: '题库', description: '维护题目、选项、版本和审核流程。', path: '/portal/education/teaching/questions' },
  { icon: '知', title: '知识点', description: '维护课程知识点树及题目关联。', path: '/portal/education/teaching/knowledge-points' },
  { icon: '研', title: '教研', description: '管理教研组、活动、纪要与成果。', path: '/portal/education/teaching/research' },
  { icon: '错', title: '错题沉淀', description: '查看错题、掌握状态与任教范围聚合。', path: '/portal/education/teaching/mistakes' },
]

const open = path => router.push(path)
</script>

<style scoped>
.teaching-center { padding: 24px; }
.page-head { display: flex; justify-content: space-between; margin-bottom: 18px; }
.page-head h1 { margin: 0 0 6px; }
.page-head p, .module-card p { margin: 0; color: #7b8794; }
.module-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 18px; margin-top: 20px; }
.module-card { cursor: pointer; }
.module-card :deep(.el-card__body) { display: grid; grid-template-columns: 52px 1fr auto; gap: 14px; align-items: center; }
.module-card h2 { margin: 0 0 7px; font-size: 17px; }
.module-icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 12px; color: #2563eb; background: #eff6ff; font-size: 20px; font-weight: 700; }
</style>
