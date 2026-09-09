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
 * 以后行业前端模块可以在各自 routes 中声明页面；核心路由不需要引用行业页面。
 */
export const packagedIndustryRoutes = () => fallback.routes || []

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
