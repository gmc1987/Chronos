import { createRouter, createWebHistory } from 'vue-router'
import AdminLogin from '../app/pages/AdminLogin.vue'
import AdminLayout from '../app/layout/AdminLayout.vue'
import AdminOverview from '../app/pages/AdminOverview.vue'
import AdminUsers from '../modules/iam/pages/AdminUsers.vue'
import AdminMenus from '../modules/iam/pages/AdminMenus.vue'
import AdminRoles from '../modules/iam/pages/AdminRoles.vue'
import AdminPermissions from '../modules/iam/pages/AdminPermissions.vue'
import AdminPermissionDefinitions from '../modules/iam/pages/AdminPermissionDefinitions.vue'
import AdminOrganizations from '../modules/iam/pages/AdminOrganizations.vue'
import AdminDirectory from '../modules/iam/pages/AdminDirectory.vue'
import AdminCustomers from '../modules/iam/pages/AdminCustomers.vue'
import AdminDicts from '../modules/iam/pages/AdminDicts.vue'
import AdminAuditLogs from '../modules/iam/pages/AdminAuditLogs.vue'
import AdminAiModels from '../modules/ai/pages/AdminAiModels.vue'
import AdminAiApis from '../modules/ai/pages/AdminAiApis.vue'
import AdminWorkflows from '../modules/workflow/pages/AdminWorkflows.vue'
import AdminAgents from '../modules/ai/pages/AdminAgents.vue'
import AdminSkills from '../modules/ai/pages/AdminSkills.vue'
import AdminTools from '../modules/ai/pages/AdminTools.vue'
import AdminWorkflowNodes from '../modules/workflow/pages/AdminWorkflowNodes.vue'
import AdminForms from '../modules/workflow/pages/AdminForms.vue'
import PortalWorkflowStart from '../modules/workflow/pages/PortalWorkflowStart.vue'
import PortalWorkflowForms from '../modules/workflow/pages/PortalWorkflowForms.vue'
import PortalWorkflowTasks from '../modules/workflow/pages/PortalWorkflowTasks.vue'
import PortalWorkflowDelegations from '../modules/workflow/pages/PortalWorkflowDelegations.vue'
import PortalWorkflowNotifications from '../modules/workflow/pages/PortalWorkflowNotifications.vue'
import AdminWorkflowOutbox from '../modules/workflow/pages/AdminWorkflowOutbox.vue'
import AdminWorkflowIncidents from '../modules/workflow/pages/AdminWorkflowIncidents.vue'
import AdminAgentLogs from '../modules/ai/pages/AdminAgentLogs.vue'
import AdminStoryboardPromptProfiles from '../modules/ai/pages/AdminStoryboardPromptProfiles.vue'
import { isAdminAuthed, mustChangeAdminPassword } from '../store/auth'
import ChangePassword from '../app/pages/ChangePassword.vue'
import PortalLogin from '../app/pages/PortalLogin.vue'
import PortalLayout from '../app/layout/PortalLayout.vue'
import PortalHome from '../modules/portal/pages/PortalHome.vue'
import PortalApplications from '../modules/portal/pages/PortalApplications.vue'
import PortalEducationSchedule from '../modules/education/pages/PortalEducationSchedule.vue'
import AdminPortal from '../modules/portal/pages/AdminPortal.vue'
import AdminPublications from '../modules/message/pages/AdminPublications.vue'
import AdminNotificationSettings from '../modules/message/pages/AdminNotificationSettings.vue'
import PortalPublications from '../modules/message/pages/PortalPublications.vue'
import PortalPublicationDetail from '../modules/message/pages/PortalPublicationDetail.vue'
import { packagedIndustryRoutes } from '../industries/core'

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
