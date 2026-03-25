import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '@/mocks/handlers'
import { useCbomList } from '@/features/cbom/useCbomList'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useCbomList', () => {
  it('GET /api/cbom 호출 시 10건 반환', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    const { result } = renderHook(() => useCbomList(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toHaveLength(10)
    expect(result.current.data?.[0]).toMatchObject({
      id: expect.any(Number),
      algorithm: expect.any(String),
      type: expect.any(String),
      risk_level: expect.any(String),
      registered_at: expect.any(String),
    })
  })

  it('Authorization 헤더 없으면 isError 상태', async () => {
    // token을 설정하지 않음 → axiosClient가 Authorization 헤더 미전송
    const { result } = renderHook(() => useCbomList(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
