import { http, HttpResponse } from 'msw'

export const handlers = [
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
