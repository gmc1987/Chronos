import { computed, reactive } from 'vue'
import { http } from '../../api/http'
import { educationIndustry } from '../education'
import { hospitalIndustry } from '../hospital'

const packagedIndustries = new Map([
  [hospitalIndustry.code, hospitalIndustry],
  [educationIndustry.code, educationIndustry],
])

const buildIndustry = String(import.meta.env.VITE_CHRONOS_INDUSTRY || 'HOSPITAL').toUpperCase()
const fallback = packagedIndustries.get(buildIndustry) || hospitalIndustry

export const industryContext = reactive({ ...fallback })
export const industryBranding = computed(() => industryContext.branding || fallback.branding)

/**
 * 行业菜单由后端运行时配置决定，但路由必须在前端启动时同步注册。
 * 注册所有已打包行业的懒加载路由，避免部署遗漏 VITE_CHRONOS_INDUSTRY 时
 * 出现“菜单可见但页面空白”的情况；未访问的行业页面不会加载对应 chunk。
 */
export const packagedIndustryRoutes = () => {
  const routes = [...packagedIndustries.values()].flatMap(industry => industry.routes || [])
  const paths = new Set()
  const names = new Set()
  return routes.filter((route) => {
    if (paths.has(route.path) || (route.name && names.has(route.name))) return false
    paths.add(route.path)
    if (route.name) names.add(route.name)
    return true
  })
}

export const loadIndustryContext = async () => {
  try {
    const response = await http.get('/public/industry/context')
    if (response?.code === '200' && response.data?.code) {
      Object.assign(industryContext, response.data)
    }
  } catch {
    // 后端不可用时使用构建期模板，确保登录页仍能正常显示。
  }
  document.title = industryBranding.value.systemName || 'Chronos'
  return industryContext
}
