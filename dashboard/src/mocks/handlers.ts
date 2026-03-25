import { http, HttpResponse } from 'msw'
import type { CbomEntry } from '@/api/cbom'

const mockCbomList: CbomEntry[] = [
  { id: 1, algorithm: 'ML-KEM-768', type: 'KEM', risk_level: 'LOW', registered_at: '2026-03-01T00:00:00Z' },
  { id: 2, algorithm: 'ML-DSA-65', type: 'DSA', risk_level: 'LOW', registered_at: '2026-03-02T00:00:00Z' },
  { id: 3, algorithm: 'RSA-2048', type: 'Encryption', risk_level: 'HIGH', registered_at: '2026-03-03T00:00:00Z' },
  { id: 4, algorithm: 'ECDSA-P256', type: 'DSA', risk_level: 'MEDIUM', registered_at: '2026-03-04T00:00:00Z' },
  { id: 5, algorithm: 'AES-128', type: 'Symmetric', risk_level: 'MEDIUM', registered_at: '2026-03-05T00:00:00Z' },
  { id: 6, algorithm: 'ML-KEM-512', type: 'KEM', risk_level: 'LOW', registered_at: '2026-03-06T00:00:00Z' },
  { id: 7, algorithm: 'SHA-1', type: 'Hash', risk_level: 'HIGH', registered_at: '2026-03-07T00:00:00Z' },
  { id: 8, algorithm: 'ML-DSA-44', type: 'DSA', risk_level: 'LOW', registered_at: '2026-03-08T00:00:00Z' },
  { id: 9, algorithm: 'DES-56', type: 'Symmetric', risk_level: 'HIGH', registered_at: '2026-03-09T00:00:00Z' },
  { id: 10, algorithm: 'AES-256', type: 'Symmetric', risk_level: 'LOW', registered_at: '2026-03-10T00:00:00Z' },
]

export const handlers = [
  http.get('/api/cbom', ({ request }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    return HttpResponse.json(mockCbomList)
  }),

  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { userId: string; password: string }
    if (body.userId === 'admin' && body.password === 'password') {
      return HttpResponse.json({ token: 'mock-jwt-token' })
    }
    return HttpResponse.json({ message: 'Invalid credentials' }, { status: 401 })
  }),

  http.post('/api/encrypt', async ({ request }) => {
    const body = (await request.json()) as { plaintext: string; risk_level?: string }
    return HttpResponse.json({
      key_id: 1,
      algorithm: 'ML-KEM-768',
      kem_ciphertext: 'mock-kem-ct',
      aes_ciphertext: btoa(body.plaintext),
      aes_iv: 'mock-iv',
      risk_level: body.risk_level ?? 'LOW',
    })
  }),
]
