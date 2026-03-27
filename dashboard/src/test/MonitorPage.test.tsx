import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse, delay } from 'msw'
import { handlers } from '@/mocks/handlers'
import MonitorPage from '@/features/monitor/MonitorPage'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
beforeEach(() => useAppStore.setState({ token: 'mock-jwt-token' }))
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function renderMonitor() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MonitorPage />
    </QueryClientProvider>,
  )
}

describe('MonitorPage', () => {
  it('로딩 상태 표시', () => {
    server.use(
      http.get('/api/cbom', async () => {
        await delay('infinite')
        return HttpResponse.json([])
      }),
    )
    renderMonitor()
    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })

  it('정상 렌더 — 통계 카드 4개 표시', async () => {
    renderMonitor()
    await waitFor(() => expect(screen.getByText('실시간 모니터링')).toBeInTheDocument())
    // 전체 10건 (id:10도 테이블에 존재하므로 getAllByText 사용)
    expect(screen.getAllByText('10').length).toBeGreaterThanOrEqual(1)
    // HIGH 3, MEDIUM 2, LOW 5
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('isError 시 에러 메시지 표시 (빈 테이블 미노출)', async () => {
    server.use(http.get('/api/cbom', () => HttpResponse.json({}, { status: 500 })))
    renderMonitor()
    await waitFor(() =>
      expect(screen.getByText('데이터를 불러오지 못했습니다.')).toBeInTheDocument(),
    )
    expect(screen.queryByText('최근 이력 (5건)')).not.toBeInTheDocument()
  })

  it('최근 5건 registered_at 내림차순 정렬 표시', async () => {
    renderMonitor()
    await waitFor(() => expect(screen.getByText('최근 이력 (5건)')).toBeInTheDocument())

    const rows = screen.getAllByRole('row')
    // thead 1행 + tbody 5행 = 6행
    expect(rows).toHaveLength(6)

    // 가장 최근 항목(AES-256, 2026-03-10)이 첫 번째 tbody 행
    const cells = rows[1].querySelectorAll('td')
    expect(cells[1].textContent).toBe('AES-256')
    expect(cells[3].textContent).toBe('2026-03-10')
  })

  it('빈 배열 응답 시 테이블 행 없음', async () => {
    server.use(http.get('/api/cbom', () => HttpResponse.json([])))
    renderMonitor()
    await waitFor(() => expect(screen.getByText('실시간 모니터링')).toBeInTheDocument())
    // thead만 존재
    expect(screen.getAllByRole('row')).toHaveLength(1)
  })

  it('알고리즘별 bar — aria-label 텍스트 검증', async () => {
    renderMonitor()
    await waitFor(() => expect(screen.getByText('실시간 모니터링')).toBeInTheDocument())
    expect(screen.getByLabelText('AES-256 1건')).toBeInTheDocument()
  })
})
