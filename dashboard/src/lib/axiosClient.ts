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
