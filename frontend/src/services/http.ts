import axios from 'axios'

const appBase = import.meta.env.BASE_URL
const loginPath = `${appBase}login`

export const http = axios.create({ baseURL: `${appBase}api`, timeout: 15_000 })

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('academic-support-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('academic-support-token')
      if (!location.pathname.startsWith(loginPath)) location.assign(`${loginPath}?expired=1`)
    }
    return Promise.reject(error)
  },
)

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求超时，请重试' : '请求失败，请检查网络')
  }
  return '操作失败，请重试'
}
