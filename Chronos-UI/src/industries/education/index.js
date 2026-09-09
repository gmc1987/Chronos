import AdminClassScheduling from '../../modules/education/pages/AdminClassScheduling.vue'
import AdminAcademicTerms from '../../modules/education/pages/AdminAcademicTerms.vue'
import AdminCourses from '../../modules/education/pages/AdminCourses.vue'
import AdminMajors from '../../modules/education/pages/AdminMajors.vue'
import AdminAdministrativeClasses from '../../modules/education/pages/AdminAdministrativeClasses.vue'
import AdminTeachers from '../../modules/education/pages/AdminTeachers.vue'
import AdminStudents from '../../modules/education/pages/AdminStudents.vue'
import AdminGrades from '../../modules/education/pages/AdminGrades.vue'
import AdminSubjects from '../../modules/education/pages/AdminSubjects.vue'
import AdminParents from '../../modules/education/pages/AdminParents.vue'
import AdminTeachingAssignments from '../../modules/education/pages/AdminTeachingAssignments.vue'
import AdminKnowledgeBases from '../../modules/education/pages/AdminKnowledgeBases.vue'
import AdminEducationAgents from '../../modules/education/pages/AdminEducationAgents.vue'

export const educationIndustry = {
  code: 'EDUCATION',
  routes: [
    { path: '/admin/education/terms', name: 'admin-education-terms', component: AdminAcademicTerms },
    { path: '/admin/education/courses', name: 'admin-education-courses', component: AdminCourses },
    { path: '/admin/education/majors', name: 'admin-education-majors', component: AdminMajors },
    { path: '/admin/education/classes', name: 'admin-education-classes', component: AdminAdministrativeClasses },
    { path: '/admin/education/teachers', name: 'admin-education-teachers', component: AdminTeachers },
    { path: '/admin/education/students', name: 'admin-education-students', component: AdminStudents },
    { path: '/admin/education/grades', name: 'admin-education-grades', component: AdminGrades },
    { path: '/admin/education/subjects', name: 'admin-education-subjects', component: AdminSubjects },
    { path: '/admin/education/parents', name: 'admin-education-parents', component: AdminParents },
    { path: '/admin/education/teaching-assignments', name: 'admin-education-teaching-assignments', component: AdminTeachingAssignments },
    { path: '/admin/education/knowledge', name: 'admin-education-knowledge', component: AdminKnowledgeBases },
    { path: '/admin/education/agents', name: 'admin-education-agents', component: AdminEducationAgents },
    {
      path: '/admin/education/scheduling',
      name: 'admin-education-class-scheduling',
      component: AdminClassScheduling,
    },
  ],
  branding: {
    systemName: '智慧校园教务协同平台',
    shortName: 'Chronos 教育',
    portalSubtitle: '智慧校园',
    loginTitle: '智慧校园教务协同平台',
    loginSubtitle: '使用学校统一身份账号登录',
    organizationLabel: '学校/校区',
    departmentLabel: '院系',
    employeeLabel: '教职工',
  },
  organizationTypes: [
    { code: 'EDUCATION_GROUP', name: '教育集团', sortOrder: 10 },
    { code: 'SCHOOL', name: '学校', sortOrder: 20 },
    { code: 'CAMPUS', name: '校区', sortOrder: 30 },
    { code: 'COLLEGE', name: '学院', sortOrder: 40 },
    { code: 'DEPARTMENT', name: '系部', sortOrder: 50 },
  ],
  departmentTypes: [
    { code: 'ADMINISTRATIVE', name: '行政部门', sortOrder: 10 },
    { code: 'ACADEMIC', name: '教学单位', sortOrder: 20 },
    { code: 'RESEARCH', name: '科研机构', sortOrder: 30 },
    { code: 'SUPPORT', name: '教辅单位', sortOrder: 40 },
    { code: 'DEPARTMENT', name: '其他单位', sortOrder: 50 },
  ],
  positionCategories: [
    { code: 'TEACHING', name: '教学', sortOrder: 10 },
    { code: 'RESEARCH', name: '科研', sortOrder: 20 },
    { code: 'ADMINISTRATIVE', name: '行政', sortOrder: 30 },
    { code: 'STUDENT_AFFAIRS', name: '学生工作', sortOrder: 40 },
    { code: 'LOGISTICS', name: '后勤', sortOrder: 50 },
  ],
}
