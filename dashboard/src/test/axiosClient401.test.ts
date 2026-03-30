import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { axiosClient } from '@/lib/axiosClient'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(
  http.get('http://localhost/api/test', () => HttpResponse.json({}, { status: 401 })),
)

beforeAll(() => server.listen())
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

describe('axiosClient 401 인터셉터', () => {
  it('401 응답 시 clearToken 호출', async () => {
    useAppStore.setState({ token: 'some-token' })
    await axiosClient.get('http://localhost/api/test').catch(() => {})
    expect(useAppStore.getState().token).toBeNull()
  })

  it('토큰 존재 시 Authorization Bearer 헤더 주입', async () => {
    let capturedAuth: string | null = null
    server.use(
      http.get('http://localhost/api/auth-check', ({ request }) => {
        capturedAuth = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )
    useAppStore.setState({ token: 'test-token-123' })
    await axiosClient.get('http://localhost/api/auth-check')
    expect(capturedAuth).toBe('Bearer test-token-123')
  })

  it('토큰 없을 시 Authorization 헤더 미주입', async () => {
    let capturedAuth: string | null | undefined = undefined
    server.use(
      http.get('http://localhost/api/no-auth', ({ request }) => {
        capturedAuth = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )
    useAppStore.setState({ token: null })
    await axiosClient.get('http://localhost/api/no-auth')
    expect(capturedAuth).toBeNull()
  })
})
