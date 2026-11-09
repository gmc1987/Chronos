import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { orgOptions } from '../api/admin'
import { getAdminUsername } from '../store/auth'

const STORAGE_PREFIX = 'chronos:organization-context'

const errorMessage = error =>
  error?.response?.data?.msg || error?.response?.data?.message || error?.message || '加载机构失败'

export function useOrganizationContext({
  organizationId,
  contextKey,
  filter = value => true,
  autoSelectSingle = true,
}) {
  const options = ref([])
  const loading = ref(false)
  let requestVersion = 0

  const storageKey = computed(() => {
    const username = getAdminUsername() || 'anonymous'
    return `${STORAGE_PREFIX}:${username}:${contextKey.value || 'default'}`
  })
  const selectedOrganization = computed(() =>
    options.value.find(item => item.id === organizationId.value) || null)
  const hasNoAccess = computed(() => !loading.value && options.value.length === 0)
  const singleOrganization = computed(() => options.value.length === 1)
  const selectionDisabled = computed(() => loading.value || hasNoAccess.value || singleOrganization.value)

  function remember(value) {
    if (!value) {
      localStorage.removeItem(storageKey.value)
      return
    }
    if (options.value.some(item => item.id === value)) {
      localStorage.setItem(storageKey.value, value)
    }
  }

  function resolveSelection() {
    const currentAllowed = options.value.some(item => item.id === organizationId.value)
    if (!currentAllowed) organizationId.value = ''

    if (options.value.length === 1) {
      organizationId.value = options.value[0].id
      remember(organizationId.value)
      return
    }
    if (organizationId.value) return

    const remembered = localStorage.getItem(storageKey.value)
    if (remembered && options.value.some(item => item.id === remembered)) {
      organizationId.value = remembered
    } else if (remembered) {
      localStorage.removeItem(storageKey.value)
    }
  }

  async function loadOrganizations() {
    const version = ++requestVersion
    loading.value = true
    try {
      const response = await orgOptions()
      if (version !== requestVersion) return
      options.value = (response.data || []).filter(filter)
      resolveSelection()
    } catch (error) {
      if (version !== requestVersion) return
      options.value = []
      organizationId.value = ''
      ElMessage.error(errorMessage(error))
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  watch(organizationId, value => remember(value))
  watch(contextKey, () => {
    requestVersion += 1
    resolveSelection()
  })
  onMounted(loadOrganizations)

  return {
    options,
    loading,
    hasNoAccess,
    singleOrganization,
    selectedOrganization,
    selectionDisabled,
    reloadOrganizations: loadOrganizations,
  }
}
