// GET /api/cbom — 백엔드 미구현 (Day 13 연동), Day 11은 타입 정의만
export type CbomEntry = {
  id: number
  algorithm: string
  type: string
  risk_level: string
  registered_at: string
  note?: string
}
