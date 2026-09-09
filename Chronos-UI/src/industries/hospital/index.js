export const hospitalIndustry = {
  code: 'HOSPITAL',
  routes: [],
  branding: {
    systemName: '医院智慧协同办公平台',
    shortName: 'Chronos 医疗',
    portalSubtitle: '医院智慧办公',
    loginTitle: '医院智慧协同办公平台',
    loginSubtitle: '使用医院统一账号登录',
    organizationLabel: '医院/院区',
    departmentLabel: '科室',
    employeeLabel: '员工',
  },
  organizationTypes: [
    { code: 'MEDICAL_GROUP', name: '医疗集团', sortOrder: 10 },
    { code: 'HOSPITAL', name: '医院', sortOrder: 20 },
    { code: 'CAMPUS', name: '院区', sortOrder: 30 },
  ],
  departmentTypes: [
    { code: 'ADMINISTRATIVE', name: '行政部门', sortOrder: 10 },
    { code: 'CLINICAL', name: '临床科室', sortOrder: 20 },
    { code: 'MEDICAL_TECHNOLOGY', name: '医技科室', sortOrder: 30 },
    { code: 'NURSING', name: '护理单元', sortOrder: 40 },
    { code: 'DEPARTMENT', name: '其他部门', sortOrder: 50 },
  ],
  positionCategories: [
    { code: 'MEDICAL', name: '医疗', sortOrder: 10 },
    { code: 'NURSING', name: '护理', sortOrder: 20 },
    { code: 'TECHNICAL', name: '医技', sortOrder: 30 },
    { code: 'ADMINISTRATIVE', name: '行政', sortOrder: 40 },
    { code: 'LOGISTICS', name: '后勤', sortOrder: 50 },
  ],
}
