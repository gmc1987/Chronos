import { hasAdminPermission } from '../store/auth'

export const permissionDirective = {
  mounted(el, binding) {
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!hasAdminPermission(...codes.filter(Boolean))) el.remove()
  },
}
