const CONSUMER_ACCESS = 'consumer_access_token'
const CONSUMER_REFRESH = 'consumer_refresh_token'
const CONSUMER_USERNAME = 'consumer_username'

const ADMIN_ACCESS = 'admin_access_token'
const ADMIN_REFRESH = 'admin_refresh_token'
const ADMIN_USERNAME = 'admin_username'
const ADMIN_ROLES = 'admin_roles'
const ADMIN_PERMS = 'admin_permissions'
const ADMIN_MENUS = 'admin_menus'
const ADMIN_MUST_CHANGE_PASSWORD = 'admin_must_change_password'

const getStore = (remember) => (remember ? localStorage : sessionStorage)

export const saveConsumerTokens = (payload, remember = true, username) => {
  const store = getStore(remember)
  store.setItem(CONSUMER_ACCESS, payload.accessToken)
  if (payload.refreshToken) store.setItem(CONSUMER_REFRESH, payload.refreshToken)
  if (username) store.setItem(CONSUMER_USERNAME, username)

  if (remember) {
    sessionStorage.removeItem(CONSUMER_ACCESS)
    sessionStorage.removeItem(CONSUMER_REFRESH)
    sessionStorage.removeItem(CONSUMER_USERNAME)
  } else {
    localStorage.removeItem(CONSUMER_ACCESS)
    localStorage.removeItem(CONSUMER_REFRESH)
    localStorage.removeItem(CONSUMER_USERNAME)
  }
}

export const clearConsumerTokens = () => {
  localStorage.removeItem(CONSUMER_ACCESS)
  localStorage.removeItem(CONSUMER_REFRESH)
  localStorage.removeItem(CONSUMER_USERNAME)
  sessionStorage.removeItem(CONSUMER_ACCESS)
  sessionStorage.removeItem(CONSUMER_REFRESH)
  sessionStorage.removeItem(CONSUMER_USERNAME)
}

export const getConsumerToken = () =>
  localStorage.getItem(CONSUMER_ACCESS) || sessionStorage.getItem(CONSUMER_ACCESS)

export const isConsumerAuthed = () => !!getConsumerToken()
export const setConsumerAuthed = (authed) => {
  if (!authed) clearConsumerTokens()
}

export const saveAdminTokens = (payload, remember = true, username) => {
  const store = getStore(remember)
  store.setItem(ADMIN_ACCESS, payload.accessToken)
  if (payload.refreshToken) store.setItem(ADMIN_REFRESH, payload.refreshToken)
  if (username) store.setItem(ADMIN_USERNAME, username)

  if (remember) {
    sessionStorage.removeItem(ADMIN_ACCESS)
    sessionStorage.removeItem(ADMIN_REFRESH)
    sessionStorage.removeItem(ADMIN_USERNAME)
  } else {
    localStorage.removeItem(ADMIN_ACCESS)
    localStorage.removeItem(ADMIN_REFRESH)
    localStorage.removeItem(ADMIN_USERNAME)
  }
}

export const saveAdminMeta = (payload, remember = true) => {
  const store = getStore(remember)
  if (payload.roles) store.setItem(ADMIN_ROLES, JSON.stringify(payload.roles))
  if (payload.permissions) store.setItem(ADMIN_PERMS, JSON.stringify(payload.permissions))
  if (payload.menus) store.setItem(ADMIN_MENUS, JSON.stringify(payload.menus))
  if (payload.mustChangePassword !== undefined) store.setItem(ADMIN_MUST_CHANGE_PASSWORD, String(!!payload.mustChangePassword))

  if (remember) {
    sessionStorage.removeItem(ADMIN_ROLES)
    sessionStorage.removeItem(ADMIN_PERMS)
    sessionStorage.removeItem(ADMIN_MENUS)
    sessionStorage.removeItem(ADMIN_MUST_CHANGE_PASSWORD)
  } else {
    localStorage.removeItem(ADMIN_ROLES)
    localStorage.removeItem(ADMIN_PERMS)
    localStorage.removeItem(ADMIN_MENUS)
    localStorage.removeItem(ADMIN_MUST_CHANGE_PASSWORD)
  }
}

export const clearAdminTokens = () => {
  localStorage.removeItem(ADMIN_ACCESS)
  localStorage.removeItem(ADMIN_REFRESH)
  localStorage.removeItem(ADMIN_USERNAME)
  localStorage.removeItem(ADMIN_ROLES)
  localStorage.removeItem(ADMIN_PERMS)
  localStorage.removeItem(ADMIN_MENUS)
  localStorage.removeItem(ADMIN_MUST_CHANGE_PASSWORD)
  sessionStorage.removeItem(ADMIN_ACCESS)
  sessionStorage.removeItem(ADMIN_REFRESH)
  sessionStorage.removeItem(ADMIN_USERNAME)
  sessionStorage.removeItem(ADMIN_ROLES)
  sessionStorage.removeItem(ADMIN_PERMS)
  sessionStorage.removeItem(ADMIN_MENUS)
  sessionStorage.removeItem(ADMIN_MUST_CHANGE_PASSWORD)
}

export const getAdminToken = () =>
  localStorage.getItem(ADMIN_ACCESS) || sessionStorage.getItem(ADMIN_ACCESS)

export const getAdminRefresh = () =>
  localStorage.getItem(ADMIN_REFRESH) || sessionStorage.getItem(ADMIN_REFRESH)

export const getAdminUsername = () =>
  localStorage.getItem(ADMIN_USERNAME) || sessionStorage.getItem(ADMIN_USERNAME)

export const getAdminMenus = () => {
  const raw = localStorage.getItem(ADMIN_MENUS) || sessionStorage.getItem(ADMIN_MENUS)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const isAdminAuthed = () => !!getAdminToken()
export const mustChangeAdminPassword = () =>
  (localStorage.getItem(ADMIN_MUST_CHANGE_PASSWORD) || sessionStorage.getItem(ADMIN_MUST_CHANGE_PASSWORD)) === 'true'
