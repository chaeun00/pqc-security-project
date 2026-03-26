import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '@/mocks/handlers'
import { useAlgorithmSwitch } from '@/features/cbom/useAlgorithmSwitch'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useAlgorithmSwitch', () => {
  it('허용 알고리즘 전환 성공 시 isSuccess 상태', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    const { result } = renderHook(() => useAlgorithmSwitch(), { wrapper })

    act(() => {
      result.current.mutate({ asset_id: 'uuid-1', algorithm: 'ML-KEM-768' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('전환 성공 후 queryClient.invalidateQueries 호출 확인', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const localWrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useAlgorithmSwitch(), { wrapper: localWrapper })

    act(() => {
      result.current.mutate({ asset_id: 'uuid-1', algorithm: 'ML-KEM-768' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cbom'] })
  })

  it('서버 500 응답 시 isError 상태', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    server.use(
      http.post('/actuator/algorithm', () => HttpResponse.json({}, { status: 500 })),
    )
    const { result } = renderHook(() => useAlgorithmSwitch(), { wrapper })

    act(() => {
      result.current.mutate({ asset_id: 'uuid-1', algorithm: 'ML-KEM-768' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
