import { axiosClient } from '@/lib/axiosClient'

interface EncryptRequest {
  plaintext: string
  risk_level?: string
}

interface EncryptResponse {
  key_id: number
  algorithm: string
  kem_ciphertext: string
  aes_ciphertext: string
  aes_iv: string
  risk_level: string
}

export async function encrypt(body: EncryptRequest): Promise<EncryptResponse> {
  const { data } = await axiosClient.post<EncryptResponse>('/api/encrypt', body)
  return data
}
