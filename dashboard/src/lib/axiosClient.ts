import axios from 'axios'
import { useAppStore } from '@/store/useAppStore'

export const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
})

axiosClient.interceptors.request.use((config) => {
  const token = useAppStore.getState().token
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAppStore.getState().clearToken()
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(error)
  },
)
