import { axiosClient } from '@/lib/axiosClient'

interface LoginRequest {
  userId: string
  password: string
}

interface LoginResponse {
  token: string
}

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const { data } = await axiosClient.post<LoginResponse>('/api/auth/login', body)
  return data
}
