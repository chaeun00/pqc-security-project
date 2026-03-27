import { http, HttpResponse } from 'msw'
import type { CbomEntry } from '@/api/cbom'
import type { InventoryItem } from '@/api/inventory'

const INITIAL_INVENTORY: InventoryItem[] = [
  { id: 1, name: 'TLS Certificate', algorithm: 'RSA-2048', location: 'api-gateway', note: 'Needs migration' },
  { id: 2, name: 'JWT Secret', algorithm: 'ML-KEM-768', location: 'auth-service' },
  { id: 3, name: 'DB Encryption Key', algorithm: 'AES-256', location: 'db-service' },
]
let mockInventoryList: InventoryItem[] = [...INITIAL_INVENTORY]
let nextInventoryId = 4

export function resetInventoryMock() {
  mockInventoryList = [...INITIAL_INVENTORY]
  nextInventoryId = 4
}

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

  http.post('/actuator/algorithm', async ({ request }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    const body = (await request.json()) as { asset_id: string; algorithm: string }
    if (!body.asset_id || !body.algorithm) {
      return HttpResponse.json({ message: 'Bad Request' }, { status: 400 })
    }
    return HttpResponse.json({ message: 'Algorithm switched', algorithm: body.algorithm })
  }),

  http.get('/api/inventory', ({ request }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    return HttpResponse.json(mockInventoryList)
  }),

  http.post('/api/inventory', async ({ request }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    const body = (await request.json()) as Omit<InventoryItem, 'id'>
    const item: InventoryItem = { id: nextInventoryId++, ...body }
    mockInventoryList = [...mockInventoryList, item]
    return HttpResponse.json(item, { status: 201 })
  }),

  http.put('/api/inventory/:id', async ({ request, params }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    const id = Number(params.id)
    const body = (await request.json()) as Partial<Omit<InventoryItem, 'id'>>
    const idx = mockInventoryList.findIndex((i) => i.id === id)
    if (idx === -1) return HttpResponse.json({ message: 'Not Found' }, { status: 404 })
    mockInventoryList = mockInventoryList.map((i) => (i.id === id ? { ...i, ...body } : i))
    return HttpResponse.json(mockInventoryList[idx])
  }),

  http.delete('/api/inventory/:id', ({ request, params }) => {
    if (!request.headers.get('Authorization')) {
      return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
    }
    const id = Number(params.id)
    mockInventoryList = mockInventoryList.filter((i) => i.id !== id)
    return new HttpResponse(null, { status: 204 })
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
