import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { employees, orgOptions } from '../../api/admin'

export function useOrganizationEmployees(organizationId = ref('')) {
  const organizations = ref([])
  const employeeOptions = ref([])
  let sequence = 0

  watch(organizationId, async id => {
    const request = ++sequence
    employeeOptions.value = []
    if (!id) return
    try {
      const response = await employees({ organizationId: id })
      if (request === sequence) employeeOptions.value = response.data || []
    } catch (error) {
      if (request === sequence) ElMessage.error(error.message || '员工选项加载失败')
    }
  }, { immediate: true })

  async function loadOrganizations() {
    try {
      organizations.value = (await orgOptions()).data || []
      if (organizations.value.length === 1 && !organizationId.value) {
        organizationId.value = organizations.value[0].id
      }
    } catch (error) {
      ElMessage.error(error.message || '机构选项加载失败')
    }
  }

  return { organizationId, organizations, employeeOptions, loadOrganizations }
}
