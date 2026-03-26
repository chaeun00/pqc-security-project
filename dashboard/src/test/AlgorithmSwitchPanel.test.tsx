import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '@/mocks/handlers'
import AlgorithmSwitchPanel from '@/features/cbom/AlgorithmSwitchPanel'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
afterEach(() => { server.resetHandlers(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function renderPanel(onClose = () => {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AlgorithmSwitchPanel assetId="uuid-1" currentAlgorithm="RSA-2048" onClose={onClose} />
    </QueryClientProvider>,
  )
}

describe('AlgorithmSwitchPanel', () => {
  it('현재 알고리즘과 전환 버튼 렌더링', () => {
    renderPanel()
    expect(screen.getByText('RSA-2048')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전환' })).toBeInTheDocument()
  })

  it('알고리즘 미선택(빈값) 시 전환 버튼 disabled', () => {
    renderPanel()
    expect(screen.getByRole('button', { name: '전환' })).toBeDisabled()
  })

  it('허용 알고리즘 선택 후 전환 성공 시 완료 메시지 표시', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    renderPanel()

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ML-KEM-768' } })
    expect(screen.getByRole('button', { name: '전환' })).not.toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '전환' }))
    await waitFor(() =>
      expect(screen.getByText('전환 완료. 목록이 갱신됩니다.')).toBeInTheDocument(),
    )
  })

  it('서버 500 응답 시 실패 메시지 표시', async () => {
    useAppStore.setState({ token: 'mock-jwt-token' })
    server.use(
      http.post('/actuator/algorithm', () => HttpResponse.json({}, { status: 500 })),
    )
    renderPanel()

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ML-KEM-768' } })
    fireEvent.click(screen.getByRole('button', { name: '전환' }))
    await waitFor(() =>
      expect(screen.getByText('전환 실패. 다시 시도해 주세요.')).toBeInTheDocument(),
    )
  })
})
