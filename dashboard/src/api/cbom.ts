import { axiosClient } from '@/lib/axiosClient'

export type CbomEntry = {
  id: number
  algorithm: string
  type: string
  risk_level: string
  registered_at: string
  note?: string
}

/**
 * GET /api/cbom — CBOM 항목 목록 조회
 * registered_at: ISO 8601 문자열 (프론트엔드 표준 필드명, 백엔드 DTO alias 처리)
 */
export async function getCbomList(): Promise<CbomEntry[]> {
  const res = await axiosClient.get<CbomEntry[]>('/api/cbom')
  return res.data
}
