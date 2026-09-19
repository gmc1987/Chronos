import { readFile } from 'node:fs/promises'

const routerSource = await readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const layoutSource = await readFile(new URL('../src/app/layout/PortalLayout.vue', import.meta.url), 'utf8')
const homeSource = await readFile(new URL('../src/modules/portal/pages/PortalHome.vue', import.meta.url), 'utf8')

for (const path of [
  '/portal',
  '/portal/apps',
  '/portal/tasks',
  '/portal/education/schedule',
  '/portal/education/grades',
  '/portal/education/family',
  '/portal/education/supervision',
]) {
  if (!routerSource.includes(`path: '${path.replace('/portal/', '').replace('/portal', '')}'`) && !routerSource.includes(`path: '${path}'`)) {
    throw new Error(`移动端工作台缺少真实门户路由: ${path}`)
  }
}
for (const marker of ['portal-mobile-nav', 'to="/portal"', 'to="/portal/apps"', 'to="/portal/tasks"']) {
  if (!layoutSource.includes(marker)) {
    throw new Error(`移动端导航契约缺少: ${marker}`)
  }
}
for (const marker of ['TEACHER', 'STUDENT', 'PARENT', 'SUPERVISOR', 'portal/education/grades', 'portal/education/family']) {
  if (!homeSource.includes(marker)) throw new Error(`身份工作台契约缺少: ${marker}`)
}

console.log('Mobile H5 route contract passed (navigation, role workbench, existing portal routes)')
