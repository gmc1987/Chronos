<template>
  <section class="admin-teaching-page">
    <header class="admin-teaching-header">
      <div>
        <h1>教学中心</h1>
        <p>按业务领域进入独立工作台，编辑、审核和版本操作由对应服务处理。</p>
      </div>
    </header>

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
const modules = [
  { icon: '计', title: '教学计划', description: '维护章节、周次和审核版本。', path: '/admin/education/teaching-center/plan' },
  { icon: '案', title: '教案', description: '编写课次教案并提交审核。', path: '/admin/education/teaching-center/lesson-plan' },
  { icon: '备', title: '集体备课', description: '沉淀讨论、资料和备课结论。', path: '/admin/education/teaching-center/preparation' },
  { icon: '课', title: '课件', description: '管理课件版本、共享范围和发布状态。', path: '/admin/education/teaching-center/courseware' },
  { icon: '材', title: '教学材料', description: '维护校本材料和附件版本。', path: '/admin/education/teaching-center/material' },
  { icon: '作', title: '作业', description: '发布作业、批改提交并发布成绩。', path: '/admin/education/teaching-center/homework' },
  { icon: '题', title: '题库', description: '维护题目、选项、版本和审核流程。', path: '/admin/education/teaching-center/question-bank' },
  { icon: '知', title: '知识点', description: '维护课程知识点树及题目关联。', path: '/admin/education/teaching-center/knowledge-point' },
  { icon: '研', title: '教研', description: '管理教研组、活动和成果。', path: '/admin/education/teaching-center/research' },
  { icon: '错', title: '错题', description: '查看错题、掌握状态和聚合数据。', path: '/admin/education/teaching-center/error-book' },
]

const open = path => router.push(path)
</script>

<style scoped>
.admin-teaching-page { padding: 24px; }
.admin-teaching-header { margin-bottom: 20px; }
.admin-teaching-header h1 { margin: 0 0 6px; font-size: 22px; }
.admin-teaching-header p, .module-card p { margin: 0; color: #7b8794; }
.module-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 18px; }
.module-card { cursor: pointer; }
.module-card :deep(.el-card__body) { display: grid; grid-template-columns: 52px 1fr auto; gap: 14px; align-items: center; }
.module-card h2 { margin: 0 0 7px; font-size: 17px; }
.module-icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 12px; color: #2563eb; background: #eff6ff; font-size: 20px; font-weight: 700; }
</style>
