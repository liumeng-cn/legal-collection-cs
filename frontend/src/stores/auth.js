import { ref, computed } from 'vue'

const TOKEN_KEY = 'legalcs_token'
const ROLE_KEY = 'legalcs_role'
const NAME_KEY = 'legalcs_name'
const USER_ID_KEY = 'legalcs_user_id'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const role = ref(localStorage.getItem(ROLE_KEY) || '')
const name = ref(localStorage.getItem(NAME_KEY) || '')
const userId = ref(localStorage.getItem(USER_ID_KEY) || '')

export function setAuth(data) {
  token.value = data.token
  role.value = data.role
  name.value = data.name
  userId.value = data.userId
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(ROLE_KEY, data.role)
  localStorage.setItem(NAME_KEY, data.name)
  localStorage.setItem(USER_ID_KEY, data.userId)
}

export function clearAuth() {
  token.value = ''
  role.value = ''
  name.value = ''
  userId.value = ''
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(USER_ID_KEY)
}

export function getToken() {
  return token.value
}

export function useAuth() {
  return {
    token,
    role,
    name,
    userId,
    isLoggedIn: computed(() => !!token.value),
    roleLabel: computed(() => {
      if (role.value === 'STAFF') return '催员'
      if (role.value === 'SRE') return '产研运维'
      return '债务人'
    })
  }
}
