import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '@/mocks/handlers'
import CbomPage from '@/features/cbom/CbomPage'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
beforeEach(() => useAppStore.setState({ token: 'mock-jwt-token' }))
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function renderCbom() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <CbomPage />
    </QueryClientProvider>,
  )
}

describe('CbomPage', () => {
  it('로딩 상태 표시', () => {
    renderCbom()
    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })

  it('10건 목록 렌더링', async () => {
    renderCbom()
    await waitFor(() => expect(screen.getByText('총 10건')).toBeInTheDocument())
    expect(screen.getAllByRole('row')).toHaveLength(11) // thead 1 + tbody 10
  })

  it('알고리즘 필터 적용 시 해당 항목만 표시', async () => {
    renderCbom()
    await waitFor(() => expect(screen.getByText('총 10건')).toBeInTheDocument())

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ML-KEM-768' } })
    expect(screen.getByText('총 1건')).toBeInTheDocument()
    // <option> + <td> 두 곳에 존재하므로 getAllByText 사용
    expect(screen.getAllByText('ML-KEM-768').length).toBeGreaterThanOrEqual(1)
  })

  it('빈 배열 응답 시 테이블 행 없음', async () => {
    server.use(http.get('/api/cbom', () => HttpResponse.json([])))
    renderCbom()
    await waitFor(() => expect(screen.getByText('총 0건')).toBeInTheDocument())
    expect(screen.getAllByRole('row')).toHaveLength(1) // thead만
  })

  it('isError 상태 시 에러 메시지 표시', async () => {
    server.use(http.get('/api/cbom', () => HttpResponse.json({}, { status: 500 })))
    renderCbom()
    await waitFor(() =>
      expect(screen.getByText('데이터를 불러오지 못했습니다.')).toBeInTheDocument(),
    )
  })

  it('risk_level UNKNOWN 시 기본 배지 스타일 적용 (TypeError 없음)', async () => {
    server.use(
      http.get('/api/cbom', () =>
        HttpResponse.json([
          { id: 99, algorithm: 'UNKNOWN-ALG', type: 'Unknown', risk_level: 'UNKNOWN', registered_at: '2026-03-25T00:00:00Z' },
        ]),
      ),
    )
    renderCbom()
    await waitFor(() => expect(screen.getByText('UNKNOWN')).toBeInTheDocument())
    const badge = screen.getByText('UNKNOWN')
    expect(badge.className).toContain('bg-gray-100')
  })

  it('registered_at null 시 — 표시 (TypeError 없음)', async () => {
    server.use(
      http.get('/api/cbom', () =>
        HttpResponse.json([
          { id: 98, algorithm: 'TEST', type: 'Test', risk_level: 'LOW', registered_at: null },
        ]),
      ),
    )
    renderCbom()
    await waitFor(() => expect(screen.getByText('—')).toBeInTheDocument())
  })

  it('뷰탭 전환(목록→우선순위) 시 필터·페이지 상태 유지', async () => {
    renderCbom()
    await waitFor(() => expect(screen.getByText('총 10건')).toBeInTheDocument())

    // 필터 적용 (ML-KEM-768, 1건)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ML-KEM-768' } })
    expect(screen.getByText('총 1건')).toBeInTheDocument()

    // 우선순위 탭으로 전환
    fireEvent.click(screen.getByRole('tab', { name: '우선순위' }))
    expect(screen.queryByText('총 1건')).not.toBeInTheDocument()

    // 목록 탭으로 복귀 → 필터 유지(1건)
    fireEvent.click(screen.getByRole('tab', { name: '목록' }))
    expect(screen.getByText('총 1건')).toBeInTheDocument()
  })
})
