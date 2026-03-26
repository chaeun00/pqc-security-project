import { axiosClient } from '@/lib/axiosClient'

// UX guard only — NOT a security boundary.
// Server-side validation is required in POST /actuator/algorithm handler.
export const ALLOWED_ALGORITHMS = ['ML-KEM-512', 'ML-KEM-768', 'ML-DSA-44']

export type AlgorithmSwitchPayload = {
  asset_id: string
  algorithm: string
}

export async function postAlgorithmSwitch(payload: AlgorithmSwitchPayload): Promise<void> {
  await axiosClient.post('/actuator/algorithm', payload)
}
