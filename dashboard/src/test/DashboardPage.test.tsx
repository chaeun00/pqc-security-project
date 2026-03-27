import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '@/mocks/handlers'
import DashboardPage from '@/features/dashboard/DashboardPage'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <DashboardPage />
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  it('암호화 버튼 렌더링', () => {
    renderPage()
    expect(screen.getByText('암호화')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('plaintext...')).toBeInTheDocument()
  })

  it('빈 입력 시 mutation 미호출 — 버튼 활성 상태 유지', () => {
    renderPage()
    const btn = screen.getByRole('button', { name: '암호화' })
    fireEvent.click(btn)
    expect(btn).not.toBeDisabled()
  })

  it('plaintext 입력 후 암호화 성공 → 결과 표시', async () => {
    renderPage()
    fireEvent.change(screen.getByPlaceholderText('plaintext...'), {
      target: { value: 'hello' },
    })
    fireEvent.click(screen.getByRole('button', { name: '암호화' }))
    await waitFor(() => expect(screen.getByText(/"algorithm"/)).toBeInTheDocument())
    expect(screen.getByText(/"ML-KEM-768"/)).toBeInTheDocument()
  })
})
