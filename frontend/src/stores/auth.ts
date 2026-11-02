import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { http } from '@/services/http'
import type { ApiResponse, CurrentUser } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)
  const token = ref(sessionStorage.getItem('academic-support-token'))
  const authenticated = computed(() => Boolean(token.value && user.value))

  async function login(loginName: string, password: string) {
    const response = await http.post<ApiResponse<{ accessToken: string; user: CurrentUser }>>('/auth/login', { loginName, password })
    token.value = response.data.data.accessToken
    user.value = response.data.data.user
    sessionStorage.setItem('academic-support-token', token.value)
  }

  async function loadUser() {
    if (!token.value) return null
    loading.value = true
    try {
      const response = await http.get<ApiResponse<CurrentUser>>('/auth/me')
      user.value = response.data.data
      return user.value
    } finally { loading.value = false }
  }

  function logout() {
    token.value = null
    user.value = null
    sessionStorage.removeItem('academic-support-token')
  }

  return { user, loading, authenticated, token, login, loadUser, logout }
})
