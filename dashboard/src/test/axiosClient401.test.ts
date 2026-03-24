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
})
