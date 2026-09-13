import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../app/layout/AdminLayout.vue'
import { isAdminAuthed, mustChangeAdminPassword } from '../store/auth'
import PortalLayout from '../app/layout/PortalLayout.vue'
import { packagedIndustryRoutes } from '../industries/core'

// 页面按路由拆包，避免登录页一次下载整个管理后台和行业模块。
const AdminLogin = () => import('../app/pages/AdminLogin.vue')
const AdminOverview = () => import('../app/pages/AdminOverview.vue')
const AdminUsers = () => import('../modules/iam/pages/AdminUsers.vue')
const AdminMenus = () => import('../modules/iam/pages/AdminMenus.vue')
const AdminRoles = () => import('../modules/iam/pages/AdminRoles.vue')
const AdminPermissions = () => import('../modules/iam/pages/AdminPermissions.vue')
const AdminPermissionDefinitions = () => import('../modules/iam/pages/AdminPermissionDefinitions.vue')
const AdminOrganizations = () => import('../modules/iam/pages/AdminOrganizations.vue')
const AdminDirectory = () => import('../modules/iam/pages/AdminDirectory.vue')
const AdminCustomers = () => import('../modules/iam/pages/AdminCustomers.vue')
const AdminDicts = () => import('../modules/iam/pages/AdminDicts.vue')
const AdminAuditLogs = () => import('../modules/iam/pages/AdminAuditLogs.vue')
const AdminAiModels = () => import('../modules/ai/pages/AdminAiModels.vue')
const AdminAiApis = () => import('../modules/ai/pages/AdminAiApis.vue')
const AdminAgents = () => import('../modules/ai/pages/AdminAgents.vue')
const AdminSkills = () => import('../modules/ai/pages/AdminSkills.vue')
const AdminTools = () => import('../modules/ai/pages/AdminTools.vue')
const AdminAgentLogs = () => import('../modules/ai/pages/AdminAgentLogs.vue')
const AdminStoryboardPromptProfiles = () => import('../modules/ai/pages/AdminStoryboardPromptProfiles.vue')
const AdminWorkflows = () => import('../modules/workflow/pages/AdminWorkflows.vue')
const AdminWorkflowNodes = () => import('../modules/workflow/pages/AdminWorkflowNodes.vue')
const AdminForms = () => import('../modules/workflow/pages/AdminForms.vue')
const AdminWorkflowOutbox = () => import('../modules/workflow/pages/AdminWorkflowOutbox.vue')
const AdminWorkflowIncidents = () => import('../modules/workflow/pages/AdminWorkflowIncidents.vue')
const PortalWorkflowStart = () => import('../modules/workflow/pages/PortalWorkflowStart.vue')
const PortalWorkflowForms = () => import('../modules/workflow/pages/PortalWorkflowForms.vue')
const PortalWorkflowTasks = () => import('../modules/workflow/pages/PortalWorkflowTasks.vue')
const PortalWorkflowDelegations = () => import('../modules/workflow/pages/PortalWorkflowDelegations.vue')
const PortalWorkflowNotifications = () => import('../modules/workflow/pages/PortalWorkflowNotifications.vue')
const ChangePassword = () => import('../app/pages/ChangePassword.vue')
const PortalLogin = () => import('../app/pages/PortalLogin.vue')
const PortalHome = () => import('../modules/portal/pages/PortalHome.vue')
const PortalApplications = () => import('../modules/portal/pages/PortalApplications.vue')
const PortalEducationSchedule = () => import('../modules/education/pages/PortalEducationSchedule.vue')
const AdminTeachingPlan = () => import('../modules/education/pages/AdminTeachingPlan.vue')
const AdminLessonPlans = () => import('../modules/education/pages/AdminLessonPlans.vue')
const AdminPreparation = () => import('../modules/education/pages/AdminPreparation.vue')
const AdminCourseware = () => import('../modules/education/pages/AdminCourseware.vue')
const AdminMaterials = () => import('../modules/education/pages/AdminMaterials.vue')
const AdminHomework = () => import('../modules/education/pages/AdminHomework.vue')
const AdminQuestionBank = () => import('../modules/education/pages/AdminQuestionBank.vue')
const AdminKnowledgePoints = () => import('../modules/education/pages/AdminKnowledgePoints.vue')
const AdminMistakes = () => import('../modules/education/pages/AdminMistakes.vue')
const AdminResearch = () => import('../modules/education/pages/AdminResearch.vue')
const AdminPortal = () => import('../modules/portal/pages/AdminPortal.vue')
const AdminPublications = () => import('../modules/message/pages/AdminPublications.vue')
const AdminNotificationSettings = () => import('../modules/message/pages/AdminNotificationSettings.vue')
const PortalPublications = () => import('../modules/message/pages/PortalPublications.vue')
const PortalPublicationDetail = () => import('../modules/message/pages/PortalPublicationDetail.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/portal' },
    { path: '/login', name: 'portal-login', component: PortalLogin },
    {
      path: '/portal',
      component: PortalLayout,
      children: [
        { path: '', name: 'portal-home', component: PortalHome },
        { path: 'apps', name: 'portal-apps', component: PortalApplications },
        { path: 'education/schedule', name: 'portal-education-schedule', component: PortalEducationSchedule },
        { path: 'tasks', name: 'portal-tasks', component: PortalWorkflowTasks },
        { path: 'workflow-delegations', name: 'portal-workflow-delegations', component: PortalWorkflowDelegations },
        { path: 'workflow-notifications', name: 'portal-workflow-notifications', component: PortalWorkflowNotifications },
        { path: 'workflows', name: 'portal-workflows', component: PortalWorkflowStart },
        { path: 'workflow-instances/:id/forms', name: 'portal-workflow-forms', component: PortalWorkflowForms },
        { path: 'publications', name: 'portal-publications', component: PortalPublications },
        { path: 'publications/:id', name: 'portal-publication-detail', component: PortalPublicationDetail },
      ],
    },
    { path: '/admin/login', name: 'admin-login', component: AdminLogin },
    { path: '/account/change-password', name: 'change-password', component: ChangePassword },
    {
      path: '/admin',
      component: AdminLayout,
      children: [
        { path: '', redirect: '/admin/overview' },
        { path: 'overview', name: 'admin-overview', component: AdminOverview },
        { path: 'users', name: 'admin-users', component: AdminUsers },
        { path: 'menus', name: 'admin-menus', component: AdminMenus },
        { path: 'roles', name: 'admin-roles', component: AdminRoles },
        { path: 'permissions', name: 'admin-permissions', component: AdminPermissions },
        { path: 'permission-definitions', name: 'admin-permission-definitions', component: AdminPermissionDefinitions },
        { path: 'organizations', name: 'admin-organizations', component: AdminOrganizations },
        { path: 'directory', name: 'admin-directory', component: AdminDirectory },
        { path: 'customers', name: 'admin-customers', component: AdminCustomers },
        { path: 'dicts', name: 'admin-dicts', component: AdminDicts },
        { path: 'audit-logs', name: 'admin-audit-logs', component: AdminAuditLogs },
        { path: 'portal', name: 'admin-portal', component: AdminPortal },
        { path: 'ai-models', name: 'admin-ai-models', component: AdminAiModels },
        { path: 'ai-model/models', redirect: { name: 'admin-ai-models' } },
        { path: 'ai-model/apis', name: 'admin-ai-apis', component: AdminAiApis },
        { path: 'agent/agents', name: 'admin-agents', component: AdminAgents },
        { path: 'agent/skills', name: 'admin-skills', component: AdminSkills },
        { path: 'agent/tools', name: 'admin-tools', component: AdminTools },
        { path: 'workflow/workflow-nodes', name: 'admin-workflow-nodes', component: AdminWorkflowNodes },
        { path: 'workflow/forms', name: 'admin-forms', component: AdminForms },
        { path: 'agent/agent-logs', name: 'admin-agent-logs', component: AdminAgentLogs },
        { path: 'storyboard/prompt', name: 'admin-storyboard-prompt', component: AdminStoryboardPromptProfiles },
        { path: 'workflows', name: 'admin-workflows', component: AdminWorkflows },
        { path: 'workflow/outbox', name: 'admin-workflow-outbox', component: AdminWorkflowOutbox },
        { path: 'workflow/incidents', name: 'admin-workflow-incidents', component: AdminWorkflowIncidents },
        { path: 'publications', name: 'admin-publications', component: AdminPublications },
        { path: 'message-settings', name: 'admin-message-settings', component: AdminNotificationSettings },
        { path: 'education/teaching-center/plan', name: 'admin-education-teaching-plan', component: AdminTeachingPlan },
        { path: 'education/teaching-center/lesson-plan', name: 'admin-education-lesson-plan', component: AdminLessonPlans },
        { path: 'education/teaching-center/preparation', name: 'admin-education-preparation', component: AdminPreparation },
        { path: 'education/teaching-center/courseware', name: 'admin-education-courseware', component: AdminCourseware },
        { path: 'education/teaching-center/material', name: 'admin-education-material', component: AdminMaterials },
        { path: 'education/teaching-center/homework', name: 'admin-education-homework', component: AdminHomework },
        { path: 'education/teaching-center/question-bank', name: 'admin-education-question-bank', component: AdminQuestionBank },
        { path: 'education/teaching-center/knowledge-point', name: 'admin-education-knowledge-point', component: AdminKnowledgePoints },
        { path: 'education/teaching-center/error-book', name: 'admin-education-error-book', component: AdminMistakes },
        { path: 'education/teaching-center/research', name: 'admin-education-research', component: AdminResearch },
        ...packagedIndustryRoutes(),
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.path === '/account/change-password' && !isAdminAuthed()) return '/login'
  if (isAdminAuthed() && mustChangeAdminPassword() && to.path !== '/account/change-password') return '/account/change-password'
  if (to.path.startsWith('/portal') && !isAdminAuthed()) return '/login'
  if (to.path === '/login' && isAdminAuthed()) return '/portal'
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    if (!isAdminAuthed()) return '/admin/login'
  }
  if (to.path === '/admin/login' && isAdminAuthed()) return '/admin/overview'
  return true
})

export default router
