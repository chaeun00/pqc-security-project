import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers, resetInventoryMock } from '@/mocks/handlers'
import InventoryPage from '@/features/inventory/InventoryPage'
import { useAppStore } from '@/store/useAppStore'

const server = setupServer(...handlers)
beforeAll(() => server.listen())
beforeEach(() => useAppStore.setState({ token: 'mock-jwt-token' }))
afterEach(() => { server.resetHandlers(); resetInventoryMock(); useAppStore.setState({ token: null }) })
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <InventoryPage />
    </QueryClientProvider>,
  )
}

describe('InventoryPage', () => {
  it('로딩 상태 표시', () => {
    renderPage()
    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })

  it('목록 3건 렌더링', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())
    expect(screen.getByText('JWT Secret')).toBeInTheDocument()
    expect(screen.getByText('DB Encryption Key')).toBeInTheDocument()
  })

  it('필수 필드 빈값 시 등록 버튼 disabled', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
  })

  it('필수 필드 입력 후 등록 버튼 활성화', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.change(screen.getByPlaceholderText('이름'), { target: { value: 'New Item' } })
    fireEvent.change(screen.getByPlaceholderText('알고리즘'), { target: { value: 'ML-KEM-512' } })
    fireEvent.change(screen.getByPlaceholderText('위치'), { target: { value: 'service-x' } })

    expect(screen.getByRole('button', { name: '등록' })).not.toBeDisabled()
  })

  it('등록 성공 시 목록에 추가', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.change(screen.getByPlaceholderText('이름'), { target: { value: 'New Item' } })
    fireEvent.change(screen.getByPlaceholderText('알고리즘'), { target: { value: 'ML-KEM-512' } })
    fireEvent.change(screen.getByPlaceholderText('위치'), { target: { value: 'service-x' } })
    fireEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(screen.getByText('New Item')).toBeInTheDocument())
  })

  it('등록 실패(500) 시 인라인 에러 메시지 표시 + 롤백', async () => {
    server.use(http.post('/api/inventory', () => HttpResponse.json({}, { status: 500 })))
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.change(screen.getByPlaceholderText('이름'), { target: { value: 'Fail Item' } })
    fireEvent.change(screen.getByPlaceholderText('알고리즘'), { target: { value: 'RSA-2048' } })
    fireEvent.change(screen.getByPlaceholderText('위치'), { target: { value: 'svc' } })
    fireEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(screen.getByText('등록에 실패했습니다. 다시 시도해 주세요.')).toBeInTheDocument(),
    )
    // rollback: optimistic item should not remain
    expect(screen.queryByText('Fail Item')).not.toBeInTheDocument()
  })

  it('편집 버튼 클릭 시 인라인 편집 폼 표시', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('편집 TLS Certificate'))
    expect(screen.getByLabelText('편집 이름')).toBeInTheDocument()
  })

  it('편집 필수 필드 비우면 저장 버튼 disabled', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('편집 TLS Certificate'))
    const nameInput = screen.getByLabelText('편집 이름')
    fireEvent.change(nameInput, { target: { value: '' } })

    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
  })

  it('편집 성공 시 목록에 변경값 반영', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('편집 TLS Certificate'))
    fireEvent.change(screen.getByLabelText('편집 이름'), { target: { value: 'Updated Cert' } })
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(screen.getByText('Updated Cert')).toBeInTheDocument())
    expect(screen.queryByText('TLS Certificate')).not.toBeInTheDocument()
  })

  it('편집 실패(500) 시 에러 메시지 표시 + 롤백', async () => {
    server.use(http.put('/api/inventory/:id', () => HttpResponse.json({}, { status: 500 })))
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('편집 TLS Certificate'))
    fireEvent.change(screen.getByLabelText('편집 이름'), { target: { value: 'Should Rollback' } })
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(screen.getByText('수정에 실패했습니다. 다시 시도해 주세요.')).toBeInTheDocument(),
    )
    // rollback: original name restored
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())
    expect(screen.queryByText('Should Rollback')).not.toBeInTheDocument()
  })

  it('삭제 버튼 클릭 시 confirm UI 표시 후 삭제', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('삭제 TLS Certificate'))
    expect(screen.getByText('삭제할까요?')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('삭제 확인 TLS Certificate'))
    await waitFor(() => expect(screen.queryByText('TLS Certificate')).not.toBeInTheDocument())
  })

  it('삭제 취소 시 항목 유지', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('삭제 TLS Certificate'))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.queryByText('삭제할까요?')).not.toBeInTheDocument()
    expect(screen.getByText('TLS Certificate')).toBeInTheDocument()
  })

  it('삭제 실패(500) 시 롤백 — 항목 재표시', async () => {
    server.use(http.delete('/api/inventory/:id', () => HttpResponse.json({}, { status: 500 })))
    renderPage()
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('삭제 TLS Certificate'))
    fireEvent.click(screen.getByLabelText('삭제 확인 TLS Certificate'))

    // Optimistic: item removed, then rolled back
    await waitFor(() => expect(screen.getByText('TLS Certificate')).toBeInTheDocument())
  })

  it('isError 상태 시 에러 메시지 표시', async () => {
    server.use(http.get('/api/inventory', () => HttpResponse.json({}, { status: 500 })))
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('데이터를 불러오지 못했습니다.')).toBeInTheDocument(),
    )
  })
})
